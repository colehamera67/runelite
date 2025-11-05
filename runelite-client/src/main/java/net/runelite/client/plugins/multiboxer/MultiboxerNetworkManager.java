package net.runelite.client.plugins.multiboxer;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages network communication between multiple RuneLite clients
 */
@Slf4j
@Singleton
public class MultiboxerNetworkManager
{
	@Inject
	private MultiboxerPlugin plugin;

	private final Gson gson = new Gson();
	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private ServerSocket serverSocket;
	private Socket clientSocket;
	private PrintWriter clientWriter;
	private final List<ClientConnection> connectedClients = new CopyOnWriteArrayList<>();

	private volatile boolean running = false;
	private boolean isServer = false;

	/**
	 * Start the network manager in either server or client mode
	 */
	public void start(boolean serverMode, String serverAddress, int serverPort)
	{
		if (running)
		{
			log.warn("Network manager already running");
			return;
		}

		running = true;
		isServer = serverMode;

		if (serverMode)
		{
			startServer(serverPort);
		}
		else
		{
			startClient(serverAddress, serverPort);
		}
	}

	/**
	 * Stop the network manager
	 */
	public void stop()
	{
		running = false;

		// Close all client connections
		for (ClientConnection conn : connectedClients)
		{
			conn.close();
		}
		connectedClients.clear();

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

		// Close server socket
		if (serverSocket != null)
		{
			try
			{
				serverSocket.close();
			}
			catch (IOException e)
			{
				log.error("Error closing server socket", e);
			}
			serverSocket = null;
		}

		executorService.shutdown();
	}

	/**
	 * Start in server mode - accept connections from clients
	 */
	private void startServer(int port)
	{
		executorService.submit(() ->
		{
			try
			{
				serverSocket = new ServerSocket(port);
				log.info("Multiboxer server started on port {}", port);

				while (running)
				{
					try
					{
						Socket clientSocket = serverSocket.accept();
						log.info("Client connected: {}", clientSocket.getRemoteSocketAddress());

						ClientConnection conn = new ClientConnection(clientSocket);
						connectedClients.add(conn);
						conn.start();
					}
					catch (IOException e)
					{
						if (running)
						{
							log.error("Error accepting client connection", e);
						}
					}
				}
			}
			catch (IOException e)
			{
				log.error("Error starting server", e);
			}
		});
	}

	/**
	 * Start in client mode - connect to server
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
	 * Send an action to all connected clients (or to server if in client mode)
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
	 * Send a sync message to all clients or server
	 */
	private void sendMessage(SyncMessage msg)
	{
		String json = gson.toJson(msg);

		if (isServer)
		{
			// Broadcast to all connected clients
			for (ClientConnection conn : connectedClients)
			{
				conn.send(json);
			}
		}
		else
		{
			// Send to server
			if (clientWriter != null)
			{
				clientWriter.println(json);
			}
		}
	}

	/**
	 * Handle received message from network
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

	/**
	 * Represents a connection to a client (server mode only)
	 */
	private class ClientConnection
	{
		private final Socket socket;
		private PrintWriter writer;
		private BufferedReader reader;

		public ClientConnection(Socket socket)
		{
			this.socket = socket;
			try
			{
				this.writer = new PrintWriter(socket.getOutputStream(), true);
				this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			}
			catch (IOException e)
			{
				log.error("Error creating client connection", e);
			}
		}

		public void start()
		{
			executorService.submit(() ->
			{
				try
				{
					String line;
					while (running && (line = reader.readLine()) != null)
					{
						// Received message from a client, broadcast to all other clients
						try
						{
							// Broadcast to all clients except the sender
							for (ClientConnection conn : connectedClients)
							{
								if (conn != this)
								{
									conn.send(line);
								}
							}

							// Also process on the server's client (if this is running on a client too)
							handleReceivedMessage(line);
						}
						catch (Exception e)
						{
							log.error("Error processing client message", e);
						}
					}
				}
				catch (IOException e)
				{
					if (running)
					{
						log.error("Error reading from client", e);
					}
				}
				finally
				{
					close();
					connectedClients.remove(this);
				}
			});
		}

		public void send(String message)
		{
			if (writer != null)
			{
				writer.println(message);
			}
		}

		public void close()
		{
			try
			{
				if (socket != null)
				{
					socket.close();
				}
			}
			catch (IOException e)
			{
				log.error("Error closing client connection", e);
			}
		}
	}
}
