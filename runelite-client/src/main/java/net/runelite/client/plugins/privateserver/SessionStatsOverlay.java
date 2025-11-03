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

/**
 * Overlay that displays multiboxing session statistics
 */
public class SessionStatsOverlay extends OverlayPanel
{
	private final PrivateServerPlugin plugin;
	private final PrivateServerConfig config;

	@Inject
	private SessionStatsOverlay(PrivateServerPlugin plugin, PrivateServerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.BOTTOM_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showSessionStats())
		{
			return null;
		}

		SessionStats stats = plugin.getSessionStats();
		if (stats == null)
		{
			return null;
		}

		panelComponent.getChildren().clear();

		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Session Stats")
			.color(Color.YELLOW)
			.build());

		// Session duration
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Uptime:")
			.right(stats.getFormattedDuration())
			.build());

		// Commands sent/received
		if (config.clientMode() == PrivateServerConfig.ClientMode.MASTER)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Commands Sent:")
				.right(String.valueOf(stats.getCommandsSent()))
				.rightColor(Color.GREEN)
				.build());
		}
		else
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Commands Received:")
				.right(String.valueOf(stats.getCommandsReceived()))
				.rightColor(Color.CYAN)
				.build());
		}

		// Clicks synced
		if (stats.getClicksSynced() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Clicks Synced:")
				.right(String.valueOf(stats.getClicksSynced()))
				.build());
		}

		// Prayers synced
		if (stats.getPrayersSynced() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Prayers Synced:")
				.right(String.valueOf(stats.getPrayersSynced()))
				.build());
		}

		// Specs synced
		if (stats.getSpecsSynced() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Specs Synced:")
				.right(String.valueOf(stats.getSpecsSynced()))
				.build());
		}

		// Targets assigned
		if (stats.getTargetsAssigned() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Targets Assigned:")
				.right(String.valueOf(stats.getTargetsAssigned()))
				.build());
		}

		// Status updates
		if (stats.getStatusUpdatesSent() > 0 || stats.getStatusUpdatesReceived() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Status Updates:")
				.right(stats.getStatusUpdatesSent() + "/" + stats.getStatusUpdatesReceived())
				.build());
		}

		return super.render(graphics);
	}
}
