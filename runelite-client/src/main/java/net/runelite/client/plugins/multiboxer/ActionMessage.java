package net.runelite.client.plugins.multiboxer;

import lombok.Data;
import net.runelite.api.MenuAction;

import java.io.Serializable;

/**
 * Represents a synchronized action to be sent between clients
 */
@Data
public class ActionMessage implements Serializable
{
	private static final long serialVersionUID = 1L;

	private MenuAction menuAction;
	private String option;
	private String target;
	private int identifier;
	private int param0;
	private int param1;
	private int itemId;

	/**
	 * For inventory actions, this contains the resolved item ID
	 * (since we sync by item ID, not slot position)
	 */
	private int resolvedItemId = -1;
}
