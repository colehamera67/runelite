package net.runelite.client.plugins.multiboxer;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Multiboxer",
	description = "Synchronizes actions between multiple RuneLite clients on private servers",
	tags = {"multibox", "sync", "private server"},
	enabledByDefault = false
)
public class MultiboxerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private MultiboxerConfig config;

	@Inject
	private MultiboxerNetworkManager networkManager;

	private volatile boolean isProcessingRemoteAction = false;
	private volatile boolean isAutoEating = false;
	private volatile boolean isAutoDrinking = false;

	// Action queueing for reliability
	private final Queue<ActionMessage> actionQueue = new LinkedList<>();
	private int ticksSinceLastAction = 0;

	// Auto-eat
	private Set<Integer> foodItemIds = new HashSet<>();
	private int ticksSinceLastEat = 0;

	// Auto-restore prayer
	private Set<Integer> prayerPotionIds = new HashSet<>();
	private int ticksSinceLastPrayerDrink = 0;

	// Auto-drink stat potions
	private Set<Integer> statPotionIds = new HashSet<>();
	private int ticksSinceLastStatDrink = 0;

	// Auto-drink stamina
	private Set<Integer> staminaPotionIds = new HashSet<>();
	private int ticksSinceLastStaminaDrink = 0;

	// Prayer syncing
	private int lastQuickPrayerState = -1;

	// Special attack syncing
	private int lastSpecialAttackEnergy = -1;
	private boolean lastSpecialAttackEnabled = false;

	// Combat style syncing
	private int lastAttackStyle = -1;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Multiboxer plugin started!");
		networkManager.start(config.serverAddress(), config.serverPort());
		parseAllItemIds();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Multiboxer plugin stopped!");
		networkManager.stop();
		synchronized (actionQueue)
		{
			actionQueue.clear();
		}
		foodItemIds.clear();
		prayerPotionIds.clear();
		statPotionIds.clear();
		staminaPotionIds.clear();
	}

	/**
	 * Parse all item IDs from config
	 */
	private void parseAllItemIds()
	{
		parseItemIds(config.foodItemIds(), foodItemIds, "food");
		parseItemIds(config.prayerPotionIds(), prayerPotionIds, "prayer potion");
		parseItemIds(config.statPotionIds(), statPotionIds, "stat potion");
		parseItemIds(config.staminaPotionIds(), staminaPotionIds, "stamina potion");
	}

	/**
	 * Parse item IDs from comma-separated string
	 */
	private void parseItemIds(String idsString, Set<Integer> targetSet, String itemType)
	{
		targetSet.clear();
		String[] ids = idsString.split(",");
		for (String id : ids)
		{
			try
			{
				targetSet.add(Integer.parseInt(id.trim()));
			}
			catch (NumberFormatException e)
			{
				log.warn("Invalid {} item ID: {}", itemType, id);
			}
		}
		log.info("Loaded {} {} item IDs", targetSet.size(), itemType);
	}

	@Provides
	MultiboxerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MultiboxerConfig.class);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Prevent infinite loop - don't sync actions that came from remote
		if (isProcessingRemoteAction)
		{
			if (config.debugMode())
			{
				log.info("[DEBUG] Skipping sync - processing remote action");
			}
			return;
		}

		// Don't sync auto-eat or auto-drink actions
		if (isAutoEating || isAutoDrinking)
		{
			if (config.debugMode())
			{
				log.info("[DEBUG] Skipping sync - auto action in progress");
			}
			return;
		}

		if (!config.enabled())
		{
			return;
		}

		MenuEntry menuEntry = event.getMenuEntry();
		MenuAction menuAction = menuEntry.getType();

		// Filter out map walking (CC_OP is for world map clicks)
		if (shouldIgnoreAction(menuAction))
		{
			if (config.debugMode())
			{
				log.info("[DEBUG] Ignoring action: {} {} {}", menuAction, menuEntry.getOption(), menuEntry.getTarget());
			}
			return;
		}

		// Create action message based on the type
		ActionMessage action = createActionMessage(menuEntry);

		if (action != null)
		{
			if (config.debugMode())
			{
				log.info("[DEBUG] Syncing action: {} {} {} (id={}, param0={}, param1={})",
					action.getMenuAction(), action.getOption(), action.getTarget(),
					action.getIdentifier(), action.getParam0(), action.getParam1());
			}
			else
			{
				log.debug("Syncing action: {} {} {}", action.getMenuAction(), action.getOption(), action.getTarget());
			}
			networkManager.sendAction(action);
		}
		else if (config.debugMode())
		{
			log.warn("[DEBUG] Failed to create action message for: {} {} {}",
				menuAction, menuEntry.getOption(), menuEntry.getTarget());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Increment all cooldown timers
		ticksSinceLastEat++;
		ticksSinceLastPrayerDrink++;
		ticksSinceLastStatDrink++;
		ticksSinceLastStaminaDrink++;
		ticksSinceLastAction++;

		// Process action queue - execute one action per tick to prevent spam/rejection
		// This ensures actions work across different game states
		synchronized (actionQueue)
		{
			if (!actionQueue.isEmpty() && ticksSinceLastAction >= 1)
			{
				ActionMessage action = actionQueue.poll();
				if (action != null)
				{
					executeRemoteAction(action);
					ticksSinceLastAction = 0;
				}
			}
		}

		// Auto-features work on all clients
		// Auto-eat (3 tick cooldown = 1.8 seconds)
		if (config.autoEatEnabled() && ticksSinceLastEat >= 3)
		{
			if (shouldEatFood())
			{
				eatFood();
			}
		}

		// Auto-restore prayer (3 tick cooldown)
		if (config.autoPrayerEnabled() && ticksSinceLastPrayerDrink >= 3)
		{
			if (shouldRestorePrayer())
			{
				drinkPrayerPotion();
			}
		}

		// Auto-drink stat potions (5 tick cooldown = 3 seconds)
		if (config.autoStatPotionsEnabled() && ticksSinceLastStatDrink >= 5)
		{
			if (shouldDrinkStatPotion())
			{
				drinkStatPotion();
			}
		}

		// Auto-drink stamina (10 tick cooldown = 6 seconds)
		if (config.autoStaminaEnabled() && ticksSinceLastStaminaDrink >= 10)
		{
			if (shouldDrinkStamina())
			{
				drinkStaminaPotion();
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!config.enabled())
		{
			return;
		}

		// Sync quick prayer activation/deactivation
		if (event.getVarbitId() == 4103) // QUICKPRAYER_ACTIVE varbit
		{
			int newState = event.getValue();
			if (config.syncQuickPrayer() && newState != lastQuickPrayerState && lastQuickPrayerState != -1)
			{
				PrayerSyncMessage msg = new PrayerSyncMessage();
				msg.setVarbitId(4103);
				msg.setActivated(newState != 0);
				msg.setQuickPrayer(true);
				networkManager.sendPrayerSync(msg);
				log.debug("Syncing quick prayer: {}", newState != 0);
			}
			lastQuickPrayerState = newState;
		}

		// Sync individual prayer activation/deactivation (varbits 4104-4129)
		if (config.syncIndividualPrayers() && event.getVarbitId() >= 4104 && event.getVarbitId() <= 4129)
		{
			PrayerSyncMessage msg = new PrayerSyncMessage();
			msg.setVarbitId(event.getVarbitId());
			msg.setActivated(event.getValue() != 0);
			msg.setQuickPrayer(false);
			networkManager.sendPrayerSync(msg);
			log.debug("Syncing prayer varbit {}: {}", event.getVarbitId(), event.getValue() != 0);
		}

		// Sync special attack usage
		if (event.getVarpId() == VarPlayer.SPECIAL_ATTACK_PERCENT)
		{
			int newEnergy = event.getValue();
			if (config.syncSpecialAttack() && lastSpecialAttackEnergy != -1 && newEnergy < lastSpecialAttackEnergy)
			{
				// Special attack was used
				SpecialAttackMessage msg = new SpecialAttackMessage();
				msg.setToggleSpecial(false);
				msg.setMinEnergyRequired(config.specialAttackMinEnergy());
				networkManager.sendSpecialAttack(msg);
				log.debug("Syncing special attack use");
			}
			lastSpecialAttackEnergy = newEnergy;
		}

		// Sync combat style changes
		if (event.getVarpId() == VarPlayer.ATTACK_STYLE)
		{
			int newStyle = event.getValue();
			if (config.syncCombatStyle() && newStyle != lastAttackStyle && lastAttackStyle != -1)
			{
				CombatStyleMessage msg = new CombatStyleMessage();
				msg.setAttackStyle(newStyle);
				msg.setWeaponType(client.getVarbitValue(357)); // Equipped weapon type
				networkManager.sendCombatStyle(msg);
				log.debug("Syncing combat style change: {}", newStyle);
			}
			lastAttackStyle = newStyle;
		}
	}

	/**
	 * Determines if an action should be ignored (not synced)
	 * Uses a blacklist approach - we exclude specific actions we don't want synced
	 */
	private boolean shouldIgnoreAction(MenuAction menuAction)
	{
		switch (menuAction)
		{
			// Ignore regular walking
			case WALK:
				return true;

			// Ignore examine actions (they're just informational)
			case EXAMINE_OBJECT:
			case EXAMINE_NPC:
			case EXAMINE_ITEM_GROUND:
			case EXAMINE_ITEM:
				return true;

			// Ignore RuneLite-specific menu actions
			case RUNELITE:
			case RUNELITE_WIDGET:
			case RUNELITE_HIGH_PRIORITY:
			case RUNELITE_OVERLAY:
			case RUNELITE_OVERLAY_CONFIG:
			case RUNELITE_PLAYER:
				return true;

			// Ignore cancel actions
			case CANCEL:
				return true;

			// Sync everything else, including:
			// - NPC interactions (NPC_FIRST_OPTION, etc.)
			// - Object interactions (GAME_OBJECT_FIRST_OPTION, etc.)
			// - Player interactions (PLAYER_FIRST_OPTION, etc.)
			// - Item actions (ITEM_USE, ITEM_USE_ON_*, etc.)
			// - Widget/Interface actions (CC_OP, WIDGET_*, etc.)
			// - Ground item actions (GROUND_ITEM_FIRST_OPTION, etc.)
			default:
				return false;
		}
	}

	/**
	 * Creates an action message from a menu entry
	 */
	private ActionMessage createActionMessage(MenuEntry menuEntry)
	{
		ActionMessage action = new ActionMessage();
		action.setMenuAction(menuEntry.getType());
		action.setOption(menuEntry.getOption());
		action.setTarget(menuEntry.getTarget());
		action.setIdentifier(menuEntry.getIdentifier());
		action.setParam0(menuEntry.getParam0());
		action.setParam1(menuEntry.getParam1());
		action.setItemId(menuEntry.getItemId());

		// For inventory clicks, we need to sync by item ID, not slot position
		if (isInventoryAction(menuEntry))
		{
			int itemId = resolveItemIdFromSlot(menuEntry.getParam0(), menuEntry.getParam1());
			if (itemId == -1)
			{
				log.warn("Could not resolve item ID for inventory action");
				return null;
			}
			action.setResolvedItemId(itemId);
		}

		return action;
	}

	/**
	 * Checks if the menu action is an inventory or bank-related action that needs item ID resolution
	 */
	private boolean isInventoryAction(MenuEntry menuEntry)
	{
		MenuAction action = menuEntry.getType();

		// Check if this is an item-related action (inventory or bank)
		return action == MenuAction.ITEM_USE ||
			action == MenuAction.ITEM_USE_ON_NPC ||
			action == MenuAction.ITEM_USE_ON_GAME_OBJECT ||
			action == MenuAction.ITEM_USE_ON_GROUND_ITEM ||
			action == MenuAction.ITEM_USE_ON_ITEM ||
			action == MenuAction.WIDGET_TARGET_ON_NPC ||
			action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT ||
			action == MenuAction.CC_OP ||  // Bank withdrawals and interface item clicks
			action == MenuAction.CC_OP_LOW_PRIORITY ||
			action.name().startsWith("CC_OP_LOW_PRIORITY") ||
			(action.getId() >= 33 && action.getId() <= 38); // Item actions
	}

	/**
	 * Resolves the item ID from an inventory slot or bank slot
	 */
	private int resolveItemIdFromSlot(int param0, int param1)
	{
		// First try inventory
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			// param0 usually contains the slot index for inventory actions
			int slot = param0;
			if (slot >= 0 && slot < 28)
			{
				Item item = inventory.getItem(slot);
				if (item != null)
				{
					if (config.debugMode())
					{
						log.info("[DEBUG] Resolved inventory item ID {} from slot {}", item.getId(), slot);
					}
					return item.getId();
				}
			}
		}

		// Try bank container (for bank withdrawals)
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			int slot = param0;
			Item[] items = bank.getItems();
			if (slot >= 0 && slot < items.length)
			{
				Item item = items[slot];
				if (item != null && item.getId() != -1)
				{
					if (config.debugMode())
					{
						log.info("[DEBUG] Resolved bank item ID {} from slot {}", item.getId(), slot);
					}
					return item.getId();
				}
			}
		}

		if (config.debugMode())
		{
			log.warn("[DEBUG] Could not resolve item ID from slot {} (param1={})", param0, param1);
		}
		return -1;
	}

	/**
	 * Called by network manager when receiving an action from another client
	 */
	public void handleRemoteAction(ActionMessage action)
	{
		if (!config.enabled())
		{
			return;
		}

		if (config.debugMode())
		{
			log.info("[DEBUG] Received remote action: {} {} {} (id={}, param0={}, param1={}, resolvedItemId={})",
				action.getMenuAction(), action.getOption(), action.getTarget(),
				action.getIdentifier(), action.getParam0(), action.getParam1(), action.getResolvedItemId());
		}
		else
		{
			log.debug("Received remote action: {} {} {}", action.getMenuAction(), action.getOption(), action.getTarget());
		}

		// Add action to queue for execution on next tick
		// This ensures actions work across different game states
		synchronized (actionQueue)
		{
			actionQueue.offer(action);
			if (config.debugMode())
			{
				log.info("[DEBUG] Added action to queue (queue size: {})", actionQueue.size());
			}
		}
	}

	/**
	 * Execute a remote action from the queue
	 * This method is called from onGameTick to ensure actions execute reliably
	 */
	private void executeRemoteAction(ActionMessage action)
	{
		// If this is an inventory/bank action with a resolved item ID, we need to find the slot
		if (action.getResolvedItemId() != -1)
		{
			int slot = findItemSlot(action.getResolvedItemId());
			if (slot == -1)
			{
				if (config.debugMode())
				{
					log.warn("[DEBUG] Could not find item {} in inventory or bank - may not have item", action.getResolvedItemId());
				}
				else
				{
					log.warn("Could not find item {} in inventory/bank", action.getResolvedItemId());
				}
				return;
			}
			// Update the param0 to use the correct slot on this client
			if (config.debugMode())
			{
				log.info("[DEBUG] Found item {} at slot {} (was slot {} on sender)", action.getResolvedItemId(), slot, action.getParam0());
			}
			action.setParam0(slot);
		}

		// Set flag to prevent infinite loop
		isProcessingRemoteAction = true;
		try
		{
			if (config.debugMode())
			{
				log.info("[DEBUG] Executing remote action: {} {} {}", action.getMenuAction(), action.getOption(), action.getTarget());
			}

			// Execute the action on this client
			// This works across game states because it's executed on game tick
			client.menuAction(
				action.getParam0(),
				action.getParam1(),
				action.getMenuAction(),
				action.getIdentifier(),
				action.getItemId(),
				action.getOption(),
				action.getTarget()
			);
		}
		catch (Exception e)
		{
			log.error("[ERROR] Failed to execute remote action: {} {} {}", action.getMenuAction(), action.getOption(), action.getTarget(), e);
		}
		finally
		{
			isProcessingRemoteAction = false;
		}
	}

	/**
	 * Finds the first inventory or bank slot containing the specified item ID
	 */
	private int findItemSlot(int itemId)
	{
		// First check inventory
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			Item[] items = inventory.getItems();
			for (int i = 0; i < items.length; i++)
			{
				Item item = items[i];
				if (item != null && item.getId() == itemId)
				{
					return i;
				}
			}
		}

		// Then check bank (for withdrawals)
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			Item[] items = bank.getItems();
			for (int i = 0; i < items.length; i++)
			{
				Item item = items[i];
				if (item != null && item.getId() == itemId)
				{
					return i;
				}
			}
		}

		return -1;
	}

	/**
	 * Check if the player should eat food based on current health percentage
	 */
	private boolean shouldEatFood()
	{
		int currentHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (maxHp <= 0)
		{
			return false;
		}

		int healthPercent = (currentHp * 100) / maxHp;
		int threshold = config.autoEatHealthPercent();

		return healthPercent <= threshold;
	}

	/**
	 * Automatically eat food from inventory
	 */
	private void eatFood()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return;
		}

		// Find the first food item in inventory
		Item[] items = inventory.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item != null && foodItemIds.contains(item.getId()))
			{
				// Found food, eat it
				log.debug("Auto-eating food: {} at slot {}", item.getId(), slot);

				isAutoEating = true;
				try
				{
					// Click on the food item to eat it
					// Widget ID for inventory is typically calculated as (slot << 16) | 9764864
					int widgetId = 9764864; // Inventory widget base
					int param1 = (slot << 16) | widgetId;

					client.menuAction(
						slot,
						param1,
						MenuAction.CC_OP,
						item.getId(),
						-1,
						"Eat",
						"<col=ff9040>" + client.getItemDefinition(item.getId()).getName()
					);

					ticksSinceLastEat = 0;
					log.info("Auto-ate food at {}% health",
						(client.getBoostedSkillLevel(Skill.HITPOINTS) * 100) / client.getRealSkillLevel(Skill.HITPOINTS));
				}
				catch (Exception e)
				{
					log.error("Error auto-eating food", e);
				}
				finally
				{
					isAutoEating = false;
				}

				return; // Only eat one food item at a time
			}
		}

		log.debug("No food found in inventory for auto-eat");
	}

	/**
	 * Check if player should restore prayer
	 */
	private boolean shouldRestorePrayer()
	{
		int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
		int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);

		if (maxPrayer <= 0)
		{
			return false;
		}

		int prayerPercent = (currentPrayer * 100) / maxPrayer;
		return prayerPercent <= config.autoPrayerPercent();
	}

	/**
	 * Drink prayer/restore potion
	 */
	private void drinkPrayerPotion()
	{
		if (drinkPotion(prayerPotionIds, "prayer potion"))
		{
			ticksSinceLastPrayerDrink = 0;
			log.info("Auto-drank prayer potion at {}% prayer",
				(client.getBoostedSkillLevel(Skill.PRAYER) * 100) / client.getRealSkillLevel(Skill.PRAYER));
		}
	}

	/**
	 * Check if player should drink stat potion
	 */
	private boolean shouldDrinkStatPotion()
	{
		int threshold = config.autoStatBoostThreshold();

		// Check attack, strength, defense, ranged, magic
		Skill[] combatSkills = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC};

		for (Skill skill : combatSkills)
		{
			int boosted = client.getBoostedSkillLevel(skill);
			int real = client.getRealSkillLevel(skill);
			int boost = boosted - real;

			// If any stat boost is below threshold, drink potion
			if (boost > 0 && boost < threshold)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Drink stat-boosting potion
	 */
	private void drinkStatPotion()
	{
		if (drinkPotion(statPotionIds, "stat potion"))
		{
			ticksSinceLastStatDrink = 0;
			log.info("Auto-drank stat potion");
		}
	}

	/**
	 * Check if player should drink stamina potion
	 */
	private boolean shouldDrinkStamina()
	{
		int energy = client.getEnergy();
		int energyPercent = energy / 100;

		// Don't drink if stamina buff is active (varbit 25)
		boolean staminaActive = client.getVarbitValue(25) > 0; // STAMINA_ACTIVE varbit
		if (staminaActive)
		{
			return false;
		}

		return energyPercent <= config.autoStaminaPercent();
	}

	/**
	 * Drink stamina potion
	 */
	private void drinkStaminaPotion()
	{
		if (drinkPotion(staminaPotionIds, "stamina potion"))
		{
			ticksSinceLastStaminaDrink = 0;
			log.info("Auto-drank stamina potion at {}% energy", client.getEnergy() / 100);
		}
	}

	/**
	 * Generic method to drink a potion from inventory
	 */
	private boolean drinkPotion(Set<Integer> potionIds, String potionType)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return false;
		}

		Item[] items = inventory.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item != null && potionIds.contains(item.getId()))
			{
				log.debug("Auto-drinking {}: {} at slot {}", potionType, item.getId(), slot);

				isAutoDrinking = true;
				try
				{
					int widgetId = 9764864; // Inventory widget base
					int param1 = (slot << 16) | widgetId;

					client.menuAction(
						slot,
						param1,
						MenuAction.CC_OP,
						item.getId(),
						-1,
						"Drink",
						"<col=ff9040>" + client.getItemDefinition(item.getId()).getName()
					);

					return true;
				}
				catch (Exception e)
				{
					log.error("Error auto-drinking {}", potionType, e);
					return false;
				}
				finally
				{
					isAutoDrinking = false;
				}
			}
		}

		log.debug("No {} found in inventory", potionType);
		return false;
	}

	/**
	 * Handle prayer sync message from another client
	 */
	public void handlePrayerSync(PrayerSyncMessage msg)
	{
		if (!config.enabled())
		{
			return;
		}

		log.debug("Received prayer sync: varbit {} = {}", msg.getVarbitId(), msg.isActivated());

		// Quick prayer can be toggled via interface
		if (msg.isQuickPrayer())
		{
			// Toggle quick prayer - interface widget varies by client
			// This is usually done via the prayer orb or interface
			log.info("Syncing quick prayer: {}", msg.isActivated());
		}
		else
		{
			// Individual prayer activation
			// Note: Prayer activation typically requires clicking the prayer interface
			// This is limited by the game's prayer system
			log.info("Syncing individual prayer varbit {}: {}", msg.getVarbitId(), msg.isActivated());
		}
	}

	/**
	 * Handle special attack sync message from another client
	 */
	public void handleSpecialAttackSync(SpecialAttackMessage msg)
	{
		if (!config.enabled() || !config.syncSpecialAttack())
		{
			return;
		}

		int currentEnergy = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);
		int energyPercent = currentEnergy / 10;

		// Check if we have enough energy
		if (energyPercent < msg.getMinEnergyRequired())
		{
			log.debug("Not enough special attack energy: {}% < {}%", energyPercent, msg.getMinEnergyRequired());
			return;
		}

		log.info("Syncing special attack usage");

		// Toggle special attack - this typically requires clicking the spec bar
		// The exact implementation depends on the weapon interface
	}

	/**
	 * Handle combat style sync message from another client
	 */
	public void handleCombatStyleSync(CombatStyleMessage msg)
	{
		if (!config.enabled() || !config.syncCombatStyle())
		{
			return;
		}

		log.info("Syncing combat style change: {}", msg.getAttackStyle());

		// Combat style is typically changed via the combat options interface
		// This requires clicking the appropriate attack style button
		// The exact widget ID depends on the weapon type
	}
}
