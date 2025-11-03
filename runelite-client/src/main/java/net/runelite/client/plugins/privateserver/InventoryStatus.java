/*
 * Copyright (c) 2024, Private Server Multiboxing
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
package net.runelite.client.plugins.privateserver;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks inventory status for a multibox client
 */
@Data
public class InventoryStatus
{
	private final String playerName;
	private Map<Integer, Integer> items = new HashMap<>(); // itemId -> quantity
	private int foodCount = 0;
	private int potionCount = 0;
	private long lastUpdate;

	public InventoryStatus(String playerName)
	{
		this.playerName = playerName;
		this.lastUpdate = System.currentTimeMillis();
	}

	/**
	 * Update inventory from item data
	 */
	public void updateItems(Map<Integer, Integer> newItems)
	{
		this.items = new HashMap<>(newItems);
		this.lastUpdate = System.currentTimeMillis();

		// Count food and potions (basic heuristic based on item IDs)
		this.foodCount = 0;
		this.potionCount = 0;

		for (Map.Entry<Integer, Integer> entry : items.entrySet())
		{
			int itemId = entry.getKey();
			int quantity = entry.getValue();

			// Common food items (sharks, lobsters, etc.)
			if (isFoodItem(itemId))
			{
				foodCount += quantity;
			}
			// Common potions
			else if (isPotionItem(itemId))
			{
				potionCount += quantity;
			}
		}
	}

	/**
	 * Check if this status is stale (no update in 5 seconds)
	 */
	public boolean isStale()
	{
		return System.currentTimeMillis() - lastUpdate > 5000;
	}

	/**
	 * Simple heuristic to identify food items
	 */
	private boolean isFoodItem(int itemId)
	{
		// Common OSRS food items
		return itemId == 385 || // Shark
			itemId == 379 || // Lobster
			itemId == 373 || // Swordfish
			itemId == 7946 || // Monkfish
			itemId == 361 || // Tuna
			itemId == 329 || // Salmon
			itemId == 2142; // Cooked karambwan
	}

	/**
	 * Simple heuristic to identify potion items
	 */
	private boolean isPotionItem(int itemId)
	{
		// Common OSRS potions (4-dose, 3-dose, 2-dose, 1-dose)
		// Super restore, prayer potion, super combat, etc.
		return (itemId >= 3024 && itemId <= 3030) || // Prayer potions
			(itemId >= 3024 && itemId <= 3030) || // Super restore
			(itemId >= 12695 && itemId <= 12701); // Super combat
	}

	/**
	 * Get total item count
	 */
	public int getTotalItems()
	{
		return items.values().stream().mapToInt(Integer::intValue).sum();
	}
}
