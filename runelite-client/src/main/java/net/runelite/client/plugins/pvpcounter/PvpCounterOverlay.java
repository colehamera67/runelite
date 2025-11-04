/*
 * Copyright (c) 2025, YourName <https://github.com/yourname>
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
package net.runelite.client.plugins.pvpcounter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Prayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class PvpCounterOverlay extends OverlayPanel
{
	private final PvpCounterPlugin plugin;
	private final PvpCounterConfig config;

	@Inject
	private PvpCounterOverlay(PvpCounterPlugin plugin, PvpCounterConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_RIGHT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		AttackType detectedAttack = plugin.getDetectedAttackType();
		CounterSetup currentSetup = plugin.getCurrentSetup();

		if (detectedAttack == null && currentSetup == null)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("PvP Counter")
			.color(Color.CYAN)
			.build());

		// Show detected attack type
		if (config.showAttackType() && detectedAttack != null && detectedAttack != AttackType.UNKNOWN)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Opponent:")
				.right(detectedAttack.getName())
				.rightColor(detectedAttack.getColor())
				.build());
		}

		// Show recommended prayer based on detected attack
		if (config.showRecommendedPrayer() && detectedAttack != null && detectedAttack.getRecommendedPrayer() != null)
		{
			Prayer prayer = detectedAttack.getRecommendedPrayer();
			Color prayerColor = config.highlightPrayer() ? Color.YELLOW : Color.WHITE;

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Prayer:")
				.right(getPrayerName(prayer))
				.rightColor(prayerColor)
				.build());
		}

		// Show current counter setup
		if (currentSetup != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Setup:")
				.right(currentSetup.getName())
				.rightColor(Color.ORANGE)
				.build());

			if (config.showRecommendedGear())
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Use:")
					.right(currentSetup.getRecommendedGear())
					.rightColor(Color.GREEN)
					.build());
			}

			if (config.showRecommendedPrayer())
			{
				Prayer prayer = currentSetup.getRecommendedPrayer();
				Color prayerColor = config.highlightPrayer() ? Color.YELLOW : Color.WHITE;

				panelComponent.getChildren().add(LineComponent.builder()
					.left("Protect:")
					.right(getPrayerName(prayer))
					.rightColor(prayerColor)
					.build());
			}
		}

		return super.render(graphics);
	}

	private String getPrayerName(Prayer prayer)
	{
		switch (prayer)
		{
			case PROTECT_FROM_MELEE:
				return "Melee";
			case PROTECT_FROM_MISSILES:
				return "Ranged";
			case PROTECT_FROM_MAGIC:
				return "Magic";
			default:
				return prayer.name();
		}
	}
}
