/*
 * Copyright (c) 2025, RuneLite
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
package net.runelite.client.plugins.firstperson;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "First Person",
	description = "Enables a first-person camera view",
	tags = {"camera", "view", "perspective", "first-person"},
	enabledByDefault = false
)
public class FirstPersonPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private FirstPersonConfig config;

	@Inject
	private Hooks hooks;

	private boolean wasPitchRelaxerEnabled;
	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	@Provides
	FirstPersonConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FirstPersonConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Save the current pitch relaxer state
		wasPitchRelaxerEnabled = client.getCameraPitchRelaxerEnabled();

		// Enable pitch relaxer to allow free camera movement
		client.setCameraPitchRelaxerEnabled(true);

		// Register the draw listener to optionally hide the local player
		hooks.registerRenderableDrawListener(drawListener);
	}

	@Override
	protected void shutDown()
	{
		// Restore the original pitch relaxer state
		client.setCameraPitchRelaxerEnabled(wasPitchRelaxerEnabled);

		// Reset camera to default position
		client.setCameraFocalPointX(0);
		client.setCameraFocalPointY(0);
		client.setCameraFocalPointZ(0);

		// Unregister the draw listener
		hooks.unregisterRenderableDrawListener(drawListener);
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Get the player's position
		LocalPoint playerPos = localPlayer.getLocalLocation();
		if (playerPos == null)
		{
			return;
		}

		// Calculate camera position at player's location with height offset
		int cameraHeight = config.cameraHeight();

		// Set camera focal point to player's position
		// In RuneScape's coordinate system, Y and Z are swapped
		client.setCameraFocalPointX(playerPos.getX());
		client.setCameraFocalPointY(playerPos.getY());
		client.setCameraFocalPointZ(cameraHeight);

		// Apply pitch offset if configured
		int pitchOffset = config.pitchOffset();
		if (pitchOffset != 0)
		{
			int currentPitch = client.getCameraPitch();
			if (config.smoothCamera())
			{
				client.setCameraPitchTarget(currentPitch + pitchOffset);
			}
			else
			{
				// For instant camera, we'd need to set pitch directly
				// but the API only provides target setters for smooth transitions
				client.setCameraPitchTarget(currentPitch + pitchOffset);
			}
		}
	}

	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		// If the renderable is the local player and hideLocalPlayer is enabled, hide it
		if (renderable instanceof Player)
		{
			Player player = (Player) renderable;
			Player localPlayer = client.getLocalPlayer();

			if (player == localPlayer && config.hideLocalPlayer())
			{
				return false; // Hide the local player
			}
		}

		return true; // Draw everything else normally
	}
}
