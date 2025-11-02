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
	private final int param0;
	private final int param1;
	private final int menuAction;
	private final int identifier;
	private final int itemId;
	private final String menuOption;
	private final String menuTarget;

	// Simple command (no click data)
	public MultiboxCommand(CommandType type)
	{
		this.type = type;
		this.param0 = 0;
		this.param1 = 0;
		this.menuAction = 0;
		this.identifier = 0;
		this.itemId = -1;
		this.menuOption = "";
		this.menuTarget = "";
	}

	// Click command with full parameters
	public MultiboxCommand(CommandType type, int param0, int param1, int menuAction, int identifier, int itemId, String menuOption, String menuTarget)
	{
		this.type = type;
		this.param0 = param0;
		this.param1 = param1;
		this.menuAction = menuAction;
		this.identifier = identifier;
		this.itemId = itemId;
		this.menuOption = menuOption;
		this.menuTarget = menuTarget;
	}

	// Serialize to string for transmission
	public String serialize()
	{
		return type.name() + "|" + param0 + "|" + param1 + "|" + menuAction + "|" + identifier + "|" + itemId + "|" + menuOption + "|" + menuTarget;
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

		if (parts.length >= 8)
		{
			int param0 = Integer.parseInt(parts[1]);
			int param1 = Integer.parseInt(parts[2]);
			int menuAction = Integer.parseInt(parts[3]);
			int identifier = Integer.parseInt(parts[4]);
			int itemId = Integer.parseInt(parts[5]);
			String menuOption = parts[6];
			String menuTarget = parts[7];
			return new MultiboxCommand(type, param0, param1, menuAction, identifier, itemId, menuOption, menuTarget);
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
