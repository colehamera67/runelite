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

import java.time.Duration;
import java.time.Instant;

/**
 * Tracks multiboxing session statistics
 */
@Data
public class SessionStats
{
	private Instant sessionStart;
	private int commandsSent = 0;
	private int commandsReceived = 0;
	private int clicksSynced = 0;
	private int prayersSynced = 0;
	private int specsSynced = 0;
	private int targetsAssigned = 0;
	private int statusUpdatesSent = 0;
	private int statusUpdatesReceived = 0;

	public SessionStats()
	{
		this.sessionStart = Instant.now();
	}

	/**
	 * Reset all statistics
	 */
	public void reset()
	{
		this.sessionStart = Instant.now();
		this.commandsSent = 0;
		this.commandsReceived = 0;
		this.clicksSynced = 0;
		this.prayersSynced = 0;
		this.specsSynced = 0;
		this.targetsAssigned = 0;
		this.statusUpdatesSent = 0;
		this.statusUpdatesReceived = 0;
	}

	/**
	 * Get session duration
	 */
	public Duration getSessionDuration()
	{
		return Duration.between(sessionStart, Instant.now());
	}

	/**
	 * Get formatted session duration
	 */
	public String getFormattedDuration()
	{
		Duration duration = getSessionDuration();
		long hours = duration.toHours();
		long minutes = duration.toMinutes() % 60;
		long seconds = duration.getSeconds() % 60;

		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

	/**
	 * Increment commands sent counter
	 */
	public void incrementCommandsSent()
	{
		commandsSent++;
	}

	/**
	 * Increment commands received counter
	 */
	public void incrementCommandsReceived()
	{
		commandsReceived++;
	}

	/**
	 * Increment clicks synced counter
	 */
	public void incrementClicksSynced()
	{
		clicksSynced++;
	}

	/**
	 * Increment prayers synced counter
	 */
	public void incrementPrayersSynced()
	{
		prayersSynced++;
	}

	/**
	 * Increment specs synced counter
	 */
	public void incrementSpecsSynced()
	{
		specsSynced++;
	}

	/**
	 * Increment targets assigned counter
	 */
	public void incrementTargetsAssigned()
	{
		targetsAssigned++;
	}

	/**
	 * Increment status updates sent counter
	 */
	public void incrementStatusUpdatesSent()
	{
		statusUpdatesSent++;
	}

	/**
	 * Increment status updates received counter
	 */
	public void incrementStatusUpdatesReceived()
	{
		statusUpdatesReceived++;
	}
}
