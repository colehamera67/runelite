package net.runelite.client.plugins.multibox;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class MultiboxOverlay extends OverlayPanel
{
	private final MultiboxPlugin plugin;
	private final MultiboxConfig config;

	@Inject
	private MultiboxOverlay(MultiboxPlugin plugin, MultiboxConfig config)
	{
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		// Title
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Multibox")
			.color(Color.GREEN)
			.build());

		// Mode
		String modeText = config.mode() == MultiboxMode.MASTER ? "Master" : "Slave";
		Color modeColor = config.mode() == MultiboxMode.MASTER ? Color.CYAN : Color.ORANGE;
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Mode:")
			.right(modeText)
			.rightColor(modeColor)
			.build());

		// Status
		String statusText = plugin.isConnected() ? "Connected" : "Disconnected";
		Color statusColor = plugin.isConnected() ? Color.GREEN : Color.RED;
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status:")
			.right(statusText)
			.rightColor(statusColor)
			.build());

		// IP Address
		if (config.mode() == MultiboxMode.MASTER)
		{
			String localIp = plugin.getLocalIpAddress();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Local IP:")
				.right(localIp)
				.rightColor(Color.WHITE)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Port:")
				.right(String.valueOf(config.port()))
				.rightColor(Color.WHITE)
				.build());

			int slaveCount = plugin.getConnectedSlaveCount();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Slaves:")
				.right(String.valueOf(slaveCount))
				.rightColor(Color.YELLOW)
				.build());
		}
		else
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Master IP:")
				.right(config.masterIp())
				.rightColor(Color.WHITE)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Port:")
				.right(String.valueOf(config.port()))
				.rightColor(Color.WHITE)
				.build());
		}

		return super.render(graphics);
	}
}
