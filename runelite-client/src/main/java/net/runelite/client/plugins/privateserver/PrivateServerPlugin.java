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

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@PluginDescriptor(
	name = "Private",
	description = "Multiboxing plugin for OSRS private servers with synchronized actions and hotkey support",
	tags = {"multibox", "private", "server", "sync", "multiple", "clients"},
	enabledByDefault = false
)
public class PrivateServerPlugin extends Plugin
{
	static final String CONFIG_GROUP = "privateserver";

	@Inject
	private Client client;

	@Inject
	private PrivateServerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Notifier notifier;

	@Inject
	private PrivateServerOverlay overlay;

	@Inject
	private UsernameHiderOverlay usernameHiderOverlay;

	@Inject
	private GroupStatusOverlay groupStatusOverlay;

	@Inject
	private MinimapSyncOverlay minimapSyncOverlay;

	private boolean multiboxingEnabled = true;
	private boolean clickSyncEnabled = false;
	private boolean pluginActive = false;

	// Networking
	private MultiboxServer server;
	private MultiboxClient slaveClient;

	// Client status tracking
	private final Map<String, ClientStatus> clientStatuses = new ConcurrentHashMap<>();
	private int tickCounter = 0;

	// Camera tracking
	private int lastCameraYaw = 0;
	private int lastCameraPitch = 0;

	@Provides
	PrivateServerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrivateServerConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Private Server Multiboxing Plugin started");
		pluginActive = true;
		multiboxingEnabled = config.enableMultiboxing();
		clickSyncEnabled = config.clickSync();

		// Register hotkey listeners
		keyManager.registerKeyListener(toggleMultiboxHotkey);
		keyManager.registerKeyListener(syncPrayerHotkey);
		keyManager.registerKeyListener(syncSpecHotkey);
		keyManager.registerKeyListener(toggleClickSyncHotkey);
		keyManager.registerKeyListener(followLeaderHotkey);

		// Initialize networking based on mode
		initializeNetworking();

		// Add overlay if enabled
		if (config.showMultiboxOverlay())
		{
			overlayManager.add(overlay);
		}

		// Add group status overlay if enabled
		if (config.showGroupStatus())
		{
			overlayManager.add(groupStatusOverlay);
		}

		// Add minimap sync overlay if enabled
		if (config.minimapSync())
		{
			overlayManager.add(minimapSyncOverlay);
		}

		// Disable client instance check for multiple clients
		if (config.allowMultipleClients())
		{
			disableInstanceCheck();
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Private Server Multiboxing Plugin stopped");
		pluginActive = false;

		// Unregister hotkey listeners
		keyManager.unregisterKeyListener(toggleMultiboxHotkey);
		keyManager.unregisterKeyListener(syncPrayerHotkey);
		keyManager.unregisterKeyListener(syncSpecHotkey);
		keyManager.unregisterKeyListener(toggleClickSyncHotkey);
		keyManager.unregisterKeyListener(followLeaderHotkey);

		// Stop networking
		stopNetworking();

		// Remove overlays
		overlayManager.remove(overlay);
		overlayManager.remove(groupStatusOverlay);
		overlayManager.remove(minimapSyncOverlay);

		// Clear status tracking
		clientStatuses.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP))
		{
			return;
		}

		switch (event.getKey())
		{
			case "enableMultiboxing":
				multiboxingEnabled = config.enableMultiboxing();
				log.info("Multiboxing " + (multiboxingEnabled ? "enabled" : "disabled"));
				break;
			case "clickSync":
				clickSyncEnabled = config.clickSync();
				log.info("Click sync " + (clickSyncEnabled ? "enabled" : "disabled"));
				break;
			case "showMultiboxOverlay":
				if (config.showMultiboxOverlay())
				{
					overlayManager.add(overlay);
				}
				else
				{
					overlayManager.remove(overlay);
				}
				break;
			case "showGroupStatus":
				if (config.showGroupStatus())
				{
					overlayManager.add(groupStatusOverlay);
				}
				else
				{
					overlayManager.remove(groupStatusOverlay);
				}
				break;
			case "minimapSync":
				if (config.minimapSync())
				{
					overlayManager.add(minimapSyncOverlay);
				}
				else
				{
					overlayManager.remove(minimapSyncOverlay);
				}
				break;
			case "allowMultipleClients":
				if (config.allowMultipleClients())
				{
					disableInstanceCheck();
				}
				break;
			case "clientMode":
			case "serverPort":
			case "masterHost":
				// Reinitialize networking when settings change
				stopNetworking();
				initializeNetworking();
				break;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			log.debug("Player logged in - multiboxing features active");
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!multiboxingEnabled || !pluginActive)
		{
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		// Hide username in chatbox if enabled
		if (config.hideUsernames())
		{
			hideUsernameInChatbox();
		}

		// Broadcast status updates every 2 ticks (~1.2 seconds)
		if (config.showGroupStatus())
		{
			tickCounter++;
			if (tickCounter >= 2)
			{
				tickCounter = 0;
				updateAndBroadcastStatus();
			}
		}

		// Broadcast camera updates if changed (master only)
		if (config.cameraSync() && config.clientMode() == PrivateServerConfig.ClientMode.MASTER && server != null)
		{
			int currentYaw = client.getCameraYaw();
			int currentPitch = client.getCameraPitch();

			// Only broadcast if camera has changed
			if (currentYaw != lastCameraYaw || currentPitch != lastCameraPitch)
			{
				lastCameraYaw = currentYaw;
				lastCameraPitch = currentPitch;

				String cameraData = String.format("%d|%d", currentYaw, currentPitch);
				MultiboxCommand cameraCommand = new MultiboxCommand(
					MultiboxCommand.CommandType.CAMERA_SYNC,
					cameraData
				);

				server.broadcast(cameraCommand);
				log.debug("Broadcasted camera update: yaw={}, pitch={}", currentYaw, currentPitch);
			}
		}
	}

	/**
	 * Hide the username widget in the chatbox
	 */
	private void hideUsernameInChatbox()
	{
		// Chatbox name widget ID - may need adjustment based on RuneLite version
		// Common widget IDs for chatbox player name: 162:34, 162:35
		Widget nameWidget = client.getWidget(162, 34);
		if (nameWidget != null)
		{
			nameWidget.setHidden(true);
		}

		// Also try alternative widget ID
		Widget nameWidget2 = client.getWidget(162, 35);
		if (nameWidget2 != null)
		{
			nameWidget2.setHidden(true);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		// Track equipment changes for broadcasting
		if (!multiboxingEnabled || !pluginActive)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || localPlayer.getName() == null)
		{
			return;
		}

		ItemContainer container = event.getItemContainer();
		if (container == null)
		{
			return;
		}

		// Handle equipment tracking
		if (config.equipmentSync() && event.getContainerId() == InventoryID.EQUIPMENT.getId())
		{
			// Broadcast equipment changes if master
			if (config.clientMode() == PrivateServerConfig.ClientMode.MASTER && server != null)
			{
				broadcastEquipmentUpdate(container);
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// Only broadcast clicks if we're master and multiboxing enabled
		if (!multiboxingEnabled)
		{
			return;
		}

		if (config.clientMode() != PrivateServerConfig.ClientMode.MASTER)
		{
			return;
		}

		if (server == null || server.getClientCount() == 0)
		{
			return;
		}

		// Regular click sync (if enabled)
		if (!clickSyncEnabled)
		{
			return;
		}

		// Get click data from the event
		MenuEntry menuEntry = event.getMenuEntry();
		MenuAction action = menuEntry.getType();

		// Get the actual screen coordinates where the player clicked
		int screenX = client.getMouseCanvasPosition().getX();
		int screenY = client.getMouseCanvasPosition().getY();

		// Get event parameters
		int param0 = event.getParam0();
		int param1 = event.getParam1();
		int identifier = event.getId();
		int itemId = event.getItemId();

		// Log the action type and coordinates for debugging
		log.info("Master click: action={} ({}), param0={}, param1={}, id={}, itemId={}, screen=({},{}), option='{}', target='{}'",
			action, action.name(), param0, param1, identifier, itemId, screenX, screenY,
			menuEntry.getOption(), menuEntry.getTarget());

		// Additional debug for player position and scene info
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null)
		{
			log.info("Master position: world=({},{},{}), baseX={}, baseY={}",
				localPlayer.getWorldLocation().getX(),
				localPlayer.getWorldLocation().getY(),
				localPlayer.getWorldLocation().getPlane(),
				client.getBaseX(),
				client.getBaseY());
		}

		// For object interactions, convert to world coordinates
		String worldCoords = "";
		if (action == MenuAction.GAME_OBJECT_FIRST_OPTION ||
		    action == MenuAction.GAME_OBJECT_SECOND_OPTION ||
		    action == MenuAction.GAME_OBJECT_THIRD_OPTION ||
		    action == MenuAction.GAME_OBJECT_FOURTH_OPTION ||
		    action == MenuAction.GAME_OBJECT_FIFTH_OPTION ||
		    action == MenuAction.EXAMINE_OBJECT)
		{
			// For object interactions, param0 and param1 are scene coordinates
			int worldX = param0 + client.getBaseX();
			int worldY = param1 + client.getBaseY();
			int plane = client.getPlane();
			worldCoords = worldX + "," + worldY + "," + plane;
			log.info("Master {}: scene=({},{}) -> world=({},{},{})", action.name(), param0, param1, worldX, worldY, plane);
		}

		MultiboxCommand command = new MultiboxCommand(
			MultiboxCommand.CommandType.CLICK_SYNC,
			screenX,
			screenY,
			param0,
			param1,
			menuEntry.getType().getId(),
			identifier,
			itemId,
			menuEntry.getOption(),
			menuEntry.getTarget(),
			worldCoords
		);

		server.broadcast(command);
	}

	// Hotkey: Toggle multiboxing on/off
	private final HotkeyListener toggleMultiboxHotkey = new HotkeyListener(() -> config.toggleMultiboxHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			multiboxingEnabled = !multiboxingEnabled;
			log.info("Multiboxing " + (multiboxingEnabled ? "enabled" : "disabled"));
			client.addChatMessage(
				net.runelite.api.ChatMessageType.GAMEMESSAGE,
				"",
				"Multiboxing " + (multiboxingEnabled ? "enabled" : "disabled"),
				null
			);
		}
	};

	// Hotkey: Sync prayer activation
	private final HotkeyListener syncPrayerHotkey = new HotkeyListener(() -> config.syncPrayerHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (!multiboxingEnabled || !config.quickPrayerSync())
			{
				return;
			}

			activateQuickPrayer();

			// Broadcast to slaves if master
			if (config.clientMode() == PrivateServerConfig.ClientMode.MASTER && server != null)
			{
				server.broadcast(new MultiboxCommand(MultiboxCommand.CommandType.ACTIVATE_PRAYER));
			}

			log.debug("Quick prayer sync activated");
		}
	};

	// Hotkey: Sync special attack
	private final HotkeyListener syncSpecHotkey = new HotkeyListener(() -> config.syncSpecHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (!multiboxingEnabled || !config.quickSpecSync())
			{
				return;
			}

			activateSpecialAttack();

			// Broadcast to slaves if master
			if (config.clientMode() == PrivateServerConfig.ClientMode.MASTER && server != null)
			{
				server.broadcast(new MultiboxCommand(MultiboxCommand.CommandType.ACTIVATE_SPEC));
			}

			log.debug("Special attack sync activated");
		}
	};

	// Hotkey: Toggle click synchronization
	private final HotkeyListener toggleClickSyncHotkey = new HotkeyListener(() -> config.toggleClickSyncHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clickSyncEnabled = !clickSyncEnabled;
			log.info("Click sync " + (clickSyncEnabled ? "enabled" : "disabled"));
			client.addChatMessage(
				net.runelite.api.ChatMessageType.GAMEMESSAGE,
				"",
				"Click sync " + (clickSyncEnabled ? "enabled" : "disabled"),
				null
			);
		}
	};

	// Hotkey: Follow leader
	private final HotkeyListener followLeaderHotkey = new HotkeyListener(() -> config.followLeaderHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (!multiboxingEnabled)
			{
				return;
			}

			// Get master player name
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer == null)
			{
				return;
			}

			String playerName = localPlayer.getName();
			if (playerName == null)
			{
				return;
			}

			// Broadcast to slaves if master
			if (config.clientMode() == PrivateServerConfig.ClientMode.MASTER && server != null)
			{
				server.broadcast(new MultiboxCommand(MultiboxCommand.CommandType.FOLLOW_LEADER, playerName));
				log.info("Follow leader command sent: {}", playerName);
			}

			client.addChatMessage(
				net.runelite.api.ChatMessageType.GAMEMESSAGE,
				"",
				"Follow leader activated",
				null
			);
		}
	};

	/**
	 * Activates quick prayers by clicking the prayer orb widget
	 */
	private void activateQuickPrayer()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			log.debug("Cannot activate prayer - not logged in");
			return;
		}

		clientThread.invoke(() ->
		{
			// Try to find the prayer button widget
			Widget prayerWidget = client.getWidget(InterfaceID.Orbs.PRAYERBUTTON);

			if (prayerWidget == null)
			{
				log.warn("Prayer widget not found!");
				return;
			}

			if (prayerWidget.isHidden())
			{
				log.warn("Prayer widget is hidden!");
				return;
			}

			log.debug("Clicking prayer widget: {}", prayerWidget.getId());

			client.menuAction(
				1,
				prayerWidget.getId(),
				MenuAction.WIDGET_FIRST_OPTION,
				0,
				-1,
				"Activate",
				""
			);

			log.info("Quick prayer toggled");
		});
	}

	/**
	 * Activates special attack by clicking the spec orb widget
	 */
	private void activateSpecialAttack()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			log.debug("Cannot activate spec - not logged in");
			return;
		}

		clientThread.invoke(() ->
		{
			// Try to find the special attack button widget
			Widget specWidget = client.getWidget(InterfaceID.Orbs.SPECBUTTON);

			if (specWidget == null)
			{
				log.warn("Spec widget not found!");
				return;
			}

			if (specWidget.isHidden())
			{
				log.warn("Spec widget is hidden!");
				return;
			}

			log.debug("Clicking spec widget: {}", specWidget.getId());

			client.menuAction(
				1,
				specWidget.getId(),
				MenuAction.WIDGET_FIRST_OPTION,
				0,
				-1,
				"Use",
				""
			);

			log.info("Special attack toggled");
		});
	}

	/**
	 * Disables the RuneLite instance check to allow multiple clients
	 */
	private void disableInstanceCheck()
	{
		// Set system property to allow multiple instances
		System.setProperty("runelite.launcher.nojvm", "true");
		log.info("Multiple client instances allowed");
	}

	/**
	 * Check if multiboxing is currently enabled
	 */
	public boolean isMultiboxingEnabled()
	{
		return multiboxingEnabled && pluginActive;
	}

	/**
	 * Check if click sync is currently enabled
	 */
	public boolean isClickSyncEnabled()
	{
		return clickSyncEnabled && multiboxingEnabled && pluginActive;
	}

	/**
	 * Initialize networking based on configured mode
	 */
	private void initializeNetworking()
	{
		if (config.clientMode() == PrivateServerConfig.ClientMode.MASTER)
		{
			// Start server for master mode
			server = new MultiboxServer(config.serverPort());
			server.start();

			String localIP = getLocalIPAddress();
			log.info("Started as MASTER on port {}", config.serverPort());
			log.info("Local IP addresses - LAN: {} | Localhost: 127.0.0.1", localIP);

			// Show in chat
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(
					net.runelite.api.ChatMessageType.GAMEMESSAGE,
					"",
					"Master server started on port " + config.serverPort(),
					null
				);
				client.addChatMessage(
					net.runelite.api.ChatMessageType.GAMEMESSAGE,
					"",
					"Connect slaves to: " + localIP + ":" + config.serverPort(),
					null
				);
			}
		}
		else
		{
			// Connect to master for slave mode
			slaveClient = new MultiboxClient(config.masterHost(), config.serverPort(), this::handleCommand);

			// Configure proxy if enabled
			if (config.useProxy())
			{
				slaveClient.setProxySettings(
					true,
					config.proxyType().name(),
					config.proxyHost(),
					config.proxyPort(),
					config.proxyUsername(),
					config.proxyPassword()
				);
				log.info("Proxy configured: {} {}:{}", config.proxyType(), config.proxyHost(), config.proxyPort());
			}

			if (config.autoConnect())
			{
				if (slaveClient.connect())
				{
					log.info("Connected as SLAVE to {}:{}", config.masterHost(), config.serverPort());

					if (config.audioNotifications())
					{
						notifier.notify("Connected to master server");
					}
				}
				else
				{
					log.warn("Failed to auto-connect to master");

					if (config.audioNotifications())
					{
						notifier.notify("Failed to connect to master server");
					}
				}
			}
		}
	}

	/**
	 * Stop all networking
	 */
	private void stopNetworking()
	{
		if (server != null)
		{
			server.stop();
			server = null;
		}

		if (slaveClient != null)
		{
			slaveClient.disconnect();
			slaveClient = null;
		}
	}

	/**
	 * Handle commands received from master (slave mode)
	 */
	private void handleCommand(MultiboxCommand command)
	{
		log.info("Received command from master: {}", command.getType());

		switch (command.getType())
		{
			case ACTIVATE_PRAYER:
				log.info("Executing ACTIVATE_PRAYER command");
				activateQuickPrayer();
				break;
			case ACTIVATE_SPEC:
				log.info("Executing ACTIVATE_SPEC command");
				activateSpecialAttack();
				break;
			case CLICK_SYNC:
				log.info("Executing CLICK_SYNC command: option='{}' target='{}' | screen=({}, {}), params=({}, {}), id={}, itemId={}, action={}",
					command.getMenuOption(), command.getMenuTarget(),
					command.getScreenX(), command.getScreenY(),
					command.getParam0(), command.getParam1(), command.getIdentifier(), command.getItemId(),
					command.getMenuAction());
				simulateClick(command);
				break;
			case FOLLOW_LEADER:
				String leaderName = command.getExtraData();
				log.info("Follow leader command received: {}", leaderName);
				followPlayer(leaderName);
				break;
			case STATUS_UPDATE:
				String statusData = command.getExtraData();
				if (!statusData.isEmpty())
				{
					String[] parts = statusData.split("\\|");
					if (parts.length >= 1)
					{
						String playerName = parts[0];
						ClientStatus status = clientStatuses.computeIfAbsent(playerName, ClientStatus::new);
						status.updateFromString(statusData);
						log.debug("Updated status for {}: HP={}/{} PR={}/{}",
							playerName, status.getHealth(), status.getMaxHealth(),
							status.getPrayer(), status.getMaxPrayer());
					}
				}
				break;
			case CAMERA_SYNC:
				String cameraData = command.getExtraData();
				if (!cameraData.isEmpty())
				{
					String[] cameraParts = cameraData.split("\\|");
					if (cameraParts.length >= 2)
					{
						int yaw = Integer.parseInt(cameraParts[0]);
						int pitch = Integer.parseInt(cameraParts[1]);
						applyCameraSync(yaw, pitch);
					}
				}
				break;
			case EQUIPMENT_SYNC:
				String equipmentData = command.getExtraData();
				if (!equipmentData.isEmpty())
				{
					applyEquipmentSync(equipmentData);
				}
				break;
			case PING:
				log.debug("Ping received from master");
				break;
			case DISCONNECT:
				if (slaveClient != null)
				{
					slaveClient.disconnect();
				}
				break;
		}
	}

	/**
	 * Simulate a click on the slave client
	 */
	private void simulateClick(MultiboxCommand command)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			log.debug("Cannot simulate click - not logged in");
			return;
		}

		clientThread.invoke(() ->
		{
			try
			{
				MenuAction action = MenuAction.of(command.getMenuAction());

				// Use the exact parameters from the master
				int p0 = command.getParam0();
				int p1 = command.getParam1();
				int id = command.getIdentifier();
				int itemId = command.getItemId();

				// For inventory item interactions, find the item by ID instead of using slot position
				if (action == MenuAction.ITEM_FIRST_OPTION ||
				    action == MenuAction.ITEM_SECOND_OPTION ||
				    action == MenuAction.ITEM_THIRD_OPTION ||
				    action == MenuAction.ITEM_FOURTH_OPTION ||
				    action == MenuAction.ITEM_FIFTH_OPTION ||
				    action == MenuAction.ITEM_USE)
				{
					// Get the slave's inventory
					ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
					if (inventory != null)
					{
						Item[] items = inventory.getItems();
						int foundSlot = -1;

						// Find the matching item in the slave's inventory
						for (int i = 0; i < items.length; i++)
						{
							if (items[i] != null && items[i].getId() == itemId)
							{
								foundSlot = i;
								break;
							}
						}

						if (foundSlot != -1)
						{
							// Update p0 to use the correct slot on this slave
							log.info("Slave inventory: Found item {} in slot {} (master was in slot {})",
								itemId, foundSlot, p0);
							p0 = foundSlot;
						}
						else
						{
							log.warn("Slave inventory: Could not find item {} in inventory, item may not exist on slave", itemId);
							return;
						}
					}
					else
					{
						log.warn("Slave inventory container is null");
						return;
					}
				}
				// For object interactions, convert world coordinates to scene coordinates
				else if ((action == MenuAction.GAME_OBJECT_FIRST_OPTION ||
				          action == MenuAction.GAME_OBJECT_SECOND_OPTION ||
				          action == MenuAction.GAME_OBJECT_THIRD_OPTION ||
				          action == MenuAction.GAME_OBJECT_FOURTH_OPTION ||
				          action == MenuAction.GAME_OBJECT_FIFTH_OPTION ||
				          action == MenuAction.EXAMINE_OBJECT) &&
				         command.getExtraData() != null && !command.getExtraData().isEmpty())
				{
					String[] worldCoordsParts = command.getExtraData().split(",");
					if (worldCoordsParts.length == 3)
					{
						try
						{
							int worldX = Integer.parseInt(worldCoordsParts[0]);
							int worldY = Integer.parseInt(worldCoordsParts[1]);
							int plane = Integer.parseInt(worldCoordsParts[2]);

							// Convert world coordinates to scene coordinates for this slave
							int sceneX = worldX - client.getBaseX();
							int sceneY = worldY - client.getBaseY();

							log.info("Slave {}: world=({},{},{}) -> scene=({},{})", action.name(), worldX, worldY, plane, sceneX, sceneY);

							// Update parameters to use converted scene coordinates
							p0 = sceneX;
							p1 = sceneY;
						}
						catch (NumberFormatException e)
						{
							log.error("Failed to parse world coordinates: {}", command.getExtraData(), e);
						}
					}
				}

				// Log slave position for debugging
				Player localPlayer = client.getLocalPlayer();
				if (localPlayer != null)
				{
					log.info("Slave position: world=({},{},{}), baseX={}, baseY={}",
						localPlayer.getWorldLocation().getX(),
						localPlayer.getWorldLocation().getY(),
						localPlayer.getWorldLocation().getPlane(),
						client.getBaseX(),
						client.getBaseY());
				}

				log.info("Slave executing: action={} ({}), p0={}, p1={}, id={}, itemId={}, option='{}', target='{}'",
					action, action.name(), p0, p1, id, itemId, command.getMenuOption(), command.getMenuTarget());

				client.menuAction(
					p0,
					p1,
					action,
					id,
					itemId,
					command.getMenuOption(),
					command.getMenuTarget()
				);

				log.info("Slave click executed successfully");
			}
			catch (Exception e)
			{
				log.error("Failed to simulate click", e);
			}
		});
	}

	/**
	 * Follow a player by name
	 */
	private void followPlayer(String playerName)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			log.debug("Cannot follow player - not logged in");
			return;
		}

		clientThread.invoke(() ->
		{
			// Find the player by name
			Player targetPlayer = null;
			for (Player player : client.getPlayers())
			{
				if (player != null && playerName.equals(player.getName()))
				{
					targetPlayer = player;
					break;
				}
			}

			if (targetPlayer == null)
			{
				log.warn("Cannot find player to follow: {}", playerName);
				return;
			}

			// Right-click player and select "Follow"
			try
			{
				client.menuAction(
					0,
					0,
					MenuAction.PLAYER_THIRD_OPTION,
					targetPlayer.getId(),
					-1,
					"Follow",
					targetPlayer.getName()
				);

				log.info("Following player: {}", playerName);
			}
			catch (Exception e)
			{
				log.error("Failed to follow player", e);
			}
		});
	}

	/**
	 * Apply camera synchronization on slave client
	 */
	private void applyCameraSync(int yaw, int pitch)
	{
		clientThread.invoke(() ->
		{
			try
			{
				client.setCameraYawTarget(yaw);
				client.setCameraPitchTarget(pitch);
				log.debug("Applied camera sync: yaw={}, pitch={}", yaw, pitch);
			}
			catch (Exception e)
			{
				log.error("Failed to apply camera sync", e);
			}
		});
	}

	/**
	 * Update local player status and broadcast to other clients
	 */
	private void updateAndBroadcastStatus()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		String playerName = localPlayer.getName();
		if (playerName == null)
		{
			return;
		}

		// Get current status
		int health = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHealth = client.getRealSkillLevel(Skill.HITPOINTS);
		int prayer = client.getBoostedSkillLevel(Skill.PRAYER);
		int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
		int worldX = localPlayer.getWorldLocation().getX();
		int worldY = localPlayer.getWorldLocation().getY();
		int plane = localPlayer.getWorldLocation().getPlane();

		// Check for low health/prayer and play notification
		if (config.audioNotifications())
		{
			int healthPercent = maxHealth > 0 ? (health * 100) / maxHealth : 0;
			int prayerPercent = maxPrayer > 0 ? (prayer * 100) / maxPrayer : 0;

			if (healthPercent <= 25 && healthPercent > 0)
			{
				notifier.notify("Low health: " + health + "/" + maxHealth);
			}

			if (prayerPercent <= 25 && prayerPercent > 0)
			{
				notifier.notify("Low prayer: " + prayer + "/" + maxPrayer);
			}
		}

		// Update local status
		ClientStatus localStatus = clientStatuses.computeIfAbsent(playerName, ClientStatus::new);
		localStatus.setHealth(health);
		localStatus.setMaxHealth(maxHealth);
		localStatus.setPrayer(prayer);
		localStatus.setMaxPrayer(maxPrayer);
		localStatus.setWorldX(worldX);
		localStatus.setWorldY(worldY);
		localStatus.setPlane(plane);
		localStatus.setLastUpdate(System.currentTimeMillis());
		localStatus.setLocal(true);

		// Broadcast to others
		String statusString = localStatus.toStatusString();
		MultiboxCommand statusCommand = new MultiboxCommand(MultiboxCommand.CommandType.STATUS_UPDATE, statusString);

		if (config.clientMode() == PrivateServerConfig.ClientMode.MASTER && server != null)
		{
			// Master broadcasts to slaves
			server.broadcast(statusCommand);
		}
		else if (config.clientMode() == PrivateServerConfig.ClientMode.SLAVE && slaveClient != null && slaveClient.isConnected())
		{
			// Slaves need to send status back to master, but our current TCP setup is one-way
			// For now, only master broadcasts. In the future, we could use bidirectional communication
			// or have slaves connect to master as clients that can also send data
		}
	}

	/**
	 * Broadcast equipment update to slaves
	 */
	private void broadcastEquipmentUpdate(ItemContainer container)
	{
		// Build equipment data string: slotId:itemId|slotId:itemId|...
		StringBuilder data = new StringBuilder();
		boolean first = true;

		Item[] items = container.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			Item item = items[slot];
			if (item != null && item.getId() != -1)
			{
				if (!first)
				{
					data.append("|");
				}
				data.append(slot).append(":").append(item.getId());
				first = false;
			}
		}

		MultiboxCommand equipmentCommand = new MultiboxCommand(
			MultiboxCommand.CommandType.EQUIPMENT_SYNC,
			data.toString()
		);

		server.broadcast(equipmentCommand);
		log.debug("Broadcasted equipment update: {}", data);
	}

	/**
	 * Apply equipment synchronization on slave client
	 */
	private void applyEquipmentSync(String equipmentData)
	{
		// Parse equipment data: slotId:itemId|slotId:itemId|...
		String[] parts = equipmentData.split("\\|");

		for (String part : parts)
		{
			String[] slotParts = part.split(":");
			if (slotParts.length == 2)
			{
				try
				{
					int slot = Integer.parseInt(slotParts[0]);
					int itemId = Integer.parseInt(slotParts[1]);

					// Find and equip the item from inventory
					equipItemFromInventory(itemId, slot);
				}
				catch (NumberFormatException e)
				{
					log.warn("Failed to parse equipment slot: {}", part);
				}
			}
		}

		log.debug("Applied equipment sync");
	}

	/**
	 * Equip an item from inventory to a specific equipment slot
	 */
	private void equipItemFromInventory(int targetItemId, int targetSlot)
	{
		clientThread.invoke(() ->
		{
			ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
			if (inventory == null)
			{
				return;
			}

			// Find the item in inventory
			Item[] items = inventory.getItems();
			for (int i = 0; i < items.length; i++)
			{
				Item item = items[i];
				if (item != null && item.getId() == targetItemId)
				{
					// Click to equip
					try
					{
						client.menuAction(
							i,
							9764864, // Inventory widget ID
							MenuAction.WIDGET_SECOND_OPTION,
							0,
							-1,
							"Wear",
							"<col=ff9040>" + client.getItemDefinition(targetItemId).getName()
						);

						log.debug("Equipped item {} from inventory slot {}", targetItemId, i);
						return;
					}
					catch (Exception e)
					{
						log.error("Failed to equip item {}", targetItemId, e);
					}
				}
			}

			log.warn("Could not find item {} in inventory to equip", targetItemId);
		});
	}

	/**
	 * Get client statuses for overlay
	 */
	public Map<String, ClientStatus> getClientStatuses()
	{
		return clientStatuses;
	}

	/**
	 * Get connection status
	 */
	public String getConnectionStatus()
	{
		if (config.clientMode() == PrivateServerConfig.ClientMode.MASTER)
		{
			return server != null ? "Master (" + server.getClientCount() + " slaves)" : "Master (inactive)";
		}
		else
		{
			return slaveClient != null && slaveClient.isConnected() ? "Slave (connected)" : "Slave (disconnected)";
		}
	}

	/**
	 * Get the local IP address for LAN connections
	 */
	private String getLocalIPAddress()
	{
		try
		{
			java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
			String localIP = localHost.getHostAddress();

			// Try to get the actual LAN IP (not 127.0.0.1)
			java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements())
			{
				java.net.NetworkInterface networkInterface = interfaces.nextElement();
				if (networkInterface.isLoopback() || !networkInterface.isUp())
				{
					continue;
				}

				java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
				while (addresses.hasMoreElements())
				{
					java.net.InetAddress addr = addresses.nextElement();
					// Get IPv4 address
					if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress())
					{
						return addr.getHostAddress();
					}
				}
			}

			return localIP;
		}
		catch (Exception e)
		{
			log.error("Failed to get local IP address", e);
			return "Unknown";
		}
	}

	/**
	 * Get master server IP and port for display
	 */
	public String getMasterAddress()
	{
		if (config.clientMode() == PrivateServerConfig.ClientMode.MASTER && server != null)
		{
			return getLocalIPAddress() + ":" + config.serverPort();
		}
		return "";
	}
}
