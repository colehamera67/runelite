package net.runelite.client.plugins.multiboxer;

import lombok.Data;
import java.io.Serializable;

/**
 * Message for syncing special attack usage between clients
 */
@Data
public class SpecialAttackMessage implements Serializable
{
	private static final long serialVersionUID = 1L;

	private boolean toggleSpecial; // true = enable special attack mode, false = use special attack
	private int minEnergyRequired; // Minimum energy slaves need to execute
}
