# Spigot-Folia Compatibility Layer: Testing Strategy

## 1. Overall Testing Approach

The testing of the Spigot-Folia compatibility layer will be multi-faceted, designed to ensure correctness, safety, and performance at various levels. The primary types of testing to be employed are:

*   **Unit Tests:** Focused, fine-grained tests for individual components and classes within the compatibility layer. These will use mocking (e.g., Mockito) for dependencies like real Bukkit objects or the `RegionTaskDelegator` where appropriate.
*   **Test Plugins (Mini-Plugins):** Small, dedicated Bukkit plugins designed to exercise specific API interactions through the compatibility layer. These will initially run in a controlled test harness that simulates a Bukkit environment and provides instances of our wrapped objects.
*   **Integration Testing (Future):** Once the layer is more mature, it will be tested on a running Folia server (or a Paper server with Folia's scheduler if possible for initial stages) with a selection of real, commonly used Spigot plugins. This will test end-to-end functionality and identify issues that only appear in a live server environment.
*   **Performance Testing (Future):** Benchmarking key API calls and common plugin operations to measure the overhead introduced by the compatibility layer and identify areas for optimization.

**Initial Focus:** The immediate priority will be on comprehensive **Unit Tests** for the foundational components already developed and the design and conceptual implementation of **Test Plugins** to validate interaction flows.

## 2. Unit Test Plan

Unit tests will be crucial for verifying the internal logic of each compatibility layer component. Mocking frameworks like Mockito will be used to isolate components and simulate dependencies.

Key areas for unit testing for each component:

---

### 2.1. Wrappers (`WrappedBlock`, `WrappedWorld`, `WrappedEntity`, `WrappedPlayer`, `WrappedChunk`, `WrappedInventory`, `WrappedPlayerInventory`)

*   **Generic Wrapper Tests (Applicable to most wrappers):**
    *   **Null Handling:** Constructors throw `IllegalArgumentException` if real objects or essential dependencies (like `RegionTaskDelegator`) are null.
    *   **Getter Delegation:** Simple getter methods (e.g., `WrappedBlock.getX()`, `WrappedEntity.getUniqueId()`, `WrappedInventory.getSize()`) correctly call the corresponding method on the mocked real Bukkit object and return its value.
    *   **Equals and HashCode:** `equals()` and `hashCode()` methods correctly delegate to the real Bukkit object. Test equality with another wrapper of the same real object and with the real object itself.
    *   **Wrapper Instantiation:** Methods that return other wrapped objects (e.g., `WrappedWorld.getBlockAt()`, `WrappedChunk.getWorld()`, `WrappedPlayer.getInventory()`) return instances of the correct wrapper type, properly initialized with necessary dependencies (like `RegionTaskDelegator` and the parent wrapper).

*   **`WrappedBlock`:**
    *   `setType(Material)` and `setType(Material, boolean)`: Verify `RegionTaskDelegator.scheduleForLocation()` is called with the correct `Location` and a `Runnable` that, when executed, calls `setType` on the real `Block`.
    *   `getWorld()`: Returns the `WrappedWorld` instance it was constructed with.
    *   `getRelative(...)`: Returns a new `WrappedBlock` for the correct relative block.

*   **`WrappedWorld`:**
    *   `getBlockAt(Location)` and `getBlockAt(int, int, int)`: Return a `WrappedBlock` initialized with the correct real `Block`, `RegionTaskDelegator`, and itself (`WrappedWorld`).
    *   `getChunkAt(Location)` and `getChunkAt(int, int)`: Return a `WrappedChunk` initialized correctly.
    *   `getEntities()`, `getPlayers()`, `getLivingEntities()`, `getEntitiesByClass()`:
        *   Return a `List` (or `Collection`) of the appropriate wrapped entity types.
        *   Verify that the returned list is unmodifiable if specified by the Bukkit API or design.
        *   Verify that the correct wrapper type (`WrappedPlayer`, `WrappedLivingEntity`, `WrappedEntity`) is used for each entity from the real list.
    *   Methods like `setTime()`, `setSpawnLocation()`: Verify they delegate to `RegionTaskDelegator` (e.g., `scheduleForLocation` or a global equivalent if applicable) or directly if deemed safe for specific global properties.

*   **`WrappedChunk`:**
    *   `getWorld()`: Returns the `WrappedWorld` instance.
    *   `getBlock(int, int, int)`: Correctly calculates world coordinates and calls `wrappedWorld.getBlockAt()` to return a `WrappedBlock`.
    *   `getEntities()`: Returns an array of appropriate wrapped entity types.
    *   `load()` and `unload()`: Verify `RegionTaskDelegator.scheduleForLocation()` is called with a representative `Location` for the chunk.

*   **`WrappedEntity` (and subclasses `WrappedLivingEntity`, `WrappedPlayer`):**
    *   `teleport(Location)`: Verify `RegionTaskDelegator.scheduleForEntity()` is called with the real `Entity` and a `Runnable` that performs the teleport on the real entity.
    *   `getWorld()`: Returns a `WrappedWorld` (mock or real, with the delegator).
    *   `getLocation()`: Returns location from real entity (cloned if necessary).
    *   `setHealth(double)` (`WrappedLivingEntity`): Verify `RegionTaskDelegator.scheduleForEntity()` is called.
    *   `kickPlayer(String)` (`WrappedPlayer`): Verify `RegionTaskDelegator.scheduleForEntity()` is called.
    *   `getInventory()` (`WrappedPlayer`): Returns `WrappedPlayerInventory` initialized with the real inventory, delegator, and the `WrappedPlayer` itself as the `InventoryHolderWrapper`.
    *   `getEnderChest()` (`WrappedPlayer`): Returns `WrappedInventory` initialized similarly.

*   **`WrappedInventory` (and `WrappedPlayerInventory`):**
    *   `getItem(int)`: Returns a *clone* of the `ItemStack` from the real inventory. Test with null and non-null items.
    *   `setItem(int, ItemStack)`:
        *   Verifies the input `ItemStack` is cloned before being passed to the `Runnable` for delegation.
        *   Verifies `RegionTaskDelegator.scheduleForInventoryHolder()` is called with the correct `InventoryHolderWrapper` and a `Runnable` that sets the (cloned) item on the real inventory.
    *   `addItem(ItemStack...)`:
        *   Verifies input `ItemStack`s are cloned.
        *   Verifies `RegionTaskDelegator.callForInventoryHolder()` is used.
        *   Verifies the `HashMap` returned by the real `addItem` has its `ItemStack` values cloned before being returned to the plugin.
    *   `getHolder()`: Returns the `InventoryHolderWrapper` it was constructed with.
    *   `getContents()`, `getArmorContents()` etc.: Verify arrays contain cloned `ItemStack`s.
    *   `setContents(ItemStack[])`, `setArmorContents(ItemStack[])` etc.: Verify input arrays and their `ItemStack`s are cloned before delegation.

### 2.2. `RegionTaskDelegator` (`DummyRegionTaskDelegator`)

*   `scheduleForLocation(Location, Runnable)`: Verify it logs the correct message and executes the `Runnable` immediately. Test with mock `Location` and `Runnable`.
*   `scheduleForEntity(Entity, Runnable)`: Verify it logs and executes. Test with mock `Entity`.
*   `callForLocation(Location, Callable)`: Verify it logs, executes the `Callable` immediately, and returns a completed `Future` with the correct value or exception.
*   `callForEntity(Entity, Callable)`: Similar to `callForLocation`.
*   `scheduleForInventoryHolder(InventoryHolderWrapper, Runnable)`:
    *   If holder `isPlayerHolder()`, verify it calls `scheduleForEntity` with the player from `holder.getAsPlayerHolder().getRealEntity()`.
    *   If not a player holder and `getLocationForDelegation()` is non-null, verify it calls `scheduleForLocation`.
    *   If not a player and location is null, verify it logs a warning and runs immediately.
*   `callForInventoryHolder(InventoryHolderWrapper, Callable)`: Similar logic to `scheduleForInventoryHolder` but for `callForEntity/Location`.
*   `getRegionId(Location)` and `getRegionId(Entity)`: Verify the placeholder logic for deriving region IDs.

### 2.3. Event Wrapping (`EventWrapperService`, `GenericEventInvocationHandler`)

*   **`EventWrapperService.wrapEvent(Event)`:**
    *   Returns a proxy when given a mock Bukkit event.
    *   The proxy should implement all interfaces of the original event's class.
    *   Verify it passes the `RegionTaskDelegator` to the `GenericEventInvocationHandler`.
*   **`GenericEventInvocationHandler`:**
    *   Test with a proxied mock `PlayerInteractEvent`.
        *   Calling `getPlayer()` on the proxy returns a `WrappedPlayer`.
        *   Calling `getClickedBlock()` returns a `WrappedBlock` (or null if real event returns null).
        *   Calling `getItem()` returns a *clone* of the `ItemStack` from the real event.
        *   Other `PlayerInteractEvent` methods (e.g., `getAction()`, `getHand()`) correctly delegate to the real event and return the original value.
    *   Test with a generic mock `Event` that returns other Bukkit objects (e.g., a method `getWorld()`, `getEntity()`). Verify these are wrapped/cloned as per `wrapObject` logic.
    *   Test `hashCode()`, `equals()`, `toString()` are correctly delegated or handled.
    *   Test the internal cache: calling the same getter method (e.g., `getPlayer()`) multiple times on the same proxied event returns the exact same `WrappedPlayer` instance.

### 2.4. Schedulers (`WrappedBukkitScheduler`, `GlobalCompatibilityTickScheduler`, `WrappedBukkitTask`)

*   **`WrappedBukkitTask`:**
    *   Constructor correctly sets ID, owner, sync status, runnable.
    *   `cancel()`: Sets cancelled status to true and calls `scheduler.performCancel()`.
    *   `isSync()`, `getOwner()`, `getTaskId()` return correct values.
*   **`GlobalCompatibilityTickScheduler`:**
    *   `scheduleSync(...)` (for immediate tasks): Verify the task `Runnable` is executed on its `singleThreadExecutorForImmediateSync`.
    *   `scheduleSync(...)` (for delayed/repeating tasks): Verify the task `Runnable` is scheduled with the `ScheduledExecutorService` with correct delay/period (converted from ticks to ms).
    *   `submitCallable(...)`: Verify the `Callable` executes on the `singleThreadExecutorForImmediateSync` and the `Future` completes with the correct value/exception.
    *   `cancelTask(int)`: Marks the `WrappedBukkitTask` as cancelled and attempts to cancel the `ScheduledFuture<?>`.
    *   `shutdown()`: Shuts down its executors.
*   **`WrappedBukkitScheduler`:**
    *   `runTask(plugin, runnable)` (and other sync variants): Verify it calls `globalSyncScheduler.scheduleSync` and returns a `WrappedBukkitTask`.
    *   `runTaskAsynchronously(plugin, runnable)` (and other async variants): Verify the runnable is submitted to its internal async `ExecutorService` and returns a `WrappedBukkitTask`. (Precise timing for async delays/repeats is secondary for initial unit tests).
    *   `callSyncMethod(plugin, callable)`: Verify it calls `globalSyncScheduler.submitCallable` and returns the `Future`.
    *   `cancelTask(int)`: Delegates to `globalSyncScheduler.cancelTask()`.
    *   `cancelTasks(Plugin)`: Correctly identifies and cancels tasks for the given plugin via `globalSyncScheduler`.

---

## 3. Test Plugin Specifications

These "mini-plugins" are designed to be run in a controlled test harness that can simulate a Bukkit environment and inject wrapped objects.

### 3.1. `CompatBlockChangePlugin`

*   **Objective:** Test `Block.setType()`, `Block.getType()`, and obtaining `Block` instances from `World` and `Chunk` wrappers.
*   **Setup:**
    *   Triggered by a command (e.g., `/testblockchange <x> <y> <z> <material>`).
    *   Assumes a `WrappedWorld` and `RegionTaskDelegator` are provided/accessible to the command executor.
    *   A mock `Player` can be the command sender.
*   **Key Actions:**
    1.  Command executor receives command.
    2.  Gets `WrappedWorld` (e.g., from player or test harness).
    3.  Calls `world.getBlockAt(x, y, z)` to get a `WrappedBlock` (`blockA`).
    4.  Calls `blockA.getType()` and logs the original material.
    5.  Calls `blockA.setType(newMaterial)`.
    6.  Calls `world.getChunkAt(blockA.getLocation()).getBlock(x_in_chunk, y, z_in_chunk)` to get `blockB`.
    7.  Asserts `blockA.getLocation().equals(blockB.getLocation())`.
    8.  Calls `blockB.getType()` and logs the new material.
*   **Verification:**
    *   Logs from `DummyRegionTaskDelegator` show `scheduleForLocation` was called for `setType`.
    *   Logs from the plugin show the correct original and new materials (requires `getType` to also work, potentially via delegation for verification if the dummy delegator doesn't actually change state).
    *   Verify that `blockA` and `blockB` (if obtained after `setType`'s task conceptually completes) show the new type.

### 3.2. `CompatEntityInteractionPlugin`

*   **Objective:** Test `Entity.teleport()`, `Entity.getLocation()`, `LivingEntity.setHealth()`, `Player.kickPlayer()`, and obtaining entities from `World/Chunk`.
*   **Setup:**
    *   Triggered by command (e.g., `/testentity <action> [params...]`).
    *   Requires a target `WrappedPlayer` or `WrappedEntity` (can be spawned/provided by harness).
*   **Key Actions:**
    *   **Teleport:**
        1.  Get `WrappedPlayer` (`playerA`).
        2.  Call `playerA.getLocation()` and log it.
        3.  Define a `newLocation`.
        4.  Call `playerA.teleport(newLocation)`.
        5.  Call `playerA.getLocation()` again and log it (may not reflect change immediately with dummy delegator, but tests the call path).
    *   **Set Health:**
        1.  Get `WrappedLivingEntity` (`mobA`).
        2.  Call `mobA.setHealth(10.0)`.
    *   **Kick Player:**
        1.  Get `WrappedPlayer` (`playerB`).
        2.  Call `playerB.kickPlayer("Test kick")`.
    *   **Get Entities from World:**
        1.  Call `wrappedWorld.getEntities()` or `wrappedWorld.getPlayers()`.
        2.  Iterate and log `entity.getUniqueId()` and `entity.getType()`. Verify they are wrapped instances.
*   **Verification:**
    *   `DummyRegionTaskDelegator` logs show `scheduleForEntity` for `teleport`, `setHealth`, `kickPlayer`.
    *   Plugin logs show expected method calls and types.
    *   Return types from `getEntities()` etc., are `WrappedEntity` or subclasses.

### 3.3. `CompatInventoryOperationsPlugin`

*   **Objective:** Test `Player.getInventory()`, `Inventory.getItem()`, `setItem()`, `addItem()`, focusing on `ItemStack` cloning and delegation.
*   **Setup:**
    *   Command triggered (e.g., `/testinventory <playername>`).
    *   Requires a `WrappedPlayer` with an inventory.
*   **Key Actions:**
    1.  Get `WrappedPlayer`.
    2.  Call `player.getInventory()` to get `WrappedPlayerInventory` (`inv`).
    3.  Create `itemStackA = new ItemStack(Material.DIAMOND, 1)`.
    4.  Call `inv.setItem(0, itemStackA)`.
    5.  Modify `itemStackA` locally (e.g., `itemStackA.setAmount(5)`).
    6.  Call `retrievedItemA = inv.getItem(0)`.
    7.  Assert `retrievedItemA.getAmount()` is 1 (due to cloning on set and get).
    8.  Create `itemStackB = new ItemStack(Material.GOLD_INGOT, 1)`.
    9.  Call `inv.addItem(itemStackB)`.
    10. Log leftover items from `addItem`.
*   **Verification:**
    *   `DummyRegionTaskDelegator` logs `scheduleForInventoryHolder` (or `scheduleForEntity` via holder logic) for `setItem` and `callForInventoryHolder` for `addItem`.
    *   Assertions for `ItemStack` cloning (amounts, distinct objects) pass.
    *   Logs show correct parameters being passed for delegation.

### 3.4. `CompatEventHandlingPlugin`

*   **Objective:** Test that event handlers receive proxied events where Bukkit objects are wrapped.
*   **Setup:**
    *   A plugin that registers listeners for events like `PlayerInteractEvent`, `BlockBreakEvent`.
    *   Test harness will need to simulate event firing, passing the event through the `EventWrapperService`.
*   **Key Actions (within event handler, e.g., `onPlayerInteract(PlayerInteractEvent event)`):**
    1.  Call `event.getPlayer()` and assert it's a `WrappedPlayer`.
    2.  Call `event.getClickedBlock()` and assert it's a `WrappedBlock` (if not null).
    3.  Call `event.getItem()` and assert it's a distinct `ItemStack` clone if modified locally after getting.
    4.  Perform an action on the wrapped object, e.g., `WrappedPlayer p = (WrappedPlayer) event.getPlayer(); p.setHealth(p.getHealth() -1);`
*   **Verification:**
    *   Assertions for wrapped types pass inside the event handler.
    *   `DummyRegionTaskDelegator` logs show that actions performed on wrapped objects (like `setHealth`) are correctly delegated.
    *   No ClassCastExceptions when casting to wrapped types.

### 3.5. `CompatSchedulerPlugin`

*   **Objective:** Test `WrappedBukkitScheduler` for sync and async tasks, ensuring API calls within tasks are correctly handled.
*   **Setup:**
    *   Command triggered (e.g., `/testscheduler`).
    *   Access to `WrappedBukkitScheduler` via test harness.
*   **Key Actions:**
    1.  **Sync Task:**
        *   `scheduler.runTask(plugin, () -> { WrappedBlock b = world.getBlockAt(...); b.setType(Material.GOLD_BLOCK); })`
    2.  **Async Task:**
        *   `scheduler.runTaskAsynchronously(plugin, () -> { WrappedBlock b = world.getBlockAt(...); b.setType(Material.IRON_BLOCK); })`
    3.  **`callSyncMethod`:**
        *   `Future<Material> future = scheduler.callSyncMethod(plugin, () -> { WrappedBlock b = world.getBlockAt(...); return b.getType(); })`
        *   Log the material obtained from the future.
    4.  **Task Cancellation:** Schedule a delayed task and then cancel it.
*   **Verification:**
    *   `GlobalCompatibilityTickScheduler` logs show sync tasks being executed on its thread.
    *   `DummyRegionTaskDelegator` logs show `setType` calls (from both sync and async tasks) being delegated.
    *   `callSyncMethod` future returns the correct value, and the callable was logged by the sync scheduler.
    *   Cancellation logs show tasks being marked as cancelled.

### 3.6. `CompatChunkOperationsPlugin`

*   **Objective:** Test `Chunk.load()`, `unload()`, `getBlock()`, `getEntities()`.
*   **Setup:**
    *   Command triggered (e.g., `/testchunkops <x> <z>`).
    *   Access to `WrappedWorld`.
*   **Key Actions:**
    1.  Call `world.getChunkAt(x, z)` to get `WrappedChunk` (`chunk`).
    2.  Call `chunk.load()`.
    3.  Call `chunk.isLoaded()` and log.
    4.  Call `chunk.getBlock(0, 64, 0)` to get `WrappedBlock`. Perform a `setType` on it.
    5.  Call `chunk.getEntities()` and verify returned entities are wrapped.
    6.  Call `chunk.unload()`.
*   **Verification:**
    *   `DummyRegionTaskDelegator` logs show `scheduleForLocation` for `load`, `unload`, and `setType` via `getBlock`.
    *   Entities returned by `chunk.getEntities()` are instances of wrapped types.

---

## 4. Tooling and Environment for Test Plugins (Conceptual)

Initially, running these test plugins directly on a Folia server might be complex or premature. A dedicated test harness is proposed to facilitate early testing:

*   **Plugin Loading:** The harness should be able to instantiate the main class of a test plugin (which extends `org.bukkit.plugin.java.JavaPlugin`). It doesn't need full lifecycle management (`onEnable`, `onDisable` can be called manually).
*   **Dependency Injection/Setup:**
    *   The harness will create instances of `DummyRegionTaskDelegator`, `GlobalCompatibilityTickScheduler`, and `WrappedBukkitScheduler`.
    *   It will create mock `org.bukkit.Server`, `org.bukkit.World`, `org.bukkit.Player`, etc. objects (using Mockito).
    *   These mock Bukkit objects will then be wrapped by their respective compatibility layer wrappers (e.g., `new WrappedWorld(mockWorld, dummyDelegator)`).
    *   The test plugin's command executors or event listeners will be manually registered or invoked by the harness.
    *   Crucially, when the plugin attempts to get the server, world, player, or scheduler (e.g., via `Bukkit.getWorld()`, `event.getPlayer()`, `Bukkit.getScheduler()`), the harness will ensure it receives the *wrapped* instances. This might involve:
        *   A simple static accessor system within the test environment for `Bukkit.getServer()`, `Bukkit.getScheduler()`.
        *   Manually passing wrapped objects to command executors or event handlers.
*   **Command Simulation:** The harness will provide a way to invoke a plugin's `onCommand` method with a simulated `CommandSender` (which could be a `WrappedPlayer`) and command arguments.
*   **Event Simulation:**
    *   The harness will create real Bukkit event objects (e.g., `new PlayerInteractEvent(realPlayer, action, item, clickedBlock, blockFace)`).
    *   It will then use the `EventWrapperService` (initialized with the `DummyRegionTaskDelegator`) to wrap this event: `wrappedEvent = eventWrapperService.wrapEvent(realEvent)`.
    *   The `wrappedEvent` is then passed to the test plugin's registered listener method (e.g., by directly calling `listener.onPlayerInteract(wrappedEvent)`).
*   **Logging Access:** The harness must capture and make accessible (e.g., to test assertions) the logs produced by:
    *   The test plugin itself.
    *   `DummyRegionTaskDelegator` (to verify delegation calls).
    *   `GlobalCompatibilityTickScheduler` (to verify task execution contexts).
*   **Assertion Framework:** Standard JUnit or TestNG assertions will be used within the test harness code to verify plugin behavior and the effects of the compatibility layer.

This harness allows testing the interaction between the plugin's Bukkit API calls and the compatibility layer's wrapping/delegation logic in a controlled environment before deploying to a full server.

---
