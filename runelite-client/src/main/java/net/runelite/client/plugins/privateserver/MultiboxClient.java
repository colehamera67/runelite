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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * TCP client for slave clients to receive commands from master
 */
@Slf4j
public class MultiboxClient
{
	private final String host;
	private final int port;
	private final Consumer<MultiboxCommand> commandHandler;
	private Socket socket;
	private Thread receiveThread;
	private volatile boolean running = false;

	// Proxy settings
	private boolean useProxy = false;
	private Proxy.Type proxyType = Proxy.Type.SOCKS;
	private String proxyHost;
	private int proxyPort;
	private String proxyUsername;
	private String proxyPassword;

	public MultiboxClient(String host, int port, Consumer<MultiboxCommand> commandHandler)
	{
		this.host = host;
		this.port = port;
		this.commandHandler = commandHandler;
	}

	/**
	 * Configure proxy settings
	 */
	public void setProxySettings(boolean useProxy, String proxyType, String proxyHost, int proxyPort, String proxyUsername, String proxyPassword)
	{
		this.useProxy = useProxy;
		this.proxyType = "HTTP".equalsIgnoreCase(proxyType) ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
		this.proxyUsername = proxyUsername;
		this.proxyPassword = proxyPassword;
	}

	/**
	 * Connect to the master server (with optional proxy support)
	 */
	public boolean connect()
	{
		if (running && socket != null && !socket.isClosed())
		{
			log.info("Already connected to master server");
			return true;
		}

		// Clean up any existing connection first
		if (socket != null)
		{
			disconnect();
		}

		try
		{
			if (useProxy)
			{
				// Set up proxy authentication if credentials are provided
				if (proxyUsername != null && !proxyUsername.isEmpty())
				{
					Authenticator.setDefault(new Authenticator()
					{
						@Override
						protected PasswordAuthentication getPasswordAuthentication()
						{
							if (getRequestorType() == RequestorType.PROXY)
							{
								return new PasswordAuthentication(proxyUsername, proxyPassword.toCharArray());
							}
							return null;
						}
					});
				}

				// Create proxy
				Proxy proxy = new Proxy(proxyType, new InetSocketAddress(proxyHost, proxyPort));

				// Create socket through proxy
				socket = new Socket(proxy);
				socket.connect(new InetSocketAddress(host, port), 5000); // 5 second timeout

				log.info("Connected to master server at {}:{} via {} proxy {}:{}",
					host, port, proxyType, proxyHost, proxyPort);
			}
			else
			{
				// Direct connection without proxy
				socket = new Socket();
				socket.connect(new InetSocketAddress(host, port), 5000); // 5 second timeout
				log.info("Connected to master server at {}:{}", host, port);
			}

			running = true;

			receiveThread = new Thread(this::receiveCommands, "Multibox-Client");
			receiveThread.setDaemon(true);
			receiveThread.start();

			return true;
		}
		catch (IOException e)
		{
			log.error("Failed to connect to master server at {}:{}: {}", host, port, e.getMessage(), e);
			running = false;
			return false;
		}
	}

	/**
	 * Disconnect from the master server
	 */
	public void disconnect()
	{
		if (!running && (socket == null || socket.isClosed()))
		{
			return;
		}

		log.info("Disconnecting from master server...");
		running = false;

		if (socket != null && !socket.isClosed())
		{
			try
			{
				socket.close();
			}
			catch (IOException e)
			{
				log.error("Error closing socket", e);
			}
		}

		// Wait for receive thread to finish
		if (receiveThread != null && receiveThread.isAlive())
		{
			try
			{
				receiveThread.join(1000); // Wait up to 1 second
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}

		socket = null;
		receiveThread = null;

		log.info("Disconnected from master server");
	}

	/**
	 * Receive commands from the master server
	 */
	private void receiveCommands()
	{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream())))
		{
			String line;
			while (running && (line = reader.readLine()) != null)
			{
				try
				{
					MultiboxCommand command = MultiboxCommand.deserialize(line);
					if (command != null)
					{
						log.debug("Received command from master: {}", command.getType());
						commandHandler.accept(command);
					}
					else
					{
						log.warn("Failed to deserialize command: {}", line);
					}
				}
				catch (Exception e)
				{
					log.warn("Error processing command: {}", line, e);
				}
			}
		}
		catch (IOException e)
		{
			if (running)
			{
				log.error("Error receiving commands from master", e);
			}
		}
		finally
		{
			running = false;
		}
	}

	/**
	 * Check if connected to master
	 */
	public boolean isConnected()
	{
		return running && socket != null && socket.isConnected() && !socket.isClosed();
	}
}
