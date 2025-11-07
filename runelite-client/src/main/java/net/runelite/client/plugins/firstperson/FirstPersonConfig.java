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
		keyName = "cameraHeight",
		name = "Camera Height",
		description = "Height offset for the first-person camera (higher = camera is higher up)"
	)
	@Range(min = -500, max = 500)
	default int cameraHeight()
	{
		return 150;
	}

	@ConfigItem(
		keyName = "pitchOffset",
		name = "Pitch Offset",
		description = "Vertical angle offset (positive = look up, negative = look down)"
	)
	@Range(min = -512, max = 512)
	default int pitchOffset()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "smoothCamera",
		name = "Smooth Camera",
		description = "Smoothly transition camera movements (disable for instant camera)"
	)
	default boolean smoothCamera()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideLocalPlayer",
		name = "Hide Local Player",
		description = "Hide your own player model in first-person view"
	)
	default boolean hideLocalPlayer()
	{
		return true;
	}
}
