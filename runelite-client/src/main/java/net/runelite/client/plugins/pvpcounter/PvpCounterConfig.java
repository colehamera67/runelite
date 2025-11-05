/*
 * Copyright (c) 2025, YourName <https://github.com/yourname>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.pvpcounter;

import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("pvpcounter")
public interface PvpCounterConfig extends Config
{
	@ConfigSection(
		name = "Display Settings",
		description = "Settings for the overlay display",
		position = 0
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Hotkey Settings",
		description = "Settings for counter setup hotkeys",
		position = 1
	)
	String hotkeySection = "hotkeys";

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Display the PvP counter overlay",
		section = displaySection,
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showAttackType",
		name = "Show attack type",
		description = "Display the detected opponent attack type",
		section = displaySection,
		position = 1
	)
	default boolean showAttackType()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRecommendedPrayer",
		name = "Show recommended prayer",
		description = "Display the recommended prayer to use",
		section = displaySection,
		position = 2
	)
	default boolean showRecommendedPrayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRecommendedGear",
		name = "Show recommended gear",
		description = "Display the recommended gear type",
		section = displaySection,
		position = 3
	)
	default boolean showRecommendedGear()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightPrayer",
		name = "Highlight prayer",
		description = "Highlight the recommended prayer with color",
		section = displaySection,
		position = 4
	)
	default boolean highlightPrayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "meleeCounterHotkey",
		name = "Melee counter hotkey",
		description = "Hotkey to switch to melee counter setup (attack ranged opponents)",
		section = hotkeySection,
		position = 0
	)
	default Keybind meleeCounterHotkey()
	{
		return new Keybind(KeyEvent.VK_F1, 0);
	}

	@ConfigItem(
		keyName = "rangedCounterHotkey",
		name = "Ranged counter hotkey",
		description = "Hotkey to switch to ranged counter setup (attack magic opponents)",
		section = hotkeySection,
		position = 1
	)
	default Keybind rangedCounterHotkey()
	{
		return new Keybind(KeyEvent.VK_F2, 0);
	}

	@ConfigItem(
		keyName = "magicCounterHotkey",
		name = "Magic counter hotkey",
		description = "Hotkey to switch to magic counter setup (attack melee opponents)",
		section = hotkeySection,
		position = 2
	)
	default Keybind magicCounterHotkey()
	{
		return new Keybind(KeyEvent.VK_F3, 0);
	}

	@ConfigItem(
		keyName = "enableAutoSwitch",
		name = "Enable auto-switch",
		description = "Automatically suggest counter setup based on opponent's attack",
		section = hotkeySection,
		position = 3
	)
	default boolean enableAutoSwitch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showChatNotifications",
		name = "Show chat notifications",
		description = "Show chat messages when opponent attack type changes",
		section = displaySection,
		position = 6
	)
	default boolean showChatNotifications()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHotkeyMessages",
		name = "Show hotkey messages",
		description = "Show chat message when you press a counter setup hotkey",
		section = hotkeySection,
		position = 4
	)
	default boolean showHotkeyMessages()
	{
		return true;
	}
}
