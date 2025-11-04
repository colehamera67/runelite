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

		MenuAction menuAction = event.getMenuAction();

		// Skip walk actions - we don't sync walking
		if (menuAction == MenuAction.WALK)
		{
			return;
		}

		// Sync all other actions (NPCs, objects, items, etc.)
		int param0 = event.getParam0();
		int param1 = event.getParam1();
		int id = event.getId();
		String option = event.getMenuOption();
		String target = event.getMenuTarget();

		log.info("MASTER: Action '{}' on '{}' - type={}, p0={}, p1={}, id={}",
			option, target, menuAction, param0, param1, id);

		// Broadcast the action to all slaves
		broadcastAction(menuAction, param0, param1, id, option, target);
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

		String[] parts = command.split("\\|");
		if (parts.length < 1)
		{
			return;
		}

		String commandType = parts[0];

		if ("ACTION".equals(commandType) && parts.length == 7)
		{
			try
			{
				int menuActionId = Integer.parseInt(parts[1]);
				int param0 = Integer.parseInt(parts[2]);
				int param1 = Integer.parseInt(parts[3]);
				int id = Integer.parseInt(parts[4]);
				String option = parts[5];
				String target = parts[6];

				MenuAction menuAction = MenuAction.of(menuActionId);

				log.info("SLAVE RECEIVED: '{}' on '{}' - type={}, p0={}, p1={}, id={}",
					option, target, menuAction, param0, param1, id);

				// Execute the exact same action on the slave
				client.menuAction(param0, param1, menuAction, id, -1, option, target);

				log.info("SLAVE EXECUTED: menuAction({}, {}, {}, {}, -1, '{}', '{}')",
					param0, param1, menuAction, id, option, target);
			}
			catch (NumberFormatException e)
			{
				log.error("Invalid action command format", e);
			}
		}
	}

	private void broadcastAction(MenuAction menuAction, int param0, int param1, int id, String option, String target)
	{
		// Encode the action: ACTION|menuActionId|param0|param1|id|option|target
		// Using | as delimiter since commas might appear in option/target text
		String command = String.format("ACTION|%d|%d|%d|%d|%s|%s",
			menuAction.getId(), param0, param1, id,
			option.replace("|", ""), target.replace("|", ""));

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
