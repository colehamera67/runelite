# Prayer Equipment Swap Plugin

A RuneLite plugin that automatically attempts to swap equipment based on active overhead prayers.

## Features

- Detects when overhead prayers change (Protect from Melee, Missiles, Magic, and Ruinous Powers variants)
- Automatically attempts to equip the configured gear based on the prayer:
  - **Melee Prayer** (Protect from Melee / Dampen Melee) → Switches to **Range gear**
  - **Range Prayer** (Protect from Missiles / Dampen Ranged) → Switches to **Melee gear**
  - **Magic Prayer** (Protect from Magic / Dampen Magic) → Switches to **Range gear**

## Configuration

### Enable Auto Swapping
Master toggle to enable/disable the plugin functionality.

### Melee Weapon IDs
Comma-separated list of item IDs for melee weapons/gear to equip.
Example: `4151,11802` (Abyssal whip, Armadyl godsword)

### Range Weapon IDs
Comma-separated list of item IDs for range weapons/gear to equip.
Example: `11235,12926` (Dark bow, Toxic blowpipe)

### Swap Weapon Only
If enabled, only swaps the main weapon. If disabled, attempts to swap all configured gear items.

### Swap Delay (ms)
Delay in milliseconds before attempting the equipment swap (0-2000ms). Useful for adding a small delay to avoid rapid switching.

## How to Find Item IDs

1. Use the RuneLite "Examine Item Stats" feature
2. Check the OSRS Wiki item pages
3. Use the DevTools plugin in RuneLite to inspect items

## Usage Example

1. Configure the plugin with your gear:
   - Melee Weapon IDs: `4151` (Abyssal whip)
   - Range Weapon IDs: `11235` (Dark bow)
2. Enable "Enable Auto Swapping"
3. Activate an overhead prayer
4. The plugin will detect the prayer change and attempt to equip the configured gear

## Important Notes

- This plugin works within the constraints of the RuneLite API
- Items must be in your inventory to be equipped
- The plugin creates menu entries for equipment swapping
- Make sure to configure item IDs correctly for your gear setup

## Limitations

Due to RuneLite API constraints and OSRS rules regarding automation, this plugin may have limitations in fully automatic behavior. The plugin creates the necessary menu infrastructure for equipment swapping, but the actual execution may require user interaction in some cases.

## PvP/PKing Usage

This plugin is particularly useful for:
- NH (No-Honour) PKing where you switch between combat styles
- Hybrid fights where you need quick gear switches
- Practice switching in PvP scenarios

## Support

For issues or feature requests, please refer to the RuneLite plugin development documentation.
