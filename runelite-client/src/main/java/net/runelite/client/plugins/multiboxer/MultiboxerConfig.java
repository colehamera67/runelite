package net.runelite.client.plugins.multiboxer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("multiboxer")
public interface MultiboxerConfig extends Config
{
	@ConfigItem(
		keyName = "enabled",
		name = "Enable Syncing",
		description = "Enable or disable action synchronization",
		position = 1
	)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "serverMode",
		name = "Server Mode",
		description = "Run as server (host) or client (connect to host)",
		position = 2
	)
	default boolean serverMode()
	{
		return false;
	}

	@ConfigItem(
		keyName = "serverAddress",
		name = "Server Address",
		description = "Address of the multiboxer sync server (for client mode)",
		position = 3
	)
	default String serverAddress()
	{
		return "localhost";
	}

	@ConfigItem(
		keyName = "serverPort",
		name = "Server Port",
		description = "Port for the multiboxer sync server",
		position = 4
	)
	default int serverPort()
	{
		return 43594;
	}

	@ConfigItem(
		keyName = "syncInventoryActions",
		name = "Sync Inventory Actions",
		description = "Synchronize inventory item usage",
		position = 5
	)
	default boolean syncInventoryActions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncNpcInteractions",
		name = "Sync NPC Interactions",
		description = "Synchronize NPC attacks, trading, etc.",
		position = 6
	)
	default boolean syncNpcInteractions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncObjectInteractions",
		name = "Sync Object Interactions",
		description = "Synchronize object clicking and interactions",
		position = 7
	)
	default boolean syncObjectInteractions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncPlayerInteractions",
		name = "Sync Player Interactions",
		description = "Synchronize player trading, following, etc.",
		position = 8
	)
	default boolean syncPlayerInteractions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoEatEnabled",
		name = "Auto-Eat (Slave Only)",
		description = "Automatically eat food when health drops below threshold (only works on slave clients)",
		position = 10
	)
	default boolean autoEatEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "autoEatHealthPercent",
		name = "Auto-Eat Health %",
		description = "Health percentage threshold to trigger auto-eat (1-99)",
		position = 11
	)
	default int autoEatHealthPercent()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "foodItemIds",
		name = "Food Item IDs",
		description = "Comma-separated list of food item IDs to eat (e.g., 385,373,379 for shark,lobster,cooked meat)",
		position = 12
	)
	default String foodItemIds()
	{
		return "385,373,379,333,329,361,7946,2142,391,365,380,386";
	}
}
