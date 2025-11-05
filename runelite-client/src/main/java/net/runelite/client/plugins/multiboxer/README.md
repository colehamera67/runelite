# Multiboxer Plugin

A RuneLite plugin that synchronizes actions between multiple clients on private servers.

## Features

- **Action Synchronization**: Automatically syncs clicks and interactions across multiple RuneLite clients
- **Smart Inventory Syncing**: Syncs inventory actions by item ID, not slot position (so items in different slots will still work)
- **Filtered Actions**: Only syncs relevant actions (excludes map walking)
- **Flexible Networking**: Run one client as server, others connect to it

## How It Works

The plugin intercepts menu actions (clicks) on one client and replicates them on all connected clients. Key features:

1. **Object Interactions**: Clicking on game objects, doors, ladders, etc. are synced
2. **NPC Interactions**: Attacking NPCs, trading, talking, etc. are synced
3. **Player Interactions**: Trading with players, following, etc. are synced
4. **Inventory Actions**: Using items is synced by item ID, so if you have the same item in different inventory slots, it will still work
5. **Interface Interactions**: Clicking buttons, spell book, prayer book, equipment, etc. are synced
6. **Walking Excluded**: Regular map clicking for movement is NOT synced (only you move)

## Setup Instructions

### Step 1: Choose Server/Client Mode

One of your RuneLite clients needs to act as the "server" (host), and the others connect to it as "clients".

**On the Server Client:**
1. Enable the Multiboxer plugin
2. Open the plugin configuration
3. Set `Server Mode` to **ON** (checked)
4. Note the `Server Port` (default: 43594)
5. Make sure `Enable Syncing` is **ON**

**On Each Client:**
1. Enable the Multiboxer plugin
2. Open the plugin configuration
3. Set `Server Mode` to **OFF** (unchecked)
4. Set `Server Address` to the IP of the server computer (use `localhost` if on same machine)
5. Set `Server Port` to match the server (default: 43594)
6. Make sure `Enable Syncing` is **ON**

### Step 2: Test the Connection

1. Start the server client first (the one with Server Mode ON)
2. Start the other clients
3. Look in the RuneLite logs - you should see messages like:
   - Server: "Multiboxer server started on port 43594"
   - Client: "Connected to multiboxer server at localhost:43594"

### Step 3: Test Syncing

1. Log in to your private server on both clients
2. Try clicking on an object or NPC on one client
3. You should see the same action happen on the other client(s)

## Configuration Options

| Option | Description |
|--------|-------------|
| **Enable Syncing** | Master toggle for all synchronization |
| **Server Mode** | ON = Act as server (host), OFF = Connect to server |
| **Server Address** | IP address of the server (for clients) |
| **Server Port** | Port number for communication (default: 43594) |
| **Sync Inventory Actions** | Enable/disable inventory item usage syncing |
| **Sync NPC Interactions** | Enable/disable NPC interaction syncing |
| **Sync Object Interactions** | Enable/disable object interaction syncing |
| **Sync Player Interactions** | Enable/disable player interaction syncing |

## What Gets Synced

### ✅ Synced Actions

- Clicking on game objects (doors, ladders, rocks, trees, etc.)
- Attacking NPCs
- Trading with NPCs or players
- Using inventory items
- Using items on objects/NPCs
- Player interactions (trade, follow, etc.)
- **Interface interactions** (clicking buttons, interface options, spell book, prayer book, etc.)
- Widget interactions (inventory tabs, settings, game interface buttons)
- Ground item interactions (take, examine actions on items)

### ❌ NOT Synced Actions

- Regular walking (clicking on the map to move)
- Camera movement
- Examine text (right-click examine)
- Chat messages
- Client-side overlays
- RuneLite plugin menu options

## Network Architecture

```
Client A (Server Mode)  <---->  Client B
                        <---->  Client C
                        <---->  Client D
```

- One client runs in "Server Mode" and accepts connections
- All other clients connect to the server
- When any client performs an action, it's sent to the server
- The server broadcasts the action to all other connected clients
- Each client executes the action locally

## Troubleshooting

### Clients not connecting?

1. Check that the server client is running with Server Mode ON
2. Verify the Server Address is correct (use `localhost` if on same machine)
3. Check that the Server Port matches on all clients
4. Check firewall settings if clients are on different machines

### Actions not syncing?

1. Verify `Enable Syncing` is ON on all clients
2. Check the RuneLite logs for errors
3. Make sure you're performing actions that should sync (not just walking)
4. Verify all clients are logged into the game

### Inventory actions not working?

1. Make sure the item exists in the inventory on all clients
2. The plugin finds items by ID, so the same item must be present
3. Check that `Sync Inventory Actions` is enabled

## Technical Details

### Item ID vs Slot Position

Traditional multiboxing tools sync inventory clicks by slot position (e.g., "click slot 5"). This plugin is smarter - it syncs by **item ID**:

- When you click an item, the plugin identifies which item ID you clicked
- It sends the item ID (not the slot number) to other clients
- Each client finds that item in their own inventory (even if it's in a different slot)
- The click is replicated using the correct slot on each client

This means your inventories don't need to be perfectly organized the same way!

### Message Format

Actions are serialized to JSON and sent over TCP sockets:

```json
{
  "menuAction": "NPC_FIRST_OPTION",
  "option": "Attack",
  "target": "Goblin",
  "identifier": 12345,
  "param0": 0,
  "param1": 0,
  "itemId": -1,
  "resolvedItemId": -1
}
```

## Limitations

- Only works on private servers (Jagex doesn't allow multiboxing on official servers)
- All clients must have the same items available for inventory syncing to work
- No protection against desync if clients are in different locations
- Basic network protocol with no encryption (use on trusted networks only)

## Safety & Legal

⚠️ **WARNING**: This plugin is designed for private servers only. Using multiboxing tools on the official Old School RuneScape servers violates Jagex's rules and can result in account bans. Use responsibly!

## Development

The plugin consists of:

- `MultiboxerPlugin.java`: Main plugin class, handles event interception
- `MultiboxerConfig.java`: Configuration interface
- `MultiboxerNetworkManager.java`: Network communication (server/client)
- `ActionMessage.java`: Data class for action synchronization

## License

This plugin is part of RuneLite and follows the same BSD 2-Clause License.
