package net.runelite.client.plugins.flippingcopilot.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class MutableReference<T> {
    private T value;
}
