# Spigot API Thread-Safety Analysis for Folia

This document analyzes potential thread-safety issues in the Spigot API when used in Folia's multi-region environment.

## Methodology

The analysis focuses on identifying:

*   **Shared Mutable State:** Methods reading/writing data potentially shared across regions or between region/global threads.
*   **Cross-Region Access:** Methods inherently accessing data that might belong to another region.
*   **Assumed Synchronicity:** Methods/event handlers where plugins might assume synchronous execution or immediate effect on the main server thread.
*   **Non-Atomic Operations:** Sequences of calls plugins might expect to be atomic but could be interleaved.

Issues are categorized as:

*   Data race
*   Deadlock potential
*   Stale data reads
*   Incorrect assumptions about execution context
*   Unintended side effects due to parallel execution

Priority is assigned as High, Medium, or Low.

## API Analysis

---

### 1. `org.bukkit.World` and related classes (`Chunk`, `Block`, `Location`)

#### **`World.getLoadedChunks()` and `World.getEntities()` (and similar entity/chunk retrieval methods)**

*   **Description:** These methods return collections of chunks or entities. In Folia, these collections might be modified concurrently by different region threads if the underlying data structures are not synchronized. Iterating over these collections while they are being modified by region ticks can lead to `ConcurrentModificationException` or inconsistent views of the world state.
*   **Issue Category:** Data race, Stale data reads.
*   **Example:**
    ```java
    // Plugin code running on a global thread or a region thread
    for (Chunk chunk : world.getLoadedChunks()) {
        // another region might unload this chunk concurrently
        // or add/remove entities from it
        for (Entity entity : chunk.getEntities()) {
            // process entity
        }
    }
    ```
*   **Potential Issue:** If `world.getLoadedChunks()` returns a live list (or a shallow copy of a list that can change), a region thread might unload a chunk while another thread (global or another region) is iterating over it. Similarly, `chunk.getEntities()` could face issues if entities are added/removed by the region thread owning that chunk.
*   **Folia Consideration:** Folia is expected to make these return immutable/thread-safe snapshots when called from a different context (e.g. global thread accessing region data) or restrict access. However, plugins might not be aware of this.
*   **Priority:** High (due to frequent use and potential for crashes/inconsistency).

#### **`World.getBlockAt(Location)` / `World.getBlockAt(int x, int y, int z)` followed by `Block.setType(Material)`**

*   **Description:** A common pattern is to get a block and then modify it. If the location is near a region border, or if the plugin is not aware of region ownership, it might attempt to modify a block controlled by another region.
*   **Issue Category:** Cross-Region Access, Non-Atomic Operations, Incorrect assumptions about execution context.
*   **Example:**
    ```java
    // Plugin in Region A
    Location loc = new Location(world, 100, 64, 200); // Assume this is in Region B
    Block block = world.getBlockAt(loc); // Accesses block in Region B
    if (block.getType() == Material.AIR) {
        // Time gap: Region B's thread might change the block type here
        block.setType(Material.STONE); // Unsafe cross-region modification
    }
    ```
*   **Potential Issue:**
    1.  Directly calling `block.setType()` on a block belonging to another region is a clear cross-region write and likely to be unsafe/disallowed by Folia.
    2.  Even if `world.getBlockAt()` provides a snapshot of the block's state, the subsequent `setType()` call is an operation that Folia must handle correctly, potentially by queueing it to the appropriate region's tick. Plugins might assume immediate synchronous execution.
    3.  The read (`getType`) and write (`setType`) are not atomic. Another thread (owning the block's region) could change the block between these two calls.
*   **Folia Consideration:** Folia will likely require block modifications to be scheduled with the region owning the block. Calls from other regions or the global thread might be queued or throw exceptions.
*   **Priority:** High.

#### **`Location` constructors and methods (`add`, `subtract`, `setX`, `setY`, `setZ`)**

*   **Description:** `Location` objects themselves are mutable. If a `Location` object is shared across threads (e.g., stored in a static field, passed between plugin event handlers that might run on different region threads), modifications by one thread can affect others.
*   **Issue Category:** Data race (if Location instance is shared and mutated).
*   **Example:**
    ```java
    // Potentially problematic if this 'sharedLocation' is accessed by multiple region threads
    static Location sharedLocation = new Location(Bukkit.getWorld("world"), 0, 0, 0);

    // In Region A thread
    sharedLocation.setX(100);

    // In Region B thread
    double y = sharedLocation.getY(); // Might get an inconsistent state if X, Y, Z are not updated atomically
                                    // or if another thread is also modifying it.
    ```
*   **Potential Issue:** While less of a direct API issue and more of a plugin development pitfall, the mutability of `Location` can lead to subtle bugs if instances are shared and modified concurrently without proper synchronization. This is a general Java thread-safety concern, but amplified in Folia if plugins are not careful about how they manage `Location` instances across potential region threads.
*   **Folia Consideration:** Folia itself won't change `Location`'s behavior, but plugins need to be more careful. Using immutable `Location` snapshots or careful copying is advisable.
*   **Priority:** Medium (more of a plugin practice issue, but important).

#### **`Chunk.load()` and `Chunk.unload()`**

*   **Description:** Forcing chunk loads/unloads.
*   **Issue Category:** Cross-Region Access, Incorrect assumptions about execution context.
*   **Potential Issue:** If a plugin running in one region attempts to load or unload a chunk that Folia has assigned to a different region, it could interfere with that region's management of its own chunks. It might also assume the load/unload is synchronous and immediately visible, which might not be true if the operation needs to be delegated to another region's thread or the main server thread.
*   **Folia Consideration:** Folia will likely need to intercept these calls and delegate them to the appropriate region or a global chunk management system. Plugins might experience delays or failures if they try to operate on chunks outside their designated regions or if the operation is asynchronous.
*   **Priority:** Medium.

#### **`Block.getState()` followed by methods on the returned `BlockState` and then `BlockState.update()`**

*   **Description:** Getting the state of a block (e.g., a chest's inventory), modifying it, and then applying the update.
*   **Issue Category:** Non-Atomic Operations, Stale data reads, Cross-Region Access.
*   **Example:**
    ```java
    Block block = world.getBlockAt(someLocation);
    if (block.getState() instanceof Chest) {
        Chest chestState = (Chest) block.getState(); // Read 1
        // Time gap: another thread/region might modify the chest's inventory
        chestState.getInventory().addItem(new ItemStack(Material.DIAMOND)); // Modify
        chestState.update(); // Write back - might overwrite other changes or apply to stale data
    }
    ```
*   **Potential Issue:**
    1.  The read of `BlockState` and the `update()` call are not atomic. Another thread (e.g., the region thread owning the block, or another plugin) could modify the block or its state between the `getState()` and `update()` calls. This could lead to lost updates or inconsistent states.
    2.  If `someLocation` is in a different region, `getState()` might provide a snapshot, but `update()` would be a cross-region write.
*   **Folia Consideration:** `BlockState.update()` will likely need to be a region-aware operation, potentially queueing the update to the correct region. Plugins need to be aware that the state they read might be stale by the time `update()` is called if they don't own the region.
*   **Priority:** High.

---

### 2. `org.bukkit.entity.Entity` and its sub-interfaces (`Player`, `LivingEntity`, `Monster`, etc.)

#### **`Entity.getLocation()`, `Entity.getVelocity()`, `Entity.teleport(Location)`, `Entity.setVelocity(Vector)`**

*   **Description:** Accessing or modifying an entity's position, motion, or world. Entities can move between regions.
*   **Issue Category:** Cross-Region Access, Stale data reads, Data race (if internal entity data is not synchronized).
*   **Example:**
    ```java
    // Plugin in Region A, entity currently in Region A
    Entity entity = somePlayer.getTargetEntity(5); // Could be null or an entity in Region B
    if (entity != null) {
        Location loc = entity.getLocation(); // Potentially reads data from Region A's thread
        // Entity might move to Region B due to its own AI or physics
        loc.setX(loc.getX() + 10);
        entity.teleport(loc); // This might be a cross-region teleport if entity moved,
                              // or if loc is in another region.
    }
    ```
*   **Potential Issue:**
    1.  **`getLocation()`/`getVelocity()`**: If an entity is owned by Region A, a plugin in Region B calling `getLocation()` might get stale data if the entity is actively moving within Region A. Folia needs to ensure that data is read safely, possibly by providing a snapshot or by ensuring the read is synchronized with the entity's owning region thread.
    2.  **`teleport(Location)`**:
        *   If the target location is in a different region, this is a cross-region operation. The entity's data needs to be safely transferred from its current region's thread to the target region's thread.
        *   If a plugin teleports an entity it *thinks* it controls, but the entity has just crossed a region boundary and is now controlled by another thread, the teleport call needs to be rerouted.
        *   Plugins might assume teleports are instantaneous and that subsequent calls (e.g., `getLocation()`) reflect the new position immediately. This might not hold if the teleport involves a region handoff.
    3.  **`setVelocity(Vector)`**: Similar to teleport, modifying velocity should ideally be done by the region thread owning the entity. Cross-thread calls need to be managed.
*   **Folia Consideration:**
    *   Entity state access (location, velocity, health, etc.) will likely require thread-safe mechanisms, potentially involving queued tasks to the owning region's thread for modifications or providing immutable snapshots for reads from other threads/regions.
    *   Teleportation across regions is a complex operation that Folia must manage carefully, ensuring data consistency and proper handoff of entity control.
*   **Priority:** High.

#### **`Entity.getNearbyEntities(double x, double y, double z)`**

*   **Description:** Gets entities within a given bounding box around an entity. This box can easily span multiple regions.
*   **Issue Category:** Cross-Region Access, Stale data reads.
*   **Example:**
    ```java
    // Entity is in Region A, near the border of Region B
    List<Entity> nearby = entity.getNearbyEntities(50, 10, 50); // Radius might extend into Region B
    for (Entity n : nearby) {
        // n could be an entity from Region B
        // Operations on 'n' might be cross-region
    }
    ```
*   **Potential Issue:** The returned list might contain entities from multiple regions. Iterating this list and accessing/modifying these entities can lead to cross-region calls. The list itself needs to be assembled by querying multiple regions, which can be complex and needs to be done safely to avoid inconsistencies if entities are moving or changing state during the query.
*   **Folia Consideration:** Folia will need a mechanism to perform a scatter-gather operation across relevant regions to build this list. The returned entities should probably be snapshots or proxies if accessed from a thread different from their owning region.
*   **Priority:** High.

#### **`Entity.remove()` / `Player.kickPlayer(String reason)`**

*   **Description:** Removing or kicking an entity/player.
*   **Issue Category:** Cross-Region Access, Incorrect assumptions about execution context.
*   **Potential Issue:** If a plugin in Region A tries to remove an entity that is currently owned/ticking in Region B (e.g., it just crossed a border), the removal command needs to be safely routed to Region B. Assumptions of immediate removal might be incorrect. Kicking a player is a global operation but might be initiated from a region thread; ensuring this is handled correctly without races (e.g., player logs out while kick is processed) is important.
*   **Folia Consideration:** Entity removal and player kicks should be operations that are either globally managed or safely delegated to the entity's current owning region.
*   **Priority:** Medium.

#### **Accessing Entity-Specific Data (e.g., `Player.getInventory()`, `LivingEntity.getHealth()`, `Entity.getCustomName()`)**

*   **Description:** Reading or modifying specific data of an entity.
*   **Issue Category:** Stale data reads, Data race (if not properly synchronized by Folia), Cross-Region Access (for modifications).
*   **Example:**
    ```java
    // Plugin in Region A, player in Region A
    Player player = Bukkit.getPlayer("somePlayer");
    if (player != null) {
        int health = player.getHealth(); // Read
        // Player might take damage in their own region thread here
        player.setHealth(health - 5); // Write - needs to be on owning region's thread
    }
    ```
*   **Potential Issue:**
    *   Reads (`getHealth`, `getInventory().getItem()`) from a different thread than the one ticking the entity might get stale data if not synchronized.
    *   Writes (`setHealth`, `getInventory().addItem()`) must be executed on the entity's owning region thread. Direct modification from another thread is unsafe.
*   **Folia Consideration:** Folia must ensure that reads are either from snapshots or appropriately synchronized. Writes must be queued to the owning region thread. This is fundamental to per-region entity ticking.
*   **Priority:** High.

#### **`Entity.isValid()`**

*   **Description:** Checks if the entity is still valid (i.e., hasn't been removed).
*   **Issue Category:** Stale data reads.
*   **Potential Issue:** An entity might be valid when checked, but then removed by its owning region thread before the calling thread can perform an operation on it. This is a classic check-then-act race condition.
*   **Folia Consideration:** While `isValid()` might give the current status, the status can change immediately after the call if the check is from a different thread. Plugins should be defensive. Folia might make cross-thread/cross-region entity references more robust, e.g., by returning a "proxy" that becomes inert if the actual entity is removed.
*   **Priority:** Medium.

---

### 3. `org.bukkit.event.*` (Common Events like `PlayerEvent`, `BlockEvent`, `EntityEvent`)

Events are a primary way plugins interact with the server. Folia's threading model has significant implications for event handling.

#### **Event Execution Thread**

*   **Description:** Plugins traditionally assume most events (especially world-related ones like `BlockBreakEvent`, `PlayerInteractEvent`) are fired on the main server thread. In Folia, these events will likely be fired on the thread of the region where the event occurs.
*   **Issue Category:** Incorrect assumptions about execution context.
*   **Potential Issue:**
    *   Plugins that access global data structures from an event handler without synchronization may cause data races if that event is now fired on a region thread.
    *   Plugins that expect to perform operations on entities or blocks outside the event's immediate context (e.g., modifying a block in a different, far-away chunk, or accessing another player's inventory) might inadvertently perform cross-region access.
    *   If an event handler itself tries to call Bukkit API methods that are not thread-safe or are blocking, it could stall the region thread.
*   **Folia Consideration:** This is a fundamental change. Folia will fire region-specific events on region threads. Events that are inherently global (e.g., `ServerListPingEvent`) might still run on a global thread. Plugins must be re-evaluated for thread safety in their event handlers.
*   **Priority:** High.

#### **Modifying Event Data (e.g., `event.setCancelled(true)`, `event.setDamage(double)`)**

*   **Description:** Event handlers often modify event parameters or outcomes.
*   **Issue Category:** Generally safe if the event and its data are confined to the firing thread (region thread). Becomes an issue if the event object or its contained data is accessed by another thread concurrently.
*   **Potential Issue:** If an event object is somehow passed to and modified by another thread while the original firing thread (region thread) is still processing it or relying on its state, inconsistencies can occur. This is less likely with standard event handling but could happen with custom event systems or improper sharing of event objects.
*   **Folia Consideration:** Standard event modification should be safe as long as the event is processed primarily by the region thread it's fired on. The main concern is what API calls the plugin *makes* within the handler.
*   **Priority:** Medium (primarily related to actions *within* handlers rather than event modification itself).

#### **Accessing Entities/Blocks/Locations from Events (e.g., `PlayerMoveEvent.getTo()`, `BlockBreakEvent.getBlock()`)**

*   **Description:** Events provide direct references to entities, blocks, locations, etc., involved in the event.
*   **Issue Category:** Stale data reads, Cross-Region Access (if the handler tries to operate outside the event's immediate scope).
*   **Example:**
    ```java
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // event.getPlayer() is the player in this region
        // event.getTo() is the new location in this region (or potentially a border crossing)
        Block blockBelow = event.getTo().clone().subtract(0, 1, 0).getBlock(); // Usually in the same region

        // Problematic if 'anotherWorld' or 'farLocation' are in different regions
        // Block farBlock = Bukkit.getWorld("anotherWorld").getBlockAt(farLocation);
        // farBlock.setType(Material.GOLD); // Cross-region modification
    }
    ```
*   **Potential Issue:**
    *   The event itself and its immediate data (e.g., `event.getBlock()`) are contextually relevant to the region firing the event. Accessing these should be safe from that region's thread.
    *   However, if the event handler uses these objects to then query or modify *other* parts of the world (e.g., a block far away, entities in a different loaded chunk that might be in another region), it risks cross-region access.
    *   If `event.getTo()` for a `PlayerMoveEvent` indicates a player is crossing into another region, operations on the player after this event might need to be handed off or queued.
*   **Folia Consideration:** Event handlers will run on region threads. Access to event-related objects (player, block) should be for the "local" context. Any operations that go outside this immediate context need to be aware they might be cross-region. Folia might provide region IDs with events or make API calls within handlers inherently region-aware.
*   **Priority:** High.

#### **Event Handler Registration/Unregistration (`PluginManager.registerEvents()`, `HandlerList.unregisterAll()`)**

*   **Description:** Managing event listeners.
*   **Issue Category:** Shared Mutable State (the internal lists of handlers).
*   **Potential Issue:** If plugins register or unregister events asynchronously while the `PluginManager` is iterating over handler lists to fire an event, it could lead to `ConcurrentModificationException` or missed/extra event calls. This is a known issue even in traditional Spigot if done incautiously, but multiple region threads firing events could exacerbate it if the handler lists are not properly synchronized.
*   **Folia Consideration:** `PluginManager`'s internal data structures for event handlers must be thread-safe. This is likely already handled in PaperMC and Spigot, but its importance is magnified. Folia would need to ensure this remains robust.
*   **Priority:** Medium (assuming this is largely handled by existing server infrastructure, but worth verifying).

---

### 4. `org.bukkit.scheduler.BukkitScheduler` and Task Management

The BukkitScheduler is traditionally used to execute code on the main server thread, or asynchronously. Folia's model introduces new execution contexts (region threads).

#### **`runTask(Plugin, Runnable)`, `runTaskLater(Plugin, Runnable, long)`, `runTaskTimer(Plugin, Runnable, long, long)`**

*   **Description:** These schedule tasks to run "synchronously" with the server's main tick.
*   **Issue Category:** Incorrect assumptions about execution context.
*   **Potential Issue:** Plugins assume these tasks run on *the* main server thread. In Folia, "sync" tasks might need to be context-aware:
        *   If scheduled from a region thread, should it run on that same region's next tick?
        *   If scheduled from a global context, or if the task relates to a specific entity/location, which thread should it run on?
        *   A task scheduled by Plugin A that modifies data also touched by Plugin B's region-thread event handler could cause races if not run on the correct thread or synchronized.
*   **Folia Consideration:** Folia needs to redefine what "synchronous" means. It will likely offer:
        *   **Global Sync Tasks:** Run on a main global server thread (for operations that are truly global).
        *   **Region Sync Tasks:** Run on the tick of a specific region (e.g., `entity.getScheduler().runTask(...)` would run on the region thread for that entity).
        *   Plugins calling `Bukkit.getScheduler().runTask(...)` might default to a global sync task, or it might be disallowed/discouraged in favor of region-specific schedulers. Ambiguity here is dangerous.
*   **Priority:** High.

#### **`runTaskAsynchronously(Plugin, Runnable)`, `runTaskLaterAsynchronously(Plugin, Runnable, long)`, `runTaskTimerAsynchronously(Plugin, Runnable, long, long)`**

*   **Description:** These schedule tasks to run on separate threads from the server's thread pool.
*   **Issue Category:** Incorrect assumptions about execution context, Data race, Cross-Region Access.
*   **Potential Issue:**
        *   Async tasks already carry the burden of thread safety; plugins using them *must not* access Bukkit API (that isn't explicitly thread-safe) without scheduling a sync task back.
        *   In Folia, an async task that calls Bukkit API methods might inadvertently target a region-specific structure without being on the correct region thread, leading to data corruption or errors. For example, getting a block and setting its type from an async task is unsafe.
        *   If an async task tries to operate on an entity, it must ensure that any subsequent synchronous operations are scheduled to that entity's *specific region thread*, not just any "main thread".
*   **Folia Consideration:** The existing advice for async tasks (don't call API directly, schedule back to sync) becomes even more critical. The "sync" part now needs to be region-aware. Folia might provide new scheduler methods like `runTaskOnRegion(Location, Runnable)` or `entity.getScheduler().runTask(...)` to ensure tasks requiring access to specific game elements run on the correct region thread.
*   **Priority:** High.

#### **`callSyncMethod(Plugin, Callable<T>)`**

*   **Description:** Executes a `Callable` on the main server thread and returns a `Future`.
*   **Issue Category:** Incorrect assumptions about execution context, Potential for deadlock.
*   **Potential Issue:** If called from a region thread, and the "main server thread" is busy or is the *same* as the region thread (unlikely, but consider single-region Folia for a moment), it could lead to deadlocks or unexpected behavior. The definition of "main server thread" is key. If it's a global thread, then a region thread calling this and waiting for the `Future` could stall its region.
*   **Folia Consideration:** The utility of `callSyncMethod` might diminish or change. If it means "run on global main thread," then it's a cross-thread invocation. If Folia has multiple region threads, it's less clear which thread this should target by default. It might be deprecated or replaced by region-specific versions.
*   **Priority:** Medium.

#### **`BukkitTask.cancel()` and `isCancelled()`**

*   **Description:** Managing scheduled tasks.
*   **Issue Category:** Shared Mutable State (the scheduler's task lists).
*   **Potential Issue:** Task cancellation needs to be thread-safe. If one thread (e.g., a region thread) tries to cancel a task while the scheduler (on another thread, or even the same) is processing its task queues, this could lead to issues if not internally synchronized.
*   **Folia Consideration:** The internal mechanics of the scheduler must be robustly thread-safe, especially with tasks potentially being scheduled from and executing across various global and regional threads. This is likely already a concern in Paper/Spigot but is stressed further by Folia.
*   **Priority:** Medium.

---

### 5. `org.bukkit.inventory.Inventory` and Item Manipulation

Inventories can belong to players (who can move between regions), blocks (like chests, tied to a location), or be custom (no direct world association).

#### **Accessing Player Inventories (`Player.getInventory()`, `Player.getOpenInventory()`)**

*   **Description:** Getting a player's inventory or the inventory they currently have open.
*   **Issue Category:** Cross-Region Access, Stale data reads, Data race.
*   **Potential Issue:**
        *   A player is an entity and can move between regions. If Plugin A (in Region A) gets `player.getInventory()` while the player is in Region A, and then the player moves to Region B, subsequent operations by Plugin A on that inventory object are cross-region and potentially unsafe if not proxied or queued.
        *   The `Inventory` object itself (and `ItemStack`s within it) might not be thread-safe. If the player's region thread is modifying the inventory (e.g., player picks up an item) while another thread (e.g., global task, another region's plugin) is reading or modifying it, data races can occur.
*   **Folia Consideration:** Access to a player's inventory should ideally always be routed through the player entity's current region thread. Reads might provide snapshots/copies of items. Modifications must be queued to the owning region thread. `Player.getOpenInventory()` adds another layer, as the top-level inventory might be a block inventory in a different region.
*   **Priority:** High.

#### **Accessing Block Inventories (e.g., `((Chest) block.getState()).getInventory()`)**

*   **Description:** Getting the inventory of a block like a chest or furnace.
*   **Issue Category:** Cross-Region Access (if block is in another region), Stale data reads, Non-Atomic Operations.
*   **Potential Issue:**
        *   If `block.getState()` is called for a block in another region, the returned `BlockState` and its inventory might be a snapshot. Modifying this snapshot inventory and calling `state.update()` is a cross-region write with atomicity concerns (see `BlockState.update()` section).
        *   Directly modifying the inventory object obtained from a block in another region is unsafe.
*   **Folia Consideration:** Similar to `BlockState.update()`, all operations on block inventories must be delegated to the region owning the block. Reads from other regions should provide immutable snapshots.
*   **Priority:** High.

#### **`Inventory.getItem(int slot)`, `Inventory.setItem(int slot, ItemStack item)`, `Inventory.addItem(ItemStack... items)` etc.**

*   **Description:** Direct manipulation of inventory contents.
*   **Issue Category:** Data race, Cross-Region Access (if the inventory is associated with an entity/block in another region).
*   **Example:**
    ```java
    // inventory might be from a Player (Region B) or a Chest (Region C)
    // Current code in Plugin (Region A)
    ItemStack item = inventory.getItem(0); // Read
    if (item != null) {
        item.setAmount(item.getAmount() - 1); // Modify ItemStack (problematic if ItemStack is live reference)
        inventory.setItem(0, item); // Write
    }
    ```
*   **Potential Issue:**
        *   If the `inventory` object is live and being accessed by its owning thread (e.g., player's region thread processing a pickup) while another thread calls `setItem`, a data race can occur.
        *   `ItemStack` objects are mutable. Getting an `ItemStack` from an inventory, modifying it, and then expecting those changes to be reflected without an explicit `setItem` is not guaranteed and is bad practice. If the `ItemStack` is a direct reference, modifications are visible immediately, which can be problematic if another thread is also interacting with it. If it's a copy, changes won't be saved without `setItem`. This ambiguity is dangerous in concurrent scenarios.
        *   These operations are not atomic. `getItem`, modify, `setItem` can be interleaved.
*   **Folia Consideration:**
    *   All direct inventory modifications must be executed on the thread owning the inventory (player's region, block's region).
    *   `getItem` from a foreign inventory should return a deep copy of the `ItemStack` to prevent unsafe modifications of shared mutable objects.
    *   `setItem` and `addItem` to foreign inventories must be queued operations.
*   **Priority:** High.

#### **`InventoryView` (e.g., from `Player.getOpenInventory()`)**

*   **Description:** Represents the items a player is currently viewing, combining top and bottom inventories.
*   **Issue Category:** Stale data reads, Cross-Region Access.
*   **Potential Issue:** `InventoryView` methods like `getItem()`, `setItem()` can access slots in either the player's inventory or the opened inventory (e.g., a chest). If the player is in Region A and the chest is in Region B, operations on the `InventoryView` can span regions. Modifying the view needs to correctly delegate to the appropriate region for each slot.
*   **Folia Consideration:** Operations on `InventoryView` must be carefully managed. If the view spans regions (e.g. player inventory in region A, chest in region B), modifications need to be routed to the correct region for the respective part of the view. Reads should provide snapshots.
*   **Priority:** High.

#### **`ItemStack` mutability**

*   **Description:** `ItemStack` objects are mutable (e.g., `setAmount`, `setItemMeta`, `addEnchantment`).
*   **Issue Category:** Data race.
*   **Potential Issue:** If an `ItemStack` instance is obtained from an inventory slot and then modified, and this instance is shared or accessible by multiple threads (e.g., cached by a plugin, or if the inventory returns direct references rather than copies), concurrent modifications can lead to inconsistent item states. This is a general Java concurrency issue but is exacerbated if plugins are not expecting their `ItemStack` references to be handled by different threads.
*   **Folia Consideration:** Best practice is for APIs returning `ItemStack`s (especially from potentially foreign inventories) to return defensive copies. Plugins should also adopt the practice of treating received `ItemStack`s as immutable or immediately copying them if they intend to modify and store them long-term or across threads. Bukkit/Spigot/Paper often return copies, but this needs to be consistently ensured for cross-region access.
*   **Priority:** Medium (mitigated if APIs consistently return copies, but a common plugin error source).

---

### 6. `org.bukkit.plugin.PluginManager` and Plugin Interactions

The `PluginManager` handles plugin loading, enabling, disabling, and inter-plugin communication.

#### **`PluginManager.getPlugin(String name)` and `PluginManager.getPlugins()`**

*   **Description:** Accessing loaded plugin instances.
*   **Issue Category:** Shared Mutable State (plugin list).
*   **Potential Issue:** If plugins are loaded/unloaded/reloaded dynamically (though often discouraged or restricted during runtime), the list of plugins could change. If one thread is iterating `getPlugins()` while another thread is modifying this list (e.g., an admin command to load a plugin), `ConcurrentModificationException` could occur if the underlying collection is not thread-safe. Accessing a plugin that was just disabled could lead to errors.
*   **Folia Consideration:** The list of plugins is generally managed by a global server thread. Accessing plugin instances themselves is usually safe as they are typically stateless or manage their own state. The main risk is concurrent modification of the plugin list itself, which should be synchronized by the `PluginManager`. This is a general Spigot/Paper concern, not hugely specific to Folia's regional threading unless plugins try to load/unload others from region threads.
*   **Priority:** Low (dynamic plugin loading/unloading from arbitrary threads is rare and risky anyway).

#### **`PluginManager.callEvent(Event event)`**

*   **Description:** Allows a plugin to fire a custom event.
*   **Issue Category:** Incorrect assumptions about execution context.
*   **Potential Issue:** If a plugin fires an event from a region thread, other plugins handling this event will execute their handlers on that same region thread. If the event implies global state change or requires access to data outside that region, handlers must be careful. If a plugin fires an event from an asynchronous task, it bypasses normal Bukkit event handling flow (sync to main thread) which is dangerous.
*   **Folia Consideration:**
        *   If an event is called from a region thread, handlers run on that region thread. This is consistent with Folia's model for its own events.
        *   If `callEvent` is used from a global thread, handlers run on that global thread.
        *   The key is that `callEvent` is synchronous with the caller's thread. Plugins need to be aware of which thread they are on when calling it.
        *   Folia might need to introduce `callEvent(Event event, Location/Entity)` to target an event to a specific region's thread if it's being called from a global context but pertains to regional data.
*   **Priority:** Medium.

#### **Enabling/Disabling Plugins (`PluginManager.enablePlugin()`, `PluginManager.disablePlugin()`)**

*   **Description:** Changing the enabled state of plugins.
*   **Issue Category:** Shared Mutable State, Unintended side effects due to parallel execution.
*   **Potential Issue:** These are powerful operations, typically performed from a main server console/thread. If a plugin could trigger these from a region thread for another plugin:
        *   It could destabilize other plugins that are actively running in other regions or on global threads.
        *   `onEnable`/`onDisable` logic in plugins nearly always assumes it's running on a main thread and has exclusive access during this period.
*   **Folia Consideration:** These operations should almost certainly be restricted to a global server thread and not callable directly from region threads or by plugins on themselves/others in a regional context. This is more of a server operational safety concern.
*   **Priority:** Medium (as a risk, but likely to be restricted by design).

#### **Services Manager (`Server.getServicesManager()`)**

*   **Description:** Allows plugins to provide and consume services from other plugins.
*   **Issue Category:** Shared Mutable State (service registrations), Incorrect assumptions about execution context (if service methods are not thread-safe).
*   **Potential Issue:**
        *   Registration/unregistration of services needs to be thread-safe.
        *   If a plugin obtains a service from another plugin, it must understand the threading guarantees of that service's methods. If Service A is registered by Plugin A (which might be primarily designed for main-thread operation) and Plugin B on a region thread calls methods on Service A, those methods must be thread-safe.
*   **Folia Consideration:** The ServicesManager itself should ensure its registration map is thread-safe. However, the thread safety of the *service implementations* is the responsibility of the plugins providing them. With Folia, service providers need to be more explicit about whether their services can be safely called from region threads or if they require calls to be marshaled to a specific thread (global or the provider's "home" region, if applicable).
*   **Priority:** Medium.

---

### 7. `org.bukkit.Server` and Global Server State

The `Server` interface (`Bukkit.getServer()`) provides access to many global aspects of the server.

#### **`Server.getOnlinePlayers()`**

*   **Description:** Returns a collection of all online players.
*   **Issue Category:** Shared Mutable State, Stale data reads.
*   **Example:**
    ```java
    // Called from a region thread or global task
    for (Player player : Bukkit.getServer().getOnlinePlayers()) {
        // Player object might be from any region
        // player.teleport(someGlobalLocation); // Potentially cross-region, needs queueing
    }
    ```
*   **Potential Issue:** The underlying collection of players is constantly modified as players join and leave. Iterating over this collection requires it to be thread-safe (e.g., a snapshot or concurrent collection). Even with a snapshot, the `Player` objects obtained might be ticked on different region threads. Operations on these `Player` objects from a different thread (e.g., a global task iterating all players) must be region-aware and likely queue actions to the player's respective region thread.
*   **Folia Consideration:** `getOnlinePlayers()` will likely return an immutable snapshot. Plugins iterating this list and performing actions on players must use Folia-provided mechanisms to execute those actions on the correct region thread for each player (e.g., `player.getRegionScheduler().run(...)`). Direct calls like `player.teleport()` from a thread other than the player's own will be unsafe.
*   **Priority:** High.

#### **`Server.getPlayer(String name)` / `Server.getPlayer(UUID uuid)`**

*   **Description:** Gets a specific player.
*   **Issue Category:** Stale data reads (player might log off), Cross-Region Access (returned player is on a region thread).
*   **Potential Issue:** A plugin might get a `Player` object, but the player logs out immediately after. The reference becomes stale. If the calling code is on a different thread from the player's region thread, any operations on the player object are cross-thread and need proper handling.
*   **Folia Consideration:** Similar to `getOnlinePlayers()`, the returned `Player` object is managed by a region thread. Operations must be queued to that thread. The risk of the player logging out is always present; plugins should check `player.isOnline()`.
*   **Priority:** High.

#### **`Server.getWorlds()` / `Server.getWorld(String name)`**

*   **Description:** Accessing loaded worlds.
*   **Issue Category:** Shared Mutable State (list of worlds), Stale data reads.
*   **Potential Issue:** The list of worlds can change if worlds are loaded/unloaded. Iterating `getWorlds()` needs to be safe against concurrent modification (usually handled by current server implementations by returning a copy). Operations on the `World` objects themselves (which have their own regionalized concerns, see section 1) are the main issue.
*   **Folia Consideration:** Accessing the list of worlds itself is likely safe (returns a snapshot). Each `World` object obtained will have its own set of region threads (or be managed by specific regions). Interactions with these `World` objects must follow the rules for cross-region data access.
*   **Priority:** Medium (managing the list of worlds is less of an issue than interacting with the worlds themselves).

#### **`Server.broadcastMessage(String message)` / `Server.broadcast(String message, String permission)`**

*   **Description:** Sending messages to all players or those with permission.
*   **Issue Category:** Unintended side effects due to parallel execution (if not properly implemented by Folia).
*   **Potential Issue:** Broadcasting involves iterating over all players. This iteration needs to be thread-safe. Sending the message packet to each player should also be safe, typically handled by queuing to each player's network connection. If called from a region thread, it implies a global operation.
*   **Folia Consideration:** This is inherently a global operation. Folia needs to ensure that broadcasting can be called from any thread (global or regional) and that it safely iterates over players (likely using snapshots) and dispatches messages to each player via their respective region thread or a dedicated network thread.
*   **Priority:** Medium.

#### **Server-wide Settings (e.g., `Server.hasWhitelist()`, `Server.setWhitelist(boolean)`, `Server.getMaxPlayers()`)**

*   **Description:** Accessing or modifying global server configurations.
*   **Issue Category:** Shared Mutable State.
*   **Potential Issue:** These are global settings. If a region thread can modify `server.setWhitelist(true)` while another region thread is checking `server.hasWhitelist()`, there could be a race if the underlying access/modification is not synchronized.
*   **Folia Consideration:** All access and modification of global server settings must be intrinsically thread-safe, likely managed by a global server thread or using appropriate locks. Calls from region threads would need to go through this synchronized mechanism.
*   **Priority:** Medium.

#### **`Server.dispatchCommand(CommandSender sender, String commandLine)`**

*   **Description:** Executes a command as if typed by a sender.
*   **Issue Category:** Incorrect assumptions about execution context.
*   **Potential Issue:** If a plugin on a region thread dispatches a command, does the command execute on that region thread, or on a global thread, or on the target's thread (if it's like `/execute as @p run ...`)? Command execution often assumes main-thread context for safety.
*   **Folia Consideration:** Command dispatching logic will need to be carefully reviewed.
    *   If the `CommandSender` is a `Player`, the command should ideally be processed on that player's region thread.
    *   If the `CommandSender` is the console or a command block, it might run on a global thread or the command block's region thread.
    *   Plugins dispatching commands need to be aware that the command execution might not be on their current thread. Folia will need a clear contract for this.
*   **Priority:** High.

#### **Scoreboards (`Server.getScoreboardManager()`, `Scoreboard` methods)**

*   **Description:** Managing scoreboards and their objectives, teams, scores.
*   **Issue Category:** Shared Mutable State.
*   **Potential Issue:** Scoreboards are global data structures. If multiple threads (e.g., different region threads updating scores for their players, or a global thread managing a boss bar) modify the same scoreboard concurrently without internal synchronization, data corruption (e.g., lost updates, inconsistent team memberships) can occur.
*   **Folia Consideration:** The entire Scoreboard API (`ScoreboardManager`, `Scoreboard`, `Objective`, `Team`, `Score`) needs to be made thread-safe, or all operations must be queued to a specific "scoreboard owner thread" (likely a global thread). Given their frequent use, direct thread-safety with internal synchronization is preferable but complex. Simply queuing all scoreboard operations to the main thread is a common solution in Spigot derivatives. Folia needs to ensure this is robust.
*   **Priority:** High.

---
