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
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "First Person",
	description = "Enables a first-person camera view with WASD movement controls",
	tags = {"camera", "view", "perspective", "first-person", "wasd", "movement"},
	enabledByDefault = false
)
public class FirstPersonPlugin extends Plugin implements KeyListener
{
	@Inject
	private Client client;

	@Inject
	private FirstPersonConfig config;

	@Inject
	private Hooks hooks;

	@Inject
	private KeyManager keyManager;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	// Track which keys are currently pressed
	private boolean wPressed = false;
	private boolean aPressed = false;
	private boolean sPressed = false;
	private boolean dPressed = false;

	@Provides
	FirstPersonConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FirstPersonConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Enable pitch relaxer to allow free camera movement
		client.setCameraPitchRelaxerEnabled(true);

		// Register the draw listener to optionally hide the local player
		hooks.registerRenderableDrawListener(drawListener);

		// Register keyboard listener for WASD movement
		keyManager.registerKeyListener(this);
	}

	@Override
	protected void shutDown()
	{
		// Disable pitch relaxer
		client.setCameraPitchRelaxerEnabled(false);

		// Reset camera to default position
		client.setCameraFocalPointX(0);
		client.setCameraFocalPointY(0);
		client.setCameraFocalPointZ(0);

		// Unregister the draw listener
		hooks.unregisterRenderableDrawListener(drawListener);

		// Unregister keyboard listener
		keyManager.unregisterKeyListener(this);

		// Reset key states
		wPressed = aPressed = sPressed = dPressed = false;
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

		// Handle WASD movement
		if (config.wasdMovement() && (wPressed || aPressed || sPressed || dPressed))
		{
			handleWASDMovement(localPlayer);
		}
	}

	private void handleWASDMovement(Player localPlayer)
	{
		WorldPoint playerWorldPos = localPlayer.getWorldPoint();
		if (playerWorldPos == null)
		{
			return;
		}

		// Get camera yaw to determine forward direction
		int cameraYaw = client.getCameraYaw();

		// Calculate movement direction based on camera angle and WASD keys
		// Camera yaw: 0 = North, 512 = East, 1024 = South, 1536 = West
		double angleRadians = Math.toRadians(cameraYaw * 360.0 / 2048.0);

		int deltaX = 0;
		int deltaY = 0;

		// W = Forward relative to camera
		if (wPressed)
		{
			deltaX += (int) Math.round(Math.sin(angleRadians));
			deltaY += (int) Math.round(Math.cos(angleRadians));
		}

		// S = Backward relative to camera
		if (sPressed)
		{
			deltaX -= (int) Math.round(Math.sin(angleRadians));
			deltaY -= (int) Math.round(Math.cos(angleRadians));
		}

		// A = Left relative to camera
		if (aPressed)
		{
			deltaX -= (int) Math.round(Math.cos(angleRadians));
			deltaY += (int) Math.round(Math.sin(angleRadians));
		}

		// D = Right relative to camera
		if (dPressed)
		{
			deltaX += (int) Math.round(Math.cos(angleRadians));
			deltaY -= (int) Math.round(Math.sin(angleRadians));
		}

		// Calculate target position
		int targetX = playerWorldPos.getX() + deltaX;
		int targetY = playerWorldPos.getY() + deltaY;
		int plane = playerWorldPos.getPlane();

		// Get the world view and check if target is in scene
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		// Check if target is within the scene
		if (!WorldPoint.isInScene(worldView, targetX, targetY))
		{
			return;
		}

		// Convert to scene coordinates
		int sceneX = targetX - worldView.getBaseX();
		int sceneY = targetY - worldView.getBaseY();

		// Check collision data to ensure tile is walkable
		CollisionData[] collisionMaps = worldView.getCollisionMaps();
		if (collisionMaps != null && plane < collisionMaps.length && collisionMaps[plane] != null)
		{
			int[][] flags = collisionMaps[plane].getFlags();

			// Ensure coordinates are within bounds
			if (sceneX >= 0 && sceneX < flags.length && sceneY >= 0 && sceneY < flags[0].length)
			{
				int tileFlag = flags[sceneX][sceneY];

				// Check if tile is walkable (no full blocking flags)
				if ((tileFlag & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0)
				{
					// Invoke walk action
					client.menuAction(sceneX, sceneY, MenuAction.WALK, 0, 0, "", "");
				}
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

	// KeyListener implementation
	@Override
	public void keyPressed(KeyEvent e)
	{
		if (!config.wasdMovement())
		{
			return;
		}

		switch (e.getKeyCode())
		{
			case KeyEvent.VK_W:
				wPressed = true;
				break;
			case KeyEvent.VK_A:
				aPressed = true;
				break;
			case KeyEvent.VK_S:
				sPressed = true;
				break;
			case KeyEvent.VK_D:
				dPressed = true;
				break;
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		switch (e.getKeyCode())
		{
			case KeyEvent.VK_W:
				wPressed = false;
				break;
			case KeyEvent.VK_A:
				aPressed = false;
				break;
			case KeyEvent.VK_S:
				sPressed = false;
				break;
			case KeyEvent.VK_D:
				dPressed = false;
				break;
		}
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		// Not used
	}
}
