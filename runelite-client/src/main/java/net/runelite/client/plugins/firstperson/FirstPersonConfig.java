/*
 * Copyright (c) 2025, RuneLite
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
package net.runelite.client.plugins.firstperson;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("firstperson")
public interface FirstPersonConfig extends Config
{
	@ConfigItem(
		keyName = "cameraDistance",
		name = "Camera Distance",
		description = "How far the camera is from the player (lower = closer, more first-person)"
	)
	@Range(min = 0, max = 1000)
	default int cameraDistance()
	{
		return 200;
	}

	@ConfigItem(
		keyName = "cameraHeight",
		name = "Camera Height",
		description = "Height of the camera above the player"
	)
	@Range(min = 0, max = 500)
	default int cameraHeight()
	{
		return 150;
	}

	@ConfigItem(
		keyName = "hideLocalPlayer",
		name = "Hide Local Player",
		description = "Hide your own player model for a more immersive view"
	)
	default boolean hideLocalPlayer()
	{
		return false;
	}

	@ConfigItem(
		keyName = "wasdMovement",
		name = "WASD Movement",
		description = "Enable WASD keys for movement (W=Forward, A=Left, S=Back, D=Right relative to camera)"
	)
	default boolean wasdMovement()
	{
		return true;
	}
}
