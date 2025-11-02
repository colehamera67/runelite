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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TCP server for master client to broadcast commands to slave clients
 */
@Slf4j
public class MultiboxServer
{
	private final int port;
	private ServerSocket serverSocket;
	private Thread acceptThread;
	private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
	private volatile boolean running = false;

	public MultiboxServer(int port)
	{
		this.port = port;
	}

	/**
	 * Start the server
	 */
	public void start()
	{
		if (running)
		{
			return;
		}

		try
		{
			serverSocket = new ServerSocket(port);
			running = true;
			log.info("Multibox server started on port {}", port);

			acceptThread = new Thread(this::acceptClients, "Multibox-Server");
			acceptThread.setDaemon(true);
			acceptThread.start();
		}
		catch (IOException e)
		{
			log.error("Failed to start multibox server", e);
		}
	}

	/**
	 * Stop the server
	 */
	public void stop()
	{
		running = false;

		// Close all client connections
		for (ClientHandler client : clients)
		{
			client.close();
		}
		clients.clear();

		// Close server socket
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

		log.info("Multibox server stopped");
	}

	/**
	 * Accept incoming client connections
	 */
	private void acceptClients()
	{
		while (running)
		{
			try
			{
				Socket clientSocket = serverSocket.accept();
				ClientHandler handler = new ClientHandler(clientSocket);
				clients.add(handler);
				log.info("Slave client connected: {}", clientSocket.getRemoteSocketAddress());
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

	/**
	 * Broadcast a command to all connected slave clients
	 */
	public void broadcast(MultiboxCommand command)
	{
		List<ClientHandler> disconnected = new ArrayList<>();

		for (ClientHandler client : clients)
		{
			if (!client.send(command))
			{
				disconnected.add(client);
			}
		}

		// Remove disconnected clients
		clients.removeAll(disconnected);
	}

	/**
	 * Get the number of connected slave clients
	 */
	public int getClientCount()
	{
		return clients.size();
	}

	/**
	 * Handler for individual slave client connections
	 */
	private static class ClientHandler
	{
		private final Socket socket;
		private final PrintWriter writer;

		public ClientHandler(Socket socket) throws IOException
		{
			this.socket = socket;
			this.writer = new PrintWriter(socket.getOutputStream(), true);
		}

		public boolean send(MultiboxCommand command)
		{
			try
			{
				writer.println(command.serialize());
				return !writer.checkError();
			}
			catch (Exception e)
			{
				log.error("Error sending command to slave", e);
				return false;
			}
		}

		public void close()
		{
			try
			{
				writer.close();
				socket.close();
			}
			catch (IOException e)
			{
				log.error("Error closing client handler", e);
			}
		}
	}
}
