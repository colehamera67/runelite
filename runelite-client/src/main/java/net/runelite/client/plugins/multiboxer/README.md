# Multiboxer Plugin

A RuneLite plugin that synchronizes actions between multiple clients on private servers.

## Features

### Action Synchronization
- **Smart Inventory Syncing**: Syncs inventory actions by item ID, not slot position
- **Interface Interactions**: Syncs clicks on spell book, prayer book, equipment, etc.
- **Filtered Actions**: Only syncs relevant actions (excludes map walking)
- **Flexible Networking**: Run one client as server, others connect to it

### Automatic Features (Slave Clients Only)
- **Auto-Eat**: Automatically eat food when health drops below threshold
- **Auto-Restore Prayer**: Automatically drink prayer/restore potions when prayer drops low
- **Auto-Drink Stat Potions**: Automatically re-pot when combat stat boosts wear off
- **Auto-Drink Stamina**: Automatically drink stamina potions when run energy is low

### Syncing Features
- **Quick Prayer Syncing**: When master activates/deactivates quick prayers, slaves follow
- **Individual Prayer Syncing**: Sync individual prayer activation (Piety, Protect from Melee, etc.)
- **Special Attack Coordination**: When master uses special attack, slaves use theirs too
- **Combat Style Syncing**: Sync attack style changes (aggressive, defensive, controlled, etc.)

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

### Step 4: Configure Auto-Eat (Optional - Slave Clients Only)

The auto-eat feature automatically eats food when your health drops below a threshold. This only works on **slave clients** (clients with Server Mode OFF).

**On Slave Clients:**
1. Open the Multiboxer plugin configuration
2. Enable `Auto-Eat (Slave Only)`
3. Set `Auto-Eat Health %` (e.g., 50 = eat when health drops to 50% or below)
4. Optionally customize `Food Item IDs` - a comma-separated list of item IDs to eat

**Default Food Items:**
- 385 = Shark
- 373 = Swordfish
- 379 = Lobster
- 333 = Trout
- 329 = Salmon
- 361 = Tuna
- 7946 = Monkfish
- 2142 = Cooked karambwan
- 391 = Manta ray
- 365 = Bass
- 380 = Sea turtle
- 386 = Shark (noted)

The slave client will automatically eat food from its inventory when health drops below the threshold. Food is NOT synced from master to slaves - each client eats independently based on its own health.

### Step 5: Configure Other Auto-Features (Optional - Slave Clients Only)

**Auto-Restore Prayer:**
- Enable `Auto-Restore Prayer (Slave Only)`
- Set `Auto-Prayer %` threshold (e.g., 30 = drink at 30% or below)
- Customize `Prayer Potion IDs` if needed

**Auto-Drink Stat Potions:**
- Enable `Auto-Stat Potions (Slave Only)`
- Set `Stat Boost Threshold` (e.g., 5 = re-pot when +4 or lower)
- Customize `Stat Potion IDs` for super attack, strength, defense, etc.

**Auto-Drink Stamina:**
- Enable `Auto-Stamina (Slave Only)`
- Set `Auto-Stamina %` threshold (e.g., 40 = drink at 40% energy)
- Won't waste potions if stamina buff is already active

## Configuration Options

### Network Settings
| Option | Description |
|--------|-------------|
| **Enable Syncing** | Master toggle for all synchronization |
| **Server Mode** | ON = Act as server (host), OFF = Connect to server |
| **Server Address** | IP address of the server (for clients) |
| **Server Port** | Port number for communication (default: 43594) |

### Syncing Options
| Option | Description |
|--------|-------------|
| **Sync Inventory Actions** | Enable/disable inventory item usage syncing |
| **Sync NPC Interactions** | Enable/disable NPC interaction syncing |
| **Sync Object Interactions** | Enable/disable object interaction syncing |
| **Sync Player Interactions** | Enable/disable player interaction syncing |
| **Sync Quick Prayer** | Sync quick prayer activation/deactivation |
| **Sync Individual Prayers** | Sync individual prayer activation (Piety, Protect from Melee, etc.) |
| **Sync Special Attack** | Sync special attack usage between clients |
| **Min Special Energy** | Minimum spec energy required for slaves to use spec (0-100) |
| **Sync Combat Style** | Sync attack style changes (aggressive, defensive, etc.) |

### Auto-Features (Slave Clients Only)
| Option | Description |
|--------|-------------|
| **Auto-Eat (Slave Only)** | Automatically eat food at low health |
| **Auto-Eat Health %** | Health percentage threshold (1-99) |
| **Food Item IDs** | Comma-separated list of food item IDs |
| **Auto-Restore Prayer** | Automatically drink prayer/restore potions |
| **Auto-Prayer %** | Prayer percentage threshold (1-99) |
| **Prayer Potion IDs** | Comma-separated list of prayer potion IDs |
| **Auto-Stat Potions** | Automatically re-pot when stat boosts drop |
| **Stat Boost Threshold** | Re-pot when boost drops below this level |
| **Stat Potion IDs** | Comma-separated list of stat potion IDs |
| **Auto-Stamina** | Automatically drink stamina potions |
| **Auto-Stamina %** | Run energy percentage threshold (1-99) |
| **Stamina Potion IDs** | Comma-separated list of stamina potion IDs |

## Feature Details

### Auto-Eat (Slave Clients Only)
Monitors health every game tick and automatically eats food when health drops below the configured percentage. Uses a 3-tick cooldown to prevent spam. Supports any food item by ID.

**Default Food Items:** Shark, Lobster, Swordfish, Monkfish, Karambwan, Manta ray, Sea turtle, and more.

### Auto-Restore Prayer (Slave Clients Only)
Monitors prayer points and automatically drinks prayer/restore potions when prayer drops below threshold. Uses a 3-tick cooldown.

**Default Potions:** Prayer potion (1-4), Super restore (1-4), Sanfew serum (1-4), and variants.

**Default IDs:** 2434,3024,139,141,143,3026,3028,3030,10925,10927,10929,10931

### Auto-Drink Stat Potions (Slave Clients Only)
Monitors combat stat boosts (Attack, Strength, Defense, Ranged, Magic) and automatically re-pots when any boost drops below threshold. Uses a 5-tick cooldown.

**Default Potions:** Super attack, Super strength, Super defense, Ranging potion, Magic potion, and more.

**Default IDs:** 2436,145,147,149,157,159,161,2440,163,165,167,169,171,173,2442,2444,3016,3018,3020,3022

### Auto-Drink Stamina (Slave Clients Only)
Monitors run energy and automatically drinks stamina potions when energy drops below threshold. Smart enough to NOT waste potions if stamina buff is already active. Uses a 10-tick cooldown.

**Default Potions:** Stamina potion (1-4 dose)

**Default IDs:** 12625,12627,12629,12631

### Quick Prayer Syncing
Monitors the quick prayer varbit (4103) and syncs activation/deactivation between clients. When master toggles quick prayers on/off, all slaves do the same.

### Individual Prayer Syncing
Monitors all prayer varbits (4104-4129) and syncs individual prayer activation. When master activates Piety, Protect from Melee, or any other prayer, slaves activate the same prayer.

**Note:** Prayer activation typically requires clicking the prayer interface, so this may have limitations depending on the private server implementation.

### Special Attack Coordination
Monitors special attack energy (VarPlayer 300) and detects when master uses a special attack. Slaves will only use their special if they have enough energy (configurable minimum).

**Example:** Set minimum to 25%, master uses spec, only slaves with 25%+ energy will use their spec too.

### Combat Style Syncing
Monitors attack style (VarPlayer 43) and syncs changes between clients. When master switches from Aggressive to Defensive, all slaves switch too.

**Attack Styles:** 0=Accurate, 1=Aggressive, 2=Controlled, 3=Defensive (varies by weapon type)

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
- **Quick prayer toggling** (master activates, slaves activate)
- **Individual prayers** (master activates Piety, slaves activate Piety)
- **Special attacks** (master uses spec, slaves use spec if they have enough energy)
- **Combat style changes** (master switches to Defensive, slaves switch to Defensive)

### ❌ NOT Synced Actions

- Regular walking (clicking on the map to move)
- Camera movement
- Examine text (right-click examine)
- Chat messages
- Client-side overlays
- RuneLite plugin menu options
- Auto-features (each slave manages its own health/prayer/stats independently)

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
