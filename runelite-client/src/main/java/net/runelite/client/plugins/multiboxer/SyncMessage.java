package net.runelite.client.plugins.multiboxer;

import lombok.Data;
import java.io.Serializable;

/**
 * Wrapper message for different sync types
 */
@Data
public class SyncMessage implements Serializable
{
	private static final long serialVersionUID = 1L;

	public enum MessageType
	{
		ACTION,
		PRAYER,
		SPECIAL_ATTACK,
		COMBAT_STYLE
	}

	private MessageType type;
	private String payload; // JSON string of the actual message
}
