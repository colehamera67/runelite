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

import com.google.inject.Provides;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "PvP Counter",
	description = "Detects opponent attack styles and helps with counter-setup switching",
	tags = {"pvp", "combat", "pking", "prayer", "overlay"}
)
public class PvpCounterPlugin extends Plugin
{
	// Common attack animation IDs
	// Melee animations
	private static final int[] MELEE_ANIMATIONS = {
		422, 423, 386, 390, 391, 400, 401, 405, 407, 408, 419, 428, 429, 440,
		1658, 1665, 1667, 2062, 2078, 2323, 7515, 8145, 8288, 8290
	};

	// Ranged animations
	private static final int[] RANGED_ANIMATIONS = {
		426, 427, 929, 1074, 2075, 4230, 7617, 7618, 8194, 8292
	};

	// Magic animations
	private static final int[] MAGIC_ANIMATIONS = {
		710, 711, 716, 724, 727, 728, 729, 1162, 1166, 1167, 1978, 7855, 8939
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PvpCounterConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PvpCounterOverlay overlay;

	@Inject
	private KeyManager keyManager;

	@Getter
	private AttackType detectedAttackType = AttackType.UNKNOWN;

	@Getter
	private CounterSetup currentSetup;

	private final Map<String, AttackType> playerAttacks = new HashMap<>();
	private final Map<String, Instant> lastAttackTime = new HashMap<>();
	private Player lastOpponent;

	private final HotkeyListener meleeCounterHotkeyListener = new HotkeyListener(() -> config.meleeCounterHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			switchToSetup(CounterSetup.MELEE_COUNTER);
		}
	};

	private final HotkeyListener rangedCounterHotkeyListener = new HotkeyListener(() -> config.rangedCounterHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			switchToSetup(CounterSetup.RANGED_COUNTER);
		}
	};

	private final HotkeyListener magicCounterHotkeyListener = new HotkeyListener(() -> config.magicCounterHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			switchToSetup(CounterSetup.MAGIC_COUNTER);
		}
	};

	@Provides
	PvpCounterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PvpCounterConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		keyManager.registerKeyListener(meleeCounterHotkeyListener);
		keyManager.registerKeyListener(rangedCounterHotkeyListener);
		keyManager.registerKeyListener(magicCounterHotkeyListener);
		log.info("PvP Counter plugin started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(meleeCounterHotkeyListener);
		keyManager.unregisterKeyListener(rangedCounterHotkeyListener);
		keyManager.unregisterKeyListener(magicCounterHotkeyListener);
		playerAttacks.clear();
		lastAttackTime.clear();
		detectedAttackType = AttackType.UNKNOWN;
		currentSetup = null;
		lastOpponent = null;
		log.info("PvP Counter plugin stopped");
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();

		// Only track other players (not local player or NPCs)
		if (!(actor instanceof Player))
		{
			return;
		}

		Player player = (Player) actor;
		Player localPlayer = client.getLocalPlayer();

		if (localPlayer == null || player == localPlayer)
		{
			return;
		}

		// Only track the player we're fighting with (mutual combat)
		if (!isDirectOpponent(player, localPlayer))
		{
			return;
		}

		int animationId = player.getAnimation();
		AttackType attackType = getAttackTypeFromAnimation(animationId);

		if (attackType != AttackType.UNKNOWN)
		{
			String playerName = player.getName();
			playerAttacks.put(playerName, attackType);
			lastAttackTime.put(playerName, Instant.now());
			lastOpponent = player;
			detectedAttackType = attackType;

			// Auto-suggest counter setup if enabled
			if (config.enableAutoSwitch())
			{
				suggestCounterSetup(attackType);
			}

			log.debug("Detected {} attack from opponent: {}", attackType.getName(), playerName);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Clean up old attack data (older than 10 seconds)
		Instant cutoff = Instant.now().minus(10, ChronoUnit.SECONDS);
		lastAttackTime.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
		playerAttacks.entrySet().removeIf(entry -> !lastAttackTime.containsKey(entry.getKey()));

		// Reset detected attack if no recent attacks
		if (lastOpponent != null)
		{
			String opponentName = lastOpponent.getName();
			if (!lastAttackTime.containsKey(opponentName))
			{
				detectedAttackType = AttackType.UNKNOWN;
				lastOpponent = null;
			}
		}
	}

	private void switchToSetup(CounterSetup setup)
	{
		currentSetup = setup;
		log.info("Switched to {} - Use {} and protect from {}",
			setup.getName(),
			setup.getRecommendedGear(),
			getPrayerName(setup.getRecommendedPrayer()));
	}

	private void suggestCounterSetup(AttackType opponentAttack)
	{
		// Don't auto-switch, just update recommendation
		// The counter setup is what YOU use to counter THEIR attack
		switch (opponentAttack)
		{
			case MELEE:
				// If they attack with melee, counter with ranged/magic
				if (currentSetup != CounterSetup.RANGED_COUNTER && currentSetup != CounterSetup.MAGIC_COUNTER)
				{
					log.debug("Opponent using melee - consider ranged or magic counter");
				}
				break;
			case RANGED:
				// If they attack with ranged, counter with melee
				if (currentSetup != CounterSetup.MELEE_COUNTER)
				{
					log.debug("Opponent using ranged - consider melee counter");
				}
				break;
			case MAGIC:
				// If they attack with magic, counter with melee or ranged
				if (currentSetup != CounterSetup.MELEE_COUNTER && currentSetup != CounterSetup.RANGED_COUNTER)
				{
					log.debug("Opponent using magic - consider melee or ranged counter");
				}
				break;
		}
	}

	private AttackType getAttackTypeFromAnimation(int animationId)
	{
		for (int id : MELEE_ANIMATIONS)
		{
			if (id == animationId)
			{
				return AttackType.MELEE;
			}
		}

		for (int id : RANGED_ANIMATIONS)
		{
			if (id == animationId)
			{
				return AttackType.RANGED;
			}
		}

		for (int id : MAGIC_ANIMATIONS)
		{
			if (id == animationId)
			{
				return AttackType.MAGIC;
			}
		}

		return AttackType.UNKNOWN;
	}

	/**
	 * Checks if the player is our direct opponent in combat
	 * Returns true if we're attacking them OR they're attacking us
	 */
	private boolean isDirectOpponent(Player player, Player localPlayer)
	{
		if (player == null || localPlayer == null)
		{
			return false;
		}

		Actor localInteracting = localPlayer.getInteracting();
		Actor playerInteracting = player.getInteracting();

		// Check if we're attacking them
		boolean weAreAttackingThem = localInteracting != null && localInteracting.equals(player);

		// Check if they're attacking us
		boolean theyAreAttackingUs = playerInteracting != null && playerInteracting.equals(localPlayer);

		// Return true if either condition is met
		return weAreAttackingThem || theyAreAttackingUs;
	}

	private String getPrayerName(net.runelite.api.Prayer prayer)
	{
		if (prayer == null)
		{
			return "None";
		}

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
