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

	public MultiboxClient(String host, int port, Consumer<MultiboxCommand> commandHandler)
	{
		this.host = host;
		this.port = port;
		this.commandHandler = commandHandler;
	}

	/**
	 * Connect to the master server
	 */
	public boolean connect()
	{
		if (running)
		{
			return true;
		}

		try
		{
			socket = new Socket(host, port);
			running = true;
			log.info("Connected to master server at {}:{}", host, port);

			receiveThread = new Thread(this::receiveCommands, "Multibox-Client");
			receiveThread.setDaemon(true);
			receiveThread.start();

			return true;
		}
		catch (IOException e)
		{
			log.error("Failed to connect to master server at {}:{}", host, port, e);
			return false;
		}
	}

	/**
	 * Disconnect from the master server
	 */
	public void disconnect()
	{
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
					MultiboxCommand command = MultiboxCommand.valueOf(line);
					log.debug("Received command from master: {}", command);
					commandHandler.accept(command);
				}
				catch (IllegalArgumentException e)
				{
					log.warn("Unknown command received: {}", line);
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
