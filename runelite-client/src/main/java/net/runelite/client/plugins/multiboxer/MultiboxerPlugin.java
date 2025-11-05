package net.runelite.client.plugins.multiboxer;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

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

	@Override
	protected void startUp() throws Exception
	{
		log.info("Multiboxer plugin started!");
		networkManager.start(config.serverMode(), config.serverAddress(), config.serverPort());
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Multiboxer plugin stopped!");
		networkManager.stop();
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
			return;
		}

		// Create action message based on the type
		ActionMessage action = createActionMessage(menuEntry);

		if (action != null)
		{
			log.debug("Syncing action: {} {} {}", action.getMenuAction(), action.getOption(), action.getTarget());
			networkManager.sendAction(action);
		}
	}

	/**
	 * Determines if an action should be ignored (not synced)
	 */
	private boolean shouldIgnoreAction(MenuAction menuAction)
	{
		switch (menuAction)
		{
			// Ignore regular walking
			case WALK:
			case CC_OP: // Widget operations for minimap/world map
				return true;

			// Allow these actions
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case ITEM_USE:
			case ITEM_USE_ON_NPC:
			case ITEM_USE_ON_GAME_OBJECT:
			case ITEM_USE_ON_GROUND_ITEM:
			case ITEM_USE_ON_ITEM:
			case WIDGET_TARGET_ON_NPC:
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case PLAYER_FIRST_OPTION:
			case PLAYER_SECOND_OPTION:
			case PLAYER_THIRD_OPTION:
			case PLAYER_FOURTH_OPTION:
			case PLAYER_FIFTH_OPTION:
			case PLAYER_SIXTH_OPTION:
			case PLAYER_SEVENTH_OPTION:
			case PLAYER_EIGHTH_OPTION:
				return false;

			default:
				// By default, sync unknown actions to be safe
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
	 * Checks if the menu action is an inventory-related action
	 */
	private boolean isInventoryAction(MenuEntry menuEntry)
	{
		MenuAction action = menuEntry.getType();

		// Check if this is an item-related action
		return action == MenuAction.ITEM_USE ||
			action == MenuAction.ITEM_USE_ON_NPC ||
			action == MenuAction.ITEM_USE_ON_GAME_OBJECT ||
			action == MenuAction.ITEM_USE_ON_GROUND_ITEM ||
			action == MenuAction.ITEM_USE_ON_ITEM ||
			action == MenuAction.WIDGET_TARGET_ON_NPC ||
			action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT ||
			action.name().startsWith("CC_OP_LOW_PRIORITY") ||
			(action.getId() >= 33 && action.getId() <= 38); // Item actions
	}

	/**
	 * Resolves the item ID from an inventory slot
	 */
	private int resolveItemIdFromSlot(int param0, int param1)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return -1;
		}

		// param0 usually contains the slot index for inventory actions
		int slot = param0;
		if (slot < 0 || slot >= 28)
		{
			return -1;
		}

		Item item = inventory.getItem(slot);
		if (item == null)
		{
			return -1;
		}

		return item.getId();
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

		log.debug("Received remote action: {} {} {}", action.getMenuAction(), action.getOption(), action.getTarget());

		// If this is an inventory action with a resolved item ID, we need to find the slot
		if (action.getResolvedItemId() != -1)
		{
			int slot = findItemSlot(action.getResolvedItemId());
			if (slot == -1)
			{
				log.warn("Could not find item {} in inventory", action.getResolvedItemId());
				return;
			}
			// Update the param0 to use the correct slot on this client
			action.setParam0(slot);
		}

		// Set flag to prevent infinite loop
		isProcessingRemoteAction = true;
		try
		{
			// Execute the action on this client
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
		finally
		{
			isProcessingRemoteAction = false;
		}
	}

	/**
	 * Finds the first inventory slot containing the specified item ID
	 */
	private int findItemSlot(int itemId)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return -1;
		}

		Item[] items = inventory.getItems();
		for (int i = 0; i < items.length; i++)
		{
			Item item = items[i];
			if (item != null && item.getId() == itemId)
			{
				return i;
			}
		}

		return -1;
	}
}
