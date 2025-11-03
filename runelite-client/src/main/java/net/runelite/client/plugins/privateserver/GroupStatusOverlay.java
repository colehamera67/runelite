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
 * Overlay that displays status of all connected multibox clients
 */
public class GroupStatusOverlay extends OverlayPanel
{
	private final PrivateServerPlugin plugin;
	private final PrivateServerConfig config;

	@Inject
	private GroupStatusOverlay(PrivateServerPlugin plugin, PrivateServerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showGroupStatus())
		{
			return null;
		}

		// Only show on master client
		if (config.clientMode() != PrivateServerConfig.ClientMode.MASTER)
		{
			return null;
		}

		Map<String, ClientStatus> clientStatuses = plugin.getClientStatuses();
		if (clientStatuses.isEmpty())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Group Status")
			.color(Color.GREEN)
			.build());

		// Display each client's status
		for (ClientStatus status : clientStatuses.values())
		{
			// Skip stale statuses
			if (status.isStale())
			{
				continue;
			}

			// Player name with indicator for local player
			String namePrefix = status.isLocal() ? "[YOU] " : "";
			panelComponent.getChildren().add(LineComponent.builder()
				.left(namePrefix + status.getPlayerName())
				.leftColor(status.isLocal() ? Color.CYAN : Color.WHITE)
				.build());

			// Health bar
			Color healthColor = getHealthColor(status.getHealthPercent());
			String healthText = String.format("HP: %d/%d (%d%%)",
				status.getHealth(), status.getMaxHealth(), status.getHealthPercent());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("  " + healthText)
				.leftColor(healthColor)
				.build());

			// Prayer bar
			Color prayerColor = getPrayerColor(status.getPrayerPercent());
			String prayerText = String.format("PR: %d/%d (%d%%)",
				status.getPrayer(), status.getMaxPrayer(), status.getPrayerPercent());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("  " + prayerText)
				.leftColor(prayerColor)
				.build());

			// Location
			String locationText = String.format("Loc: (%d, %d) P%d",
				status.getWorldX(), status.getWorldY(), status.getPlane());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("  " + locationText)
				.leftColor(Color.GRAY)
				.build());

			// Spacer between clients
			panelComponent.getChildren().add(LineComponent.builder()
				.left("")
				.build());
		}

		return super.render(graphics);
	}

	private Color getHealthColor(int percent)
	{
		if (percent >= 75) return Color.GREEN;
		if (percent >= 50) return Color.YELLOW;
		if (percent >= 25) return Color.ORANGE;
		return Color.RED;
	}

	private Color getPrayerColor(int percent)
	{
		if (percent >= 75) return Color.CYAN;
		if (percent >= 50) return Color.BLUE;
		if (percent >= 25) return Color.MAGENTA;
		return Color.RED;
	}
}
