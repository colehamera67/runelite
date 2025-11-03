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

import lombok.Data;

/**
 * Holds status information for a connected multibox client
 */
@Data
public class ClientStatus
{
	private final String playerName;
	private int health;
	private int maxHealth;
	private int prayer;
	private int maxPrayer;
	private int worldX;
	private int worldY;
	private int plane;
	private long lastUpdate;
	private boolean isLocal;

	public ClientStatus(String playerName)
	{
		this.playerName = playerName;
		this.lastUpdate = System.currentTimeMillis();
		this.isLocal = false;
	}

	/**
	 * Update status from extraData string format: "name|hp|maxHp|prayer|maxPrayer|x|y|plane"
	 */
	public void updateFromString(String extraData)
	{
		try
		{
			String[] parts = extraData.split("\\|");
			if (parts.length >= 8)
			{
				this.health = Integer.parseInt(parts[1]);
				this.maxHealth = Integer.parseInt(parts[2]);
				this.prayer = Integer.parseInt(parts[3]);
				this.maxPrayer = Integer.parseInt(parts[4]);
				this.worldX = Integer.parseInt(parts[5]);
				this.worldY = Integer.parseInt(parts[6]);
				this.plane = Integer.parseInt(parts[7]);
				this.lastUpdate = System.currentTimeMillis();
			}
		}
		catch (Exception e)
		{
			// Invalid format, ignore
		}
	}

	/**
	 * Convert status to string for transmission: "name|hp|maxHp|prayer|maxPrayer|x|y|plane"
	 */
	public String toStatusString()
	{
		return String.format("%s|%d|%d|%d|%d|%d|%d|%d",
			playerName, health, maxHealth, prayer, maxPrayer, worldX, worldY, plane);
	}

	/**
	 * Check if this status is stale (no update in 5 seconds)
	 */
	public boolean isStale()
	{
		return System.currentTimeMillis() - lastUpdate > 5000;
	}

	/**
	 * Get health percentage
	 */
	public int getHealthPercent()
	{
		if (maxHealth == 0) return 0;
		return (health * 100) / maxHealth;
	}

	/**
	 * Get prayer percentage
	 */
	public int getPrayerPercent()
	{
		if (maxPrayer == 0) return 0;
		return (prayer * 100) / maxPrayer;
	}
}
