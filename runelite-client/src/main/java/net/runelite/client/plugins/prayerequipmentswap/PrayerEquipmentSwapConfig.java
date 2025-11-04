/*
 * Copyright (c) 2025, RuneLite Plugin Developer
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
package net.runelite.client.plugins.prayerequipmentswap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("prayerequipmentswap")
public interface PrayerEquipmentSwapConfig extends Config
{
	@ConfigItem(
		keyName = "enableSwapping",
		name = "Enable Auto Swapping",
		description = "Automatically swap equipment when overhead prayers change",
		position = 1
	)
	default boolean enableSwapping()
	{
		return true;
	}

	@ConfigItem(
		keyName = "meleeWeaponIds",
		name = "Melee Weapon IDs",
		description = "Item IDs of melee weapons to equip (comma-separated)",
		position = 2
	)
	default String meleeWeaponIds()
	{
		return "";
	}

	@ConfigItem(
		keyName = "rangeWeaponIds",
		name = "Range Weapon IDs",
		description = "Item IDs of range weapons to equip (comma-separated)",
		position = 3
	)
	default String rangeWeaponIds()
	{
		return "";
	}

	@ConfigItem(
		keyName = "swapWeaponOnly",
		name = "Swap Weapon Only",
		description = "Only swap main-hand weapon instead of full gear set",
		position = 4
	)
	default boolean swapWeaponOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "delayMs",
		name = "Swap Delay (ms)",
		description = "Delay in milliseconds before swapping equipment (0-2000)",
		position = 5
	)
	default int delayMs()
	{
		return 0;
	}
}
