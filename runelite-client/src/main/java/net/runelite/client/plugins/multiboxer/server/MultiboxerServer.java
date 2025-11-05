package net.runelite.client.plugins.multiboxer.server;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone server for RuneLite Multiboxer plugin
 * Run this on your local PC to coordinate multiple RuneLite clients
 */
@Slf4j
public class MultiboxerServer
{
	private static final int DEFAULT_PORT = 43594;
	private final Gson gson = new Gson();
	private final ExecutorService executorService = Executors.newCachedThreadPool();
	private final List<ClientConnection> connectedClients = new CopyOnWriteArrayList<>();
	private final AtomicInteger clientIdCounter = new AtomicInteger(0);

	private ServerSocket serverSocket;
	private volatile boolean running = false;
	private int port;

	public MultiboxerServer(int port)
	{
		this.port = port;
	}

	/**
	 * Start the server
	 */
	public void start() throws IOException
	{
		if (running)
		{
			log.warn("Server already running");
			return;
		}

		running = true;
		serverSocket = new ServerSocket(port);

		System.out.println("╔════════════════════════════════════════════════════════════╗");
		System.out.println("║          RuneLite Multiboxer Server v1.0                  ║");
		System.out.println("╚════════════════════════════════════════════════════════════╝");
		System.out.println();
		System.out.println("✓ Server started successfully on port " + port);
		System.out.println("✓ Waiting for clients to connect...");
		System.out.println();
		System.out.println("Instructions:");
		System.out.println("  1. Start your RuneLite clients");
		System.out.println("  2. Enable the Multiboxer plugin on each client");
		System.out.println("  3. Set Server Mode to OFF (client mode)");
		System.out.println("  4. Set Server Address to 'localhost' (or this PC's IP)");
		System.out.println("  5. Set Server Port to " + port);
		System.out.println();
		System.out.println("Press Ctrl+C to stop the server");
		System.out.println("════════════════════════════════════════════════════════════");
		System.out.println();

		// Accept connections in background thread
		executorService.submit(() ->
		{
			while (running)
			{
				try
				{
					Socket clientSocket = serverSocket.accept();
					int clientId = clientIdCounter.incrementAndGet();
					String clientAddress = clientSocket.getRemoteSocketAddress().toString();

					System.out.println("[" + getTimestamp() + "] ✓ Client #" + clientId + " connected from " + clientAddress);

					ClientConnection conn = new ClientConnection(clientSocket, clientId);
					connectedClients.add(conn);
					conn.start();

					System.out.println("[" + getTimestamp() + "] ℹ Total clients connected: " + connectedClients.size());
				}
				catch (IOException e)
				{
					if (running)
					{
						System.err.println("[" + getTimestamp() + "] ✗ Error accepting client connection: " + e.getMessage());
					}
				}
			}
		});
	}

	/**
	 * Stop the server
	 */
	public void stop()
	{
		running = false;

		System.out.println();
		System.out.println("[" + getTimestamp() + "] Shutting down server...");

		// Close all client connections
		for (ClientConnection conn : connectedClients)
		{
			conn.close();
		}
		connectedClients.clear();

		// Close server socket
		if (serverSocket != null)
		{
			try
			{
				serverSocket.close();
			}
			catch (IOException e)
			{
				System.err.println("Error closing server socket: " + e.getMessage());
			}
		}

		executorService.shutdown();
		System.out.println("[" + getTimestamp() + "] ✓ Server stopped");
	}

	/**
	 * Get current timestamp for logging
	 */
	private String getTimestamp()
	{
		return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
	}

	/**
	 * Broadcast a message to all connected clients except the sender
	 */
	private void broadcastMessage(String message, ClientConnection sender)
	{
		int recipientCount = 0;
		for (ClientConnection client : connectedClients)
		{
			if (client != sender)
			{
				client.send(message);
				recipientCount++;
			}
		}

		if (recipientCount > 0)
		{
			System.out.println("[" + getTimestamp() + "] → Broadcasted message from Client #" + sender.getId() + " to " + recipientCount + " client(s)");
		}
	}

	/**
	 * Represents a connected client
	 */
	private class ClientConnection
	{
		private final Socket socket;
		private final int clientId;
		private PrintWriter writer;
		private BufferedReader reader;
		private long messagesReceived = 0;
		private long messagesSent = 0;

		public ClientConnection(Socket socket, int clientId)
		{
			this.socket = socket;
			this.clientId = clientId;
			try
			{
				this.writer = new PrintWriter(socket.getOutputStream(), true);
				this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			}
			catch (IOException e)
			{
				System.err.println("[" + getTimestamp() + "] ✗ Error creating client #" + clientId + " connection: " + e.getMessage());
			}
		}

		public int getId()
		{
			return clientId;
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
						messagesReceived++;

						// Log every 10th message to avoid spam
						if (messagesReceived % 10 == 0)
						{
							System.out.println("[" + getTimestamp() + "] ℹ Client #" + clientId + " stats: " + messagesReceived + " received, " + messagesSent + " sent");
						}

						// Broadcast to all other clients
						broadcastMessage(line, this);
					}
				}
				catch (IOException e)
				{
					if (running)
					{
						System.err.println("[" + getTimestamp() + "] ✗ Client #" + clientId + " connection error: " + e.getMessage());
					}
				}
				finally
				{
					close();
					connectedClients.remove(this);
					System.out.println("[" + getTimestamp() + "] ✗ Client #" + clientId + " disconnected");
					System.out.println("[" + getTimestamp() + "] ℹ Total clients connected: " + connectedClients.size());
				}
			});
		}

		public void send(String message)
		{
			if (writer != null)
			{
				writer.println(message);
				messagesSent++;
			}
		}

		public void close()
		{
			try
			{
				if (socket != null && !socket.isClosed())
				{
					socket.close();
				}
			}
			catch (IOException e)
			{
				System.err.println("[" + getTimestamp() + "] Error closing client #" + clientId + " socket: " + e.getMessage());
			}
		}
	}

	/**
	 * Main entry point
	 */
	public static void main(String[] args)
	{
		int port = DEFAULT_PORT;

		// Parse command line arguments
		if (args.length > 0)
		{
			try
			{
				port = Integer.parseInt(args[0]);
			}
			catch (NumberFormatException e)
			{
				System.err.println("Invalid port number: " + args[0]);
				System.err.println("Usage: java MultiboxerServer [port]");
				System.err.println("Example: java MultiboxerServer 43594");
				System.exit(1);
			}
		}

		final MultiboxerServer server = new MultiboxerServer(port);

		// Add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() ->
		{
			server.stop();
		}));

		try
		{
			server.start();

			// Keep server running
			Thread.currentThread().join();
		}
		catch (IOException e)
		{
			System.err.println("Failed to start server: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		catch (InterruptedException e)
		{
			System.out.println("Server interrupted");
		}
	}
}
