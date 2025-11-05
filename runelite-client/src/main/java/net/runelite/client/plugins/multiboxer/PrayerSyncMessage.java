package net.runelite.client.plugins.multiboxer;

import lombok.Data;
import java.io.Serializable;

/**
 * Message for syncing prayer activation/deactivation between clients
 */
@Data
public class PrayerSyncMessage implements Serializable
{
	private static final long serialVersionUID = 1L;

	private int varbitId;
	private boolean activated;
	private boolean isQuickPrayer;
}
