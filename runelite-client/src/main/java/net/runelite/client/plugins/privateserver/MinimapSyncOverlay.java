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
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Ellipse2D;
import java.util.Map;

/**
 * Overlay that displays other multibox clients on the minimap
 */
public class MinimapSyncOverlay extends Overlay
{
	private final Client client;
	private final PrivateServerPlugin plugin;
	private final PrivateServerConfig config;

	@Inject
	private MinimapSyncOverlay(Client client, PrivateServerPlugin plugin, PrivateServerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.minimapSync())
		{
			return null;
		}

		Map<String, ClientStatus> clientStatuses = plugin.getClientStatuses();
		if (clientStatuses.isEmpty())
		{
			return null;
		}

		WorldPoint localPlayerLocation = client.getLocalPlayer().getWorldLocation();

		// Draw markers for each client
		for (ClientStatus status : clientStatuses.values())
		{
			// Skip stale statuses
			if (status.isStale())
			{
				continue;
			}

			// Skip local player
			if (status.isLocal())
			{
				continue;
			}

			WorldPoint clientLocation = new WorldPoint(
				status.getWorldX(),
				status.getWorldY(),
				status.getPlane()
			);

			// Only draw if on same plane
			if (clientLocation.getPlane() != localPlayerLocation.getPlane())
			{
				continue;
			}

			// Get minimap point
			Point minimapPoint = getMinimapPoint(clientLocation);
			if (minimapPoint == null)
			{
				continue;
			}

			// Draw marker
			drawMinimapMarker(graphics, minimapPoint, status.getPlayerName());
		}

		return null;
	}

	private Point getMinimapPoint(WorldPoint worldPoint)
	{
		// Convert world point to local point first
		LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
		if (localPoint == null)
		{
			return null;
		}

		// Then convert to minimap point using Perspective
		net.runelite.api.Point minimapPoint = Perspective.localToMinimap(client, localPoint);
		if (minimapPoint == null)
		{
			return null;
		}

		return new Point(minimapPoint.getX(), minimapPoint.getY());
	}

	private void drawMinimapMarker(Graphics2D graphics, Point point, String name)
	{
		// Draw circle
		graphics.setColor(Color.CYAN);
		graphics.setStroke(new BasicStroke(2));

		int x = point.x;
		int y = point.y;
		int radius = 4;

		Ellipse2D circle = new Ellipse2D.Double(
			x - radius,
			y - radius,
			radius * 2,
			radius * 2
		);
		graphics.draw(circle);

		// Fill with semi-transparent color
		graphics.setColor(new Color(0, 255, 255, 100));
		graphics.fill(circle);

		// Draw name label
		graphics.setColor(Color.WHITE);
		FontMetrics fm = graphics.getFontMetrics();
		int textWidth = fm.stringWidth(name);
		int textHeight = fm.getHeight();

		graphics.drawString(name, x - textWidth / 2, y - radius - 2);
	}
}
