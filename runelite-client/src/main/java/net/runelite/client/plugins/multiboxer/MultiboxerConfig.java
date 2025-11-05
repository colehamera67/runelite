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
		keyName = "serverAddress",
		name = "Server Address",
		description = "Address of the multiboxer server (use 'localhost' if running on same PC)",
		position = 2
	)
	default String serverAddress()
	{
		return "localhost";
	}

	@ConfigItem(
		keyName = "serverPort",
		name = "Server Port",
		description = "Port of the multiboxer server",
		position = 3
	)
	default int serverPort()
	{
		return 43594;
	}

	@ConfigItem(
		keyName = "debugMode",
		name = "Debug Mode",
		description = "Enable detailed logging of all actions (helps troubleshoot sync issues)",
		position = 4
	)
	default boolean debugMode()
	{
		return false;
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

	// Auto-Restore Prayer
	@ConfigItem(
		keyName = "autoPrayerEnabled",
		name = "Auto-Restore Prayer (Slave Only)",
		description = "Automatically drink prayer/restore potions when prayer drops below threshold",
		position = 20
	)
	default boolean autoPrayerEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "autoPrayerPercent",
		name = "Auto-Prayer %",
		description = "Prayer percentage threshold to trigger auto-restore (1-99)",
		position = 21
	)
	default int autoPrayerPercent()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "prayerPotionIds",
		name = "Prayer Potion IDs",
		description = "Comma-separated list of prayer potion item IDs (prayer potion, super restore, sanfew)",
		position = 22
	)
	default String prayerPotionIds()
	{
		return "2434,3024,139,141,143,3026,3028,3030,10925,10927,10929,10931";
	}

	// Auto-Drink Stat Potions
	@ConfigItem(
		keyName = "autoStatPotionsEnabled",
		name = "Auto-Stat Potions (Slave Only)",
		description = "Automatically drink stat-boosting potions when boosts drop below threshold",
		position = 30
	)
	default boolean autoStatPotionsEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "autoStatBoostThreshold",
		name = "Stat Boost Threshold",
		description = "Re-pot when boost drops below this level (e.g., 5 = drink when +4 or lower)",
		position = 31
	)
	default int autoStatBoostThreshold()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "statPotionIds",
		name = "Stat Potion IDs",
		description = "Comma-separated list of stat potion item IDs (super attack, strength, defense, etc.)",
		position = 32
	)
	default String statPotionIds()
	{
		return "2436,145,147,149,157,159,161,2440,163,165,167,169,171,173,2442,2444,3016,3018,3020,3022";
	}

	// Auto-Drink Stamina Potions
	@ConfigItem(
		keyName = "autoStaminaEnabled",
		name = "Auto-Stamina (Slave Only)",
		description = "Automatically drink stamina potions when run energy drops below threshold",
		position = 40
	)
	default boolean autoStaminaEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "autoStaminaPercent",
		name = "Auto-Stamina %",
		description = "Run energy percentage threshold to trigger stamina (1-99)",
		position = 41
	)
	default int autoStaminaPercent()
	{
		return 40;
	}

	@ConfigItem(
		keyName = "staminaPotionIds",
		name = "Stamina Potion IDs",
		description = "Comma-separated list of stamina potion item IDs",
		position = 42
	)
	default String staminaPotionIds()
	{
		return "12625,12627,12629,12631";
	}

	// Quick Prayer Syncing
	@ConfigItem(
		keyName = "syncQuickPrayer",
		name = "Sync Quick Prayer",
		description = "Sync quick prayer activation/deactivation between clients",
		position = 50
	)
	default boolean syncQuickPrayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncIndividualPrayers",
		name = "Sync Individual Prayers",
		description = "Sync individual prayer activation/deactivation (e.g., Piety, Protect from Melee)",
		position = 51
	)
	default boolean syncIndividualPrayers()
	{
		return true;
	}

	// Special Attack Coordination
	@ConfigItem(
		keyName = "syncSpecialAttack",
		name = "Sync Special Attack",
		description = "Sync special attack usage between clients",
		position = 60
	)
	default boolean syncSpecialAttack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "specialAttackMinEnergy",
		name = "Min Special Energy",
		description = "Minimum special attack energy required for slaves to use spec (0-100)",
		position = 61
	)
	default int specialAttackMinEnergy()
	{
		return 25;
	}

	// Combat Style Syncing
	@ConfigItem(
		keyName = "syncCombatStyle",
		name = "Sync Combat Style",
		description = "Sync attack style changes (aggressive, defensive, controlled, etc.)",
		position = 70
	)
	default boolean syncCombatStyle()
	{
		return true;
	}
}
