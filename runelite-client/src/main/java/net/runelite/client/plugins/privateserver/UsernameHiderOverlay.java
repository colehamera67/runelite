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

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Overlay that draws black bars over player usernames for privacy
 */
public class UsernameHiderOverlay extends Overlay
{
	private final Client client;
	private final PrivateServerConfig config;

	@Inject
	private UsernameHiderOverlay(Client client, PrivateServerConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.hideUsernames())
		{
			return null;
		}

		// Hide local player name
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			drawBlackBar(graphics, localPlayer);
		}

		// Hide other players' names
		for (Player player : client.getPlayers())
		{
			if (player != null && player != localPlayer)
			{
				drawBlackBar(graphics, player);
			}
		}

		return null;
	}

	private void drawBlackBar(Graphics2D graphics, Player player)
	{
		String name = player.getName();
		if (name == null)
		{
			return;
		}

		Point textLocation = player.getCanvasTextLocation(graphics, name, player.getLogicalHeight() + 40);
		if (textLocation != null)
		{
			int textWidth = graphics.getFontMetrics().stringWidth(name);
			int textHeight = graphics.getFontMetrics().getHeight();

			// Draw black rectangle over the username
			graphics.setColor(Color.BLACK);
			graphics.fillRect(
				textLocation.getX() - 2,
				textLocation.getY() - textHeight + 2,
				textWidth + 4,
				textHeight + 2
			);
		}
	}
}
