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

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;

/**
 * Overlay that displays inventory status for all connected multibox clients
 */
public class InventoryManagementOverlay extends OverlayPanel
{
	private final PrivateServerPlugin plugin;
	private final PrivateServerConfig config;

	@Inject
	private InventoryManagementOverlay(PrivateServerPlugin plugin, PrivateServerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showInventoryPanel())
		{
			return null;
		}

		Map<String, InventoryStatus> inventories = plugin.getInventoryStatuses();
		if (inventories.isEmpty())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Group Inventory")
			.color(Color.ORANGE)
			.build());

		// Display each client's inventory summary
		for (InventoryStatus inventory : inventories.values())
		{
			// Skip stale statuses
			if (inventory.isStale())
			{
				continue;
			}

			// Player name
			panelComponent.getChildren().add(LineComponent.builder()
				.left(inventory.getPlayerName())
				.leftColor(Color.WHITE)
				.build());

			// Total items
			panelComponent.getChildren().add(LineComponent.builder()
				.left("  Items: " + inventory.getTotalItems() + "/28")
				.leftColor(Color.LIGHT_GRAY)
				.build());

			// Food count
			if (inventory.getFoodCount() > 0)
			{
				Color foodColor = getFoodColor(inventory.getFoodCount());
				panelComponent.getChildren().add(LineComponent.builder()
					.left("  Food: " + inventory.getFoodCount())
					.leftColor(foodColor)
					.build());
			}

			// Potion count
			if (inventory.getPotionCount() > 0)
			{
				Color potionColor = getPotionColor(inventory.getPotionCount());
				panelComponent.getChildren().add(LineComponent.builder()
					.left("  Potions: " + inventory.getPotionCount())
					.leftColor(potionColor)
					.build());
			}

			// Spacer
			panelComponent.getChildren().add(LineComponent.builder()
				.left("")
				.build());
		}

		return super.render(graphics);
	}

	private Color getFoodColor(int count)
	{
		if (count >= 10) return Color.GREEN;
		if (count >= 5) return Color.YELLOW;
		if (count >= 1) return Color.ORANGE;
		return Color.RED;
	}

	private Color getPotionColor(int count)
	{
		if (count >= 4) return Color.CYAN;
		if (count >= 2) return Color.BLUE;
		return Color.MAGENTA;
	}
}
