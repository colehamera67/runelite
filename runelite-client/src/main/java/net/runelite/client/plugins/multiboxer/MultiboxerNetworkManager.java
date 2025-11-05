package net.runelite.client.plugins.multiboxer;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages network communication with the standalone multiboxer server
 */
@Slf4j
@Singleton
public class MultiboxerNetworkManager
{
	@Inject
	private MultiboxerPlugin plugin;

	private final Gson gson = new Gson();
	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private volatile boolean running = false;
	private Socket clientSocket;
	private PrintWriter clientWriter;

	/**
	 * Start the network manager - connect to standalone server
	 */
	public void start(String serverAddress, int serverPort)
	{
		if (running)
		{
			log.warn("Network manager already running");
			return;
		}

		running = true;
		startClient(serverAddress, serverPort);
	}

	/**
	 * Stop the network manager
	 */
	public void stop()
	{
		running = false;

		// Close client socket
		if (clientSocket != null)
		{
			try
			{
				clientSocket.close();
			}
			catch (IOException e)
			{
				log.error("Error closing client socket", e);
			}
			clientSocket = null;
		}

		executorService.shutdown();
	}

	/**
	 * Connect to standalone multiboxer server
	 */
	private void startClient(String serverAddress, int serverPort)
	{
		executorService.submit(() ->
		{
			try
			{
				clientSocket = new Socket(serverAddress, serverPort);
				clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);
				log.info("Connected to multiboxer server at {}:{}", serverAddress, serverPort);

				// Listen for messages from server
				BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				String line;
				while (running && (line = reader.readLine()) != null)
				{
					try
					{
						handleReceivedMessage(line);
					}
					catch (Exception e)
					{
						log.error("Error processing received message", e);
					}
				}
			}
			catch (IOException e)
			{
				log.error("Error connecting to server", e);
			}
		});
	}

	/**
	 * Send an action to the server (which broadcasts to all other clients)
	 */
	public void sendAction(ActionMessage action)
	{
		SyncMessage msg = new SyncMessage();
		msg.setType(SyncMessage.MessageType.ACTION);
		msg.setPayload(gson.toJson(action));
		sendMessage(msg);
	}

	/**
	 * Send a prayer sync message
	 */
	public void sendPrayerSync(PrayerSyncMessage prayerMsg)
	{
		SyncMessage msg = new SyncMessage();
		msg.setType(SyncMessage.MessageType.PRAYER);
		msg.setPayload(gson.toJson(prayerMsg));
		sendMessage(msg);
	}

	/**
	 * Send a special attack sync message
	 */
	public void sendSpecialAttack(SpecialAttackMessage specMsg)
	{
		SyncMessage msg = new SyncMessage();
		msg.setType(SyncMessage.MessageType.SPECIAL_ATTACK);
		msg.setPayload(gson.toJson(specMsg));
		sendMessage(msg);
	}

	/**
	 * Send a combat style sync message
	 */
	public void sendCombatStyle(CombatStyleMessage styleMsg)
	{
		SyncMessage msg = new SyncMessage();
		msg.setType(SyncMessage.MessageType.COMBAT_STYLE);
		msg.setPayload(gson.toJson(styleMsg));
		sendMessage(msg);
	}

	/**
	 * Send a sync message to the server
	 */
	private void sendMessage(SyncMessage msg)
	{
		String json = gson.toJson(msg);

		if (clientWriter != null)
		{
			clientWriter.println(json);
		}
	}

	/**
	 * Handle received message from server
	 */
	private void handleReceivedMessage(String jsonLine)
	{
		try
		{
			SyncMessage msg = gson.fromJson(jsonLine, SyncMessage.class);

			switch (msg.getType())
			{
				case ACTION:
					ActionMessage action = gson.fromJson(msg.getPayload(), ActionMessage.class);
					plugin.handleRemoteAction(action);
					break;

				case PRAYER:
					PrayerSyncMessage prayer = gson.fromJson(msg.getPayload(), PrayerSyncMessage.class);
					plugin.handlePrayerSync(prayer);
					break;

				case SPECIAL_ATTACK:
					SpecialAttackMessage spec = gson.fromJson(msg.getPayload(), SpecialAttackMessage.class);
					plugin.handleSpecialAttackSync(spec);
					break;

				case COMBAT_STYLE:
					CombatStyleMessage style = gson.fromJson(msg.getPayload(), CombatStyleMessage.class);
					plugin.handleCombatStyleSync(style);
					break;

				default:
					log.warn("Unknown message type: {}", msg.getType());
			}
		}
		catch (Exception e)
		{
			log.error("Error parsing sync message", e);
		}
	}
}
