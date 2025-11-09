package net.runelite.client.plugins.flippingcopilot.ui.graph;


@FunctionalInterface
public interface CoordinateConverter {
    int toValue(int coordinate);
}