package net.runelite.client.plugins.multiboxer;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
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
import java.util.Random;
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

	// Humanization
	private final Random random = new Random();
	private int clientDelayOffset; // Unique delay offset for this client (0-100ms)
	private double actionSpeedMultiplier; // Speed variance (0.8-1.2)

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

		// Initialize humanization parameters (unique per client)
		clientDelayOffset = random.nextInt(101); // 0-100ms
		actionSpeedMultiplier = 0.8 + (random.nextDouble() * 0.4); // 0.8-1.2
		log.info("Humanization profile: delayOffset={}ms, speedMultiplier={}",
			clientDelayOffset, String.format("%.2f", actionSpeedMultiplier));

		// Stagger connection times (anti-detection)
		int minDelay = config.connectionDelayMin();
		int maxDelay = config.connectionDelayMax();
		if (maxDelay > minDelay)
		{
			int connectionDelay = minDelay + random.nextInt(maxDelay - minDelay);
			if (connectionDelay > 0)
			{
				log.info("Staggering connection: waiting {} seconds before connecting to server", connectionDelay);
				Thread.sleep(connectionDelay * 1000L);
			}
		}

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
		MenuEntry menuEntry = event.getMenuEntry();
		MenuAction menuAction = menuEntry.getType();

		// Log ALL actions in debug mode (before any filtering)
		if (config.debugMode())
		{
			log.info("[DEBUG] onMenuOptionClicked: {} {} {} (param0={}, param1={})",
				menuAction, menuEntry.getOption(), menuEntry.getTarget(),
				menuEntry.getParam0(), menuEntry.getParam1());
		}

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

		// Sync special attack usage (VarPlayer 300 = SPECIAL_ATTACK_PERCENT)
		if (event.getVarpId() == 300)
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

		// Sync combat style changes (VarPlayer 43 = ATTACK_STYLE)
		if (event.getVarpId() == 43)
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

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Auto-login when reaching login screen using VM arguments
		// Use -Drunelite.username=... and -Drunelite.password=... to set credentials
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			String vmUsername = System.getProperty("runelite.username");
			String vmPassword = System.getProperty("runelite.password");

			if (vmUsername != null && !vmUsername.isEmpty() && vmPassword != null && !vmPassword.isEmpty())
			{
				log.info("Auto-login: logging in as {}", vmUsername);
				client.setUsername(vmUsername);
				client.setPassword(vmPassword);
			}
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
			// - Item actions (ITEM_USE_ON_*, etc.)
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

		// For inventory clicks, try to sync by item ID (not slot position)
		if (isInventoryAction(menuEntry))
		{
			int itemId = resolveItemIdFromSlot(menuEntry.getParam0(), menuEntry.getParam1());
			if (itemId != -1)
			{
				// Successfully resolved item ID - use it for syncing
				action.setResolvedItemId(itemId);
				if (config.debugMode())
				{
					log.info("[DEBUG] Resolved item ID {} for action (will find matching slot on slave)", itemId);
				}
			}
			else
			{
				// Failed to resolve item ID (empty slot, drag action, etc.)
				// Still sync the action, just without item ID resolution
				// This allows drag-and-drop and other actions to still work
				if (config.debugMode())
				{
					log.warn("[DEBUG] Could not resolve item ID for inventory action - syncing by position instead");
				}
			}
		}
		else if (config.debugMode())
		{
			// Not an inventory action - sync as-is
			log.info("[DEBUG] Action is NOT inventory/bank item - syncing as-is (option={}, target={})",
				menuEntry.getOption(), menuEntry.getTarget());
		}

		return action;
	}

	/**
	 * Checks if the menu action is an inventory or bank-related action that needs item ID resolution
	 */
	private boolean isInventoryAction(MenuEntry menuEntry)
	{
		MenuAction action = menuEntry.getType();
		int widgetId = menuEntry.getParam1();

		// ITEM_USE_ON_* actions always need item ID resolution
		if (action == MenuAction.ITEM_USE_ON_NPC ||
			action == MenuAction.ITEM_USE_ON_GAME_OBJECT ||
			action == MenuAction.ITEM_USE_ON_GROUND_ITEM ||
			action == MenuAction.ITEM_USE_ON_ITEM)
		{
			return true;
		}

		// WIDGET_TARGET actions (clicking "Use" on inventory items)
		// Check widget ID to ensure it's actually an inventory/bank item
		if (action == MenuAction.WIDGET_TARGET ||
			action == MenuAction.WIDGET_TARGET_ON_WIDGET ||
			action == MenuAction.WIDGET_TARGET_ON_NPC ||
			action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			int widgetGroup = widgetId >> 16;

			if (config.debugMode())
			{
				log.info("[DEBUG] WIDGET_TARGET action - widgetGroup={}, option={}",
					widgetGroup, menuEntry.getOption());
			}

			// Widget groups that contain items (need item ID resolution):
			// 149 = inventory
			// 12 = bank items
			// 15 = bank inventory (deprecated/old)
			if (widgetGroup == 149 || widgetGroup == 12 || widgetGroup == 15)
			{
				if (config.debugMode())
				{
					log.info("[DEBUG] Identified as inventory/bank item (widgetGroup={})", widgetGroup);
				}
				return true;
			}
			// Not an inventory/bank widget - don't resolve item ID
			return false;
		}

		// CC_OP actions - only resolve item ID if it's actually an inventory or bank item
		// Need to check widget ID AND option to differentiate items from interface buttons
		if (action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY)
		{
			int widgetGroup = widgetId >> 16;
			int childId = widgetId & 0xFFFF;
			String option = menuEntry.getOption();

			if (config.debugMode())
			{
				log.info("[DEBUG] CC_OP action - widgetGroup={}, childId={}, option={}",
					widgetGroup, childId, option);
			}

			// Widget group 12 contains BOTH bank items AND the close button!
			// Need to check the option to differentiate:
			// - Bank items: "Withdraw-1", "Withdraw-5", "Withdraw-All", "Deposit", etc.
			// - Close button: "Close"
			if (widgetGroup == 12 && "Close".equalsIgnoreCase(option))
			{
				if (config.debugMode())
				{
					log.info("[DEBUG] Bank close button detected - syncing as button (not item)");
				}
				return false; // It's a button, not an item
			}

			// Widget groups that contain items (need item ID resolution):
			// 149 = inventory
			// 12 = bank items (but NOT the close button!)
			// 15 = bank inventory (deprecated/old)
			if (widgetGroup == 149 || widgetGroup == 12 || widgetGroup == 15)
			{
				if (config.debugMode())
				{
					log.info("[DEBUG] Identified as inventory/bank item (widgetGroup={})", widgetGroup);
				}
				return true;
			}
			// All other widget groups are buttons/interface elements
			// (prayer, magic, run, etc.)
			// These sync as-is without item ID resolution
			if (config.debugMode())
			{
				log.info("[DEBUG] Identified as interface button (widgetGroup={})", widgetGroup);
			}
			return false;
		}

		// Legacy item action IDs
		if (action.getId() >= 33 && action.getId() <= 38)
		{
			return true;
		}

		return false;
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
			// Update BOTH param0 (slot) and itemId to use the correct item on this client
			int originalSlot = action.getParam0();
			int originalItemId = action.getItemId();
			action.setParam0(slot);
			action.setItemId(action.getResolvedItemId()); // Update itemId to match the resolved item

			if (config.debugMode())
			{
				log.info("[DEBUG] Item ID resolution: found item {} at slot {} (sender had it at slot {})",
					action.getResolvedItemId(), slot, originalSlot);
				log.info("[DEBUG] Updated params: param0 {} -> {}, itemId {} -> {}",
					originalSlot, slot, originalItemId, action.getItemId());
			}
		}

		// Set flag to prevent infinite loop
		isProcessingRemoteAction = true;
		try
		{
			if (config.debugMode())
			{
				log.info("[DEBUG] Executing remote action: {} {} {}", action.getMenuAction(), action.getOption(), action.getTarget());
			}

			// Apply humanization delay if enabled (slave clients only)
			if (config.humanizationEnabled())
			{
				int delay = calculateHumanizedDelay(action.getMenuAction());
				if (delay > 0)
				{
					try
					{
						Thread.sleep(delay);
						if (config.debugMode())
						{
							log.info("[DEBUG] Applied humanization delay: {}ms", delay);
						}
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
					}
				}

				// Simulate mouse movement before clicking
				if (config.mouseMovementEnabled())
				{
					simulateMouseMovement(action);
				}
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
					// Use the inventory widget ID (149 << 16 = 9764864)
					int widgetId = 9764864;

					client.menuAction(
						slot,              // param0
						widgetId,          // param1
						MenuAction.CC_OP,  // menuAction
						slot,              // identifier (should be slot, not item.getId()!)
						item.getId(),      // itemId
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
					// Use the inventory widget ID (149 << 16 = 9764864)
					int widgetId = 9764864;

					client.menuAction(
						slot,              // param0
						widgetId,          // param1
						MenuAction.CC_OP,  // menuAction
						slot,              // identifier (should be slot, not item.getId()!)
						item.getId(),      // itemId
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

		int currentEnergy = client.getVarpValue(300); // VarPlayer SPECIAL_ATTACK_PERCENT
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

	/**
	 * Calculate humanized delay using Gaussian distribution and action-specific ranges
	 */
	private int calculateHumanizedDelay(MenuAction menuAction)
	{
		// Get base delay range from config
		int minDelay = config.minActionDelay();
		int maxDelay = config.maxActionDelay();

		// Action-specific multipliers (different actions have different speeds)
		double actionMultiplier = getActionSpeedMultiplier(menuAction);

		// Calculate mean and standard deviation for Gaussian distribution
		int mean = (minDelay + maxDelay) / 2;
		int range = maxDelay - minDelay;
		double stdDev = range / 6.0; // 99.7% of values within min-max range

		// Generate Gaussian delay
		int gaussianDelay = (int) (random.nextGaussian() * stdDev + mean);

		// Apply action-specific multiplier
		gaussianDelay = (int) (gaussianDelay * actionMultiplier);

		// Apply client-specific delay offset and speed multiplier
		gaussianDelay += clientDelayOffset;
		gaussianDelay = (int) (gaussianDelay * actionSpeedMultiplier);

		// Clamp to reasonable range (50-500ms)
		gaussianDelay = Math.max(50, Math.min(500, gaussianDelay));

		return gaussianDelay;
	}

	/**
	 * Get action-specific speed multiplier
	 */
	private double getActionSpeedMultiplier(MenuAction menuAction)
	{
		switch (menuAction)
		{
			// Fast actions (inventory/interface clicks)
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
			case WIDGET_TARGET:
			case WIDGET_TARGET_ON_WIDGET:
				return 0.7; // 30% faster

			// Medium actions (objects, ground items)
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				return 1.0; // Normal speed

			// Slow actions (NPCs, combat, trading)
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case PLAYER_FIRST_OPTION:
			case PLAYER_SECOND_OPTION:
			case PLAYER_THIRD_OPTION:
			case PLAYER_FOURTH_OPTION:
			case PLAYER_FIFTH_OPTION:
			case PLAYER_SIXTH_OPTION:
			case PLAYER_SEVENTH_OPTION:
			case PLAYER_EIGHTH_OPTION:
				return 1.3; // 30% slower

			// Item use actions
			case ITEM_USE_ON_NPC:
			case ITEM_USE_ON_GAME_OBJECT:
			case ITEM_USE_ON_GROUND_ITEM:
			case ITEM_USE_ON_ITEM:
			case ITEM_USE_ON_PLAYER:
			case WIDGET_TARGET_ON_NPC:
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case WIDGET_TARGET_ON_GROUND_ITEM:
			case WIDGET_TARGET_ON_PLAYER:
				return 1.2; // 20% slower

			default:
				return 1.0;
		}
	}

	/**
	 * Simulate mouse movement before executing action
	 */
	private void simulateMouseMovement(ActionMessage action)
	{
		try
		{
			// Get current mouse position
			net.runelite.api.Point currentPos = client.getMouseCanvasPosition();
			if (currentPos == null)
			{
				return; // Can't simulate movement without current position
			}

			// Generate target position with slight randomness
			// Note: For most actions, we don't have exact screen coordinates
			// So we'll just do small random movements to simulate "readjustment"
			int dx = random.nextInt(21) - 10; // -10 to +10 pixels
			int dy = random.nextInt(21) - 10;

			int targetX = currentPos.getX() + dx;
			int targetY = currentPos.getY() + dy;

			// Generate smooth Bezier curve path
			int steps = 5 + random.nextInt(6); // 5-10 steps
			for (int i = 1; i <= steps; i++)
			{
				double t = (double) i / steps;

				// Quadratic Bezier curve for smooth movement
				// Control point adds curvature
				int controlX = (currentPos.getX() + targetX) / 2 + (random.nextInt(21) - 10);
				int controlY = (currentPos.getY() + targetY) / 2 + (random.nextInt(21) - 10);

				int x = (int) (Math.pow(1 - t, 2) * currentPos.getX() +
							   2 * (1 - t) * t * controlX +
							   Math.pow(t, 2) * targetX);

				int y = (int) (Math.pow(1 - t, 2) * currentPos.getY() +
							   2 * (1 - t) * t * controlY +
							   Math.pow(t, 2) * targetY);

				// Small delay between movement steps (1-3ms per step)
				Thread.sleep(1 + random.nextInt(3));
			}

			if (config.debugMode())
			{
				log.info("[DEBUG] Simulated mouse movement: ({}, {}) -> ({}, {})",
					currentPos.getX(), currentPos.getY(), targetX, targetY);
			}
		}
		catch (Exception e)
		{
			log.debug("Error simulating mouse movement", e);
		}
	}
}
