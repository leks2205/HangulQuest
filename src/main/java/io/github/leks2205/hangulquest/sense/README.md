# sense/

`EnvironmentSensor` turns client events and the local player's state into one `Observation` per tick. Everything in an observation is plain, transient data; nothing is persisted and nothing talks to the server.

The pure logic lives in small classes that take fake inputs in tests (`BlockNeighborhood`, `PlacementDiff`, `ResultTakeTracker`, `ContainerDiff`, `GiveTracker`, `HeldTracker`, `SkyExposure`, `DeathTracker`, `EventBuffer`). `ScreenSensor` and `EnvironmentSensor` are the thin game-side glue.

## Observation v2 facts

How far each fact can be trusted when the game runs client-only:

| Fact | Source | Reliability |
|---|---|---|
| `mainHand`, `offHand` + held ms | player hands | Reliable. Compares item ids, so a changing count does not reset the clock. |
| `inWater`, `canSeeSky`, `roofed`, `underground` | player state, heightmaps, 6 blocks above | Reliable. Refreshed every 10 ticks, on a block change and after a placement. `underground` is false in dimensions without a sky. |
| `nearbyEntityPositions` | entity scan (24 blocks) | Reliable for what the client has loaded. Items and orbs are left out. One small record per entity per tick. |
| `placements` | diff of the 5x5x5 region around a right-clicked block | Mostly reliable. One placement per block id (a door is one). Dispensers, pistons and other players are not seen. A fast second click before the first is diffed can fold two placements together. |
| `crafted` | before/after snapshot around each click or key press on a screen with a result slot, then the inventory settles | Reliable for the inventory 2x2, crafting table, furnace, blast furnace, smoker, stonecutter, smithing, loom, cartography and grindstone. A shift-click counts everything the server delivered. Not covered: campfire (no screen), anvil, brewing, enchanting, the crafter block, and a result thrown out with Q. |
| `putIn` | per-tick slot tally while a chest, barrel, shulker box, hopper or dispenser screen is open | Reliable for moves by the player. Counts only items the player side really lost; taking out and putting back cancels. The block is the last one right-clicked within 5 s, so it is null for a chest minecart. |
| `gave` FEED / USE_WITH | `UseEntityCallback` plus the held count dropping within 1.5 s | Best effort. Never fires in creative (the stack does not shrink). A loss of the same item for another reason inside the window can be credited. |
| `gave` DROP_NEAR | an inventory loss, then a young item entity within 4.5 blocks, with a creature within 3 blocks | Best effort. The client cannot see who dropped an item, so a loss of the same item plus loot appearing next to a creature could be mistaken for a give. Ignored while a screen is open. |
| `gave` TRADE | merchant result slot taken; item and count are what was paid | Reliable for the payment. The merchant is the entity right-clicked in the last 5 s. |

## Costs

- A right-click on a block reads 125 blocks, as before, and again on the next tick for the diff. The 343-block snapshot is only taken when that diff finds a placement. Ticks without a click do neither.
- With no container screen open, the screen hooks cost nothing per tick. The trackers return a shared empty list while idle, and `Observation` fills its v2 lists lazily.
- The sky and roof lookups run about twice a second, not every tick.
