package net.runelite.client.plugins.multibox;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(MultiboxPlugin.CONFIG_GROUP)
public interface MultiboxConfig extends Config
{
	@ConfigSection(
		name = "General",
		description = "General multibox settings",
		position = 0
	)
	String generalSection = "general";

	@ConfigSection(
		name = "Network",
		description = "Network settings",
		position = 1
	)
	String networkSection = "network";

	@ConfigItem(
		keyName = "mode",
		name = "Mode",
		description = "Select whether this client is Master or Slave",
		position = 0,
		section = generalSection
	)
	default MultiboxMode mode()
	{
		return MultiboxMode.MASTER;
	}

	@ConfigItem(
		keyName = "port",
		name = "Port",
		description = "Port for network communication",
		position = 0,
		section = networkSection
	)
	default int port()
	{
		return 43594;
	}

	@ConfigItem(
		keyName = "masterIp",
		name = "Master IP",
		description = "IP address of the master client (for slave mode)",
		position = 1,
		section = networkSection
	)
	default String masterIp()
	{
		return "127.0.0.1";
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show Overlay",
		description = "Show IP address overlay",
		position = 2,
		section = generalSection
	)
	default boolean showOverlay()
	{
		return true;
	}
}
