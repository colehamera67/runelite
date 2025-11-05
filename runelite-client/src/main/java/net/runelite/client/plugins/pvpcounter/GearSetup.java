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

import java.util.Set;
import com.google.common.collect.ImmutableSet;

/**
 * Helper class to identify gear types based on item IDs
 */
public class GearSetup
{
	// Common ranged weapon IDs
	public static final Set<Integer> RANGED_WEAPONS = ImmutableSet.of(
		// Bows
		839, 841, 843, 845, 847, 849, 851, 853, 855, 857, 859, 861,
		// Crossbows
		9174, 9176, 9177, 9181, 9185, 767, 4734, 11235,
		// Blowpipe
		12926,
		// Shortbows
		841, 843, 849, 853, 857, 861,
		// Longbows
		839, 845, 847, 851, 855, 859,
		// Twisted bow
		20997,
		// Dark bow
		11235,
		// Armadyl crossbow
		11785
	);

	// Common magic weapons
	public static final Set<Integer> MAGIC_WEAPONS = ImmutableSet.of(
		// Staves
		1401, 1403, 1405, 1407, 1409, 3053, 3054, 3055, 6562, 6563, 6914,
		// Tridents
		11905, 11907, 12899, 22288, 22292,
		// Ancient staff
		4675,
		// Master wand
		6914,
		// Kodai wand
		21006,
		// Toxic staff
		12904,
		// Sanguinesti staff
		22323,
		// Tumeken's shadow
		27277
	);

	// Common melee weapons
	public static final Set<Integer> MELEE_WEAPONS = ImmutableSet.of(
		// Godswords
		11694, 11696, 11698, 11700, 20368, 20372, 20374, 20376,
		// Whips
		4151, 12773, 13444, 12006,
		// Dragon weapons
		1215, 1231, 1249, 1263, 1305, 1377, 1434, 3140, 4587, 5698, 6609,
		// Abyssal weapons
		13265, 13271,
		// Scythe
		22325,
		// Rapier
		22324,
		// Saeldor
		23996, 25862,
		// Ghrazi rapier
		22324,
		// Inquisitor's mace
		24417,
		// Fang
		26219
	);

	public static AttackType getWeaponType(int itemId)
	{
		if (RANGED_WEAPONS.contains(itemId))
		{
			return AttackType.RANGED;
		}
		else if (MAGIC_WEAPONS.contains(itemId))
		{
			return AttackType.MAGIC;
		}
		else if (MELEE_WEAPONS.contains(itemId))
		{
			return AttackType.MELEE;
		}
		return AttackType.UNKNOWN;
	}
}
