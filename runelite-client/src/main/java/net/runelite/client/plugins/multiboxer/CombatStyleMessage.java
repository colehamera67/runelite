package net.runelite.client.plugins.multiboxer;

import lombok.Data;
import java.io.Serializable;

/**
 * Message for syncing combat style changes between clients
 */
@Data
public class CombatStyleMessage implements Serializable
{
	private static final long serialVersionUID = 1L;

	private int attackStyle; // 0=Accurate, 1=Aggressive, 2=Controlled, 3=Defensive
	private int weaponType; // Weapon category to ensure compatibility
}
