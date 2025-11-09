package net.runelite.client.plugins.flippingcopilot.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProfileSuggestionPreferences {

    public List<Integer> blockedItemIds = new ArrayList<>();
}
