package net.runelite.client.plugins.multibox;

import com.google.inject.Provides;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Multibox",
	description = "Synchronize actions across multiple RuneLite clients",
	tags = {"multibox", "sync", "network"},
	enabledByDefault = false
)
public class MultiboxPlugin extends Plugin
{
	static final String CONFIG_GROUP = "multibox";

	@Inject
	private Client client;

	@Inject
	private MultiboxConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MultiboxOverlay overlay;

	private ServerSocket serverSocket;
	private Socket clientSocket;
	private Thread serverThread;
	private Thread clientThread;
	private PrintWriter clientOut;
	private BufferedReader clientIn;
	private final List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
	private volatile boolean running = false;
	private String localIpAddress = "Unknown";

	@Provides
	MultiboxConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MultiboxConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Multibox plugin started");
		overlayManager.add(overlay);
		localIpAddress = getLocalIp();
		startNetworking();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Multibox plugin stopped");
		overlayManager.remove(overlay);
		stopNetworking();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP))
		{
			return;
		}

		if (event.getKey().equals("mode") || event.getKey().equals("masterIp") || event.getKey().equals("port"))
		{
			stopNetworking();
			startNetworking();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (config.mode() != MultiboxMode.MASTER)
		{
			return;
		}

		// Check if this is a walk action
		MenuAction menuAction = event.getMenuAction();
		if (menuAction == MenuAction.WALK)
		{
			// Get parameters from the event
			int param0 = event.getParam0();
			int param1 = event.getParam1();

			log.info("MASTER: Walk clicked - raw params: p0={}, p1={}", param0, param1);

			// Convert to WorldPoint using the pattern from HerbiboarPlugin and others
			WorldPoint worldPoint = WorldPoint.fromScene(client, param0, param1, client.getPlane());

			if (worldPoint != null)
			{
				log.info("MASTER: Converted to world coords: ({}, {}, {})",
					worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane());

				// Broadcast world coordinates
				broadcastWalkCommand(worldPoint.getX(), worldPoint.getY(), worldPoint.getPlane());
			}
			else
			{
				log.warn("MASTER: Failed to convert to world point");
			}
		}
	}

	private void startNetworking()
	{
		running = true;

		if (config.mode() == MultiboxMode.MASTER)
		{
			startServer();
		}
		else
		{
			startClient();
		}
	}

	private void stopNetworking()
	{
		running = false;

		// Close all client connections
		for (ClientHandler handler : connectedClients)
		{
			handler.close();
		}
		connectedClients.clear();

		// Close server
		if (serverSocket != null && !serverSocket.isClosed())
		{
			try
			{
				serverSocket.close();
			}
			catch (IOException e)
			{
				log.error("Error closing server socket", e);
			}
		}

		// Close client connection
		if (clientSocket != null && !clientSocket.isClosed())
		{
			try
			{
				if (clientOut != null)
				{
					clientOut.close();
				}
				if (clientIn != null)
				{
					clientIn.close();
				}
				clientSocket.close();
			}
			catch (IOException e)
			{
				log.error("Error closing client socket", e);
			}
		}

		// Interrupt threads
		if (serverThread != null && serverThread.isAlive())
		{
			serverThread.interrupt();
		}
		if (clientThread != null && clientThread.isAlive())
		{
			clientThread.interrupt();
		}
	}

	private void startServer()
	{
		serverThread = new Thread(() -> {
			try
			{
				serverSocket = new ServerSocket(config.port());
				log.info("Multibox server started on port {}", config.port());

				while (running && !serverSocket.isClosed())
				{
					try
					{
						Socket clientSocket = serverSocket.accept();
						log.info("Client connected: {}", clientSocket.getRemoteSocketAddress());

						ClientHandler handler = new ClientHandler(clientSocket);
						connectedClients.add(handler);
						new Thread(handler).start();
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
		serverThread.setName("Multibox-Server");
		serverThread.start();
	}

	private void startClient()
	{
		clientThread = new Thread(() -> {
			while (running)
			{
				try
				{
					log.info("Attempting to connect to master at {}:{}", config.masterIp(), config.port());
					clientSocket = new Socket(config.masterIp(), config.port());
					clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
					clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

					log.info("Connected to master");

					String message;
					while (running && (message = clientIn.readLine()) != null)
					{
						handleCommand(message);
					}
				}
				catch (IOException e)
				{
					if (running)
					{
						log.error("Connection error: {}. Retrying in 5 seconds...", e.getMessage());
						try
						{
							Thread.sleep(5000);
						}
						catch (InterruptedException ie)
						{
							break;
						}
					}
				}
				finally
				{
					try
					{
						if (clientSocket != null && !clientSocket.isClosed())
						{
							clientSocket.close();
						}
					}
					catch (IOException e)
					{
						log.error("Error closing client socket", e);
					}
				}
			}
		});
		clientThread.setName("Multibox-Client");
		clientThread.start();
	}

	private void handleCommand(String command)
	{
		log.debug("Received command: {}", command);

		String[] parts = command.split(",");
		if (parts.length < 1)
		{
			return;
		}

		String action = parts[0];

		if ("WALK".equals(action) && parts.length == 4)
		{
			try
			{
				int worldX = Integer.parseInt(parts[1]);
				int worldY = Integer.parseInt(parts[2]);
				int plane = Integer.parseInt(parts[3]);

				log.info("SLAVE RECEIVED: world coords ({}, {}, {})", worldX, worldY, plane);

				// Convert world coordinates to scene coordinates
				int baseX = client.getTopLevelWorldView().getBaseX();
				int baseY = client.getTopLevelWorldView().getBaseY();
				int sceneX = worldX - baseX;
				int sceneY = worldY - baseY;

				log.info("SLAVE: base=({}, {}), scene=({}, {})", baseX, baseY, sceneX, sceneY);

				// Use scene coordinates in menuAction
				client.menuAction(sceneX, sceneY, MenuAction.WALK, 0, -1, "Walk here", "");

				log.info("SLAVE EXECUTED: menuAction({}, {}, WALK, 0, -1)", sceneX, sceneY);
			}
			catch (NumberFormatException e)
			{
				log.error("Invalid walk command format", e);
			}
		}
	}

	private void broadcastWalkCommand(int worldX, int worldY, int plane)
	{
		String command = String.format("WALK,%d,%d,%d", worldX, worldY, plane);
		log.info("MASTER BROADCASTING: {}", command);

		List<ClientHandler> disconnected = new ArrayList<>();
		for (ClientHandler handler : connectedClients)
		{
			if (!handler.send(command))
			{
				disconnected.add(handler);
			}
		}

		// Remove disconnected clients
		connectedClients.removeAll(disconnected);
	}

	private String getLocalIp()
	{
		try
		{
			InetAddress localHost = InetAddress.getLocalHost();
			return localHost.getHostAddress();
		}
		catch (UnknownHostException e)
		{
			log.error("Unable to get local IP address", e);
			return "Unknown";
		}
	}

	public boolean isConnected()
	{
		if (config.mode() == MultiboxMode.MASTER)
		{
			return serverSocket != null && !serverSocket.isClosed();
		}
		else
		{
			return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
		}
	}

	public String getLocalIpAddress()
	{
		return localIpAddress;
	}

	public int getConnectedSlaveCount()
	{
		return connectedClients.size();
	}

	private class ClientHandler implements Runnable
	{
		private final Socket socket;
		private PrintWriter out;
		private BufferedReader in;

		public ClientHandler(Socket socket)
		{
			this.socket = socket;
		}

		@Override
		public void run()
		{
			try
			{
				out = new PrintWriter(socket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				// Keep connection alive
				while (running && !socket.isClosed())
				{
					Thread.sleep(1000);
				}
			}
			catch (IOException | InterruptedException e)
			{
				log.debug("Client disconnected: {}", socket.getRemoteSocketAddress());
			}
			finally
			{
				close();
			}
		}

		public boolean send(String message)
		{
			if (out != null && !socket.isClosed())
			{
				out.println(message);
				return !out.checkError();
			}
			return false;
		}

		public void close()
		{
			try
			{
				if (out != null)
				{
					out.close();
				}
				if (in != null)
				{
					in.close();
				}
				if (socket != null && !socket.isClosed())
				{
					socket.close();
				}
			}
			catch (IOException e)
			{
				log.error("Error closing client handler", e);
			}
		}
	}
}
