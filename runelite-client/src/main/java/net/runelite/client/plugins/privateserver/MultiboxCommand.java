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

import lombok.Getter;

/**
 * Commands that can be sent between multiboxing clients
 */
@Getter
public class MultiboxCommand
{
	private final CommandType type;
	private final int x;
	private final int y;
	private final int menuAction;
	private final String menuOption;
	private final String menuTarget;

	// Simple command (no click data)
	public MultiboxCommand(CommandType type)
	{
		this.type = type;
		this.x = 0;
		this.y = 0;
		this.menuAction = 0;
		this.menuOption = "";
		this.menuTarget = "";
	}

	// Click command with coordinates
	public MultiboxCommand(CommandType type, int x, int y, int menuAction, String menuOption, String menuTarget)
	{
		this.type = type;
		this.x = x;
		this.y = y;
		this.menuAction = menuAction;
		this.menuOption = menuOption;
		this.menuTarget = menuTarget;
	}

	// Serialize to string for transmission
	public String serialize()
	{
		return type.name() + "|" + x + "|" + y + "|" + menuAction + "|" + menuOption + "|" + menuTarget;
	}

	// Deserialize from string
	public static MultiboxCommand deserialize(String data)
	{
		String[] parts = data.split("\\|", -1);
		if (parts.length < 1)
		{
			return null;
		}

		CommandType type = CommandType.valueOf(parts[0]);

		if (parts.length >= 6)
		{
			int x = Integer.parseInt(parts[1]);
			int y = Integer.parseInt(parts[2]);
			int menuAction = Integer.parseInt(parts[3]);
			String menuOption = parts[4];
			String menuTarget = parts[5];
			return new MultiboxCommand(type, x, y, menuAction, menuOption, menuTarget);
		}
		else
		{
			return new MultiboxCommand(type);
		}
	}

	public enum CommandType
	{
		ACTIVATE_PRAYER,
		ACTIVATE_SPEC,
		FOLLOW_LEADER,
		CLICK_SYNC,
		PING,
		DISCONNECT
	}
}
