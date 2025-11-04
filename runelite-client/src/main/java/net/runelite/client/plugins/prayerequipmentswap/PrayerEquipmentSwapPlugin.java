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

import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Prayer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Prayer Equipment Swap Plugin
 *
 * This plugin monitors overhead prayers and attempts to swap equipment automatically based on the active prayer:
 * - Protect from Melee / Dampen Melee → Switches to Range gear
 * - Protect from Missiles / Dampen Ranged → Switches to Melee gear
 * - Protect from Magic / Dampen Magic → Switches to Range gear
 *
 * Configuration:
 * - Enable Auto Swapping: Master toggle for the plugin
 * - Melee Weapon IDs: Comma-separated item IDs for melee weapons (e.g., "4151,11802")
 * - Range Weapon IDs: Comma-separated item IDs for range weapons (e.g., "11235,12926")
 * - Swap Weapon Only: If true, only swaps weapons. If false, swaps full gear sets
 * - Swap Delay: Delay in milliseconds before attempting swap (0-2000ms)
 *
 * Note: This plugin creates menu entries for equipment swapping. For full automatic behavior,
 * it requires the game client to process these entries, which may have limitations due to
 * RuneLite's API constraints and game rules regarding automation.
 */
@PluginDescriptor(
	name = "Prayer Equipment Swap",
	description = "Automatically swaps equipment based on active overhead prayers",
	tags = {"prayer", "equipment", "swap", "combat", "pvp"}
)
@Slf4j
public class PrayerEquipmentSwapPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private PrayerEquipmentSwapConfig config;

	@Inject
	private ClientThread clientThread;

	private Prayer lastOverheadPrayer = null;
	private Set<Integer> pendingEquipItems = new HashSet<>();

	@Provides
	PrayerEquipmentSwapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrayerEquipmentSwapConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Prayer Equipment Swap plugin started");
		lastOverheadPrayer = null;
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Prayer Equipment Swap plugin stopped");
		lastOverheadPrayer = null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.enableSwapping())
		{
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Prayer currentOverheadPrayer = getActiveOverheadPrayer();

		// Check if overhead prayer changed
		if (currentOverheadPrayer != lastOverheadPrayer)
		{
			log.debug("Overhead prayer changed from {} to {}", lastOverheadPrayer, currentOverheadPrayer);
			lastOverheadPrayer = currentOverheadPrayer;

			if (currentOverheadPrayer != null)
			{
				handlePrayerChange(currentOverheadPrayer);
			}
		}
	}

	private Prayer getActiveOverheadPrayer()
	{
		// Check standard overhead prayers
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MELEE))
		{
			return Prayer.PROTECT_FROM_MELEE;
		}
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
		{
			return Prayer.PROTECT_FROM_MISSILES;
		}
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
		{
			return Prayer.PROTECT_FROM_MAGIC;
		}

		// Check Ruinous Powers prayers
		if (client.isPrayerActive(Prayer.RP_DAMPEN_MELEE))
		{
			return Prayer.RP_DAMPEN_MELEE;
		}
		if (client.isPrayerActive(Prayer.RP_DAMPEN_RANGED))
		{
			return Prayer.RP_DAMPEN_RANGED;
		}
		if (client.isPrayerActive(Prayer.RP_DAMPEN_MAGIC))
		{
			return Prayer.RP_DAMPEN_MAGIC;
		}

		return null;
	}

	private void handlePrayerChange(Prayer prayer)
	{
		Set<Integer> itemsToEquip = null;

		// Determine which gear to equip based on the prayer
		switch (prayer)
		{
			case PROTECT_FROM_MELEE:
			case RP_DAMPEN_MELEE:
				// Melee prayer → switch to range gear
				itemsToEquip = parseItemIds(config.rangeWeaponIds());
				log.debug("Melee prayer detected, switching to range gear: {}", itemsToEquip);
				break;

			case PROTECT_FROM_MISSILES:
			case RP_DAMPEN_RANGED:
				// Range prayer → switch to melee gear
				itemsToEquip = parseItemIds(config.meleeWeaponIds());
				log.debug("Range prayer detected, switching to melee gear: {}", itemsToEquip);
				break;

			case PROTECT_FROM_MAGIC:
			case RP_DAMPEN_MAGIC:
				// Magic prayer → switch to range gear
				itemsToEquip = parseItemIds(config.rangeWeaponIds());
				log.debug("Magic prayer detected, switching to range gear: {}", itemsToEquip);
				break;
		}

		if (itemsToEquip != null && !itemsToEquip.isEmpty())
		{
			scheduleEquipmentSwap(itemsToEquip);
		}
	}

	private Set<Integer> parseItemIds(String itemIdsString)
	{
		Set<Integer> itemIds = new HashSet<>();
		if (itemIdsString == null || itemIdsString.trim().isEmpty())
		{
			return itemIds;
		}

		String[] parts = itemIdsString.split(",");
		for (String part : parts)
		{
			try
			{
				int itemId = Integer.parseInt(part.trim());
				itemIds.add(itemId);
			}
			catch (NumberFormatException e)
			{
				log.warn("Invalid item ID: {}", part);
			}
		}

		return itemIds;
	}

	private void scheduleEquipmentSwap(Set<Integer> itemIds)
	{
		pendingEquipItems = new HashSet<>(itemIds);
		clientThread.invoke(() -> equipItems(itemIds));
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.enableSwapping() || pendingEquipItems.isEmpty())
		{
			return;
		}

		// Add convenient menu entries for pending equipment swaps
		MenuEntry[] entries = event.getMenuEntries();
		for (MenuEntry entry : entries)
		{
			if (entry.getType() == MenuAction.CC_OP && entry.isItemOp())
			{
				int itemId = entry.getItemId();
				if (pendingEquipItems.contains(itemId))
				{
					// Highlight this item for swapping by modifying the entry
					entry.setDeprioritized(false);
					entry.setForceLeftClick(true);
				}
			}
		}
	}

	private void equipItems(Set<Integer> itemIds)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null)
		{
			log.debug("Inventory is null, cannot equip items");
			return;
		}

		Item[] items = inventory.getItems();
		for (int i = 0; i < items.length; i++)
		{
			Item item = items[i];
			if (item == null)
			{
				continue;
			}

			int itemId = item.getId();
			if (itemIds.contains(itemId))
			{
				log.debug("Equipping item {} from slot {}", itemId, i);
				equipItem(i, itemId);
			}
		}
	}

	private void equipItem(int slot, int itemId)
	{
		log.debug("Attempting to equip item {} from slot {}", itemId, slot);

		Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
		if (inventoryWidget == null || inventoryWidget.isHidden())
		{
			log.debug("Inventory widget is not available");
			return;
		}

		// Get the item's widget ID
		int widgetId = inventoryWidget.getId();

		// Simulate a "Wield" action by creating a menu entry
		// This uses the CC_OP action type (Component Operation) which is used for widget interactions
		// For inventory items, the "Wield" option is typically CC_OP
		clientThread.invokeLater(() -> {
			try
			{
				// Create the menu entry for the wield action
				// The game will process this similar to a user clicking "Wield"
				MenuEntry entry = client.createMenuEntry(1);
				entry.setOption("Wield");
				entry.setTarget("");
				entry.setIdentifier(itemId);
				entry.setType(MenuAction.CC_OP);
				entry.setParam0(slot);
				entry.setParam1(widgetId);
				entry.setForceLeftClick(true);

				// Set an onClick handler that will be invoked
				entry.onClick(e -> {
					log.debug("Equipping item {} (slot {})", itemId, slot);
					// When this callback is invoked, the game should process the equipment action
				});

				// The entry is now in the menu system and will be processed
				// Note: For fully automatic behavior, we'd need to programmatically "click" this entry
				// which requires deeper integration with the game client
				log.debug("Created menu entry for item {}", itemId);
			}
			catch (Exception e)
			{
				log.error("Error creating menu entry for item {}", itemId, e);
			}
		});
	}
}
