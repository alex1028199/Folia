# Spigot-Folia Compatibility Layer Architecture

## 1. Introduction

This document outlines the proposed architecture for a compatibility layer designed to allow existing Spigot plugins to run on the Folia server software with minimal modification. Folia's multi-region architecture introduces significant changes to threading and data access, which the traditional Spigot API was not designed for. This layer aims to bridge that gap.

The previous analysis (`spigot_api_thread_safety_analysis.md`) identified numerous areas where Spigot API calls could lead to thread-safety issues in Folia. This architecture directly addresses those concerns.

## 2. Core Principles

The design of the compatibility layer is guided by the following core principles:

*   **Transparency (Maximize):** For plugin developers, interaction with the Bukkit API should feel as close to vanilla Spigot as possible. The layer should ideally hide the complexity of Folia's regionalization unless absolutely necessary.
*   **Safety (Prioritize):** The primary goal is to prevent thread-safety issues like data races, deadlocks, and stale reads. The layer must ensure that API calls are executed in a way that is compatible with Folia's threading model.
*   **Region Awareness (Internal):** The compatibility layer itself must be deeply region-aware. It needs to identify the context of an API call (which region, if any, it pertains to) and act accordingly.
*   **Delegation (Prefer):** Operations that modify region-specific data (e.g., block changes, entity updates) must be delegated to the appropriate Folia region thread for execution.
*   **Snapshotting & Immutability (Utilize):** For read operations on data that might change, or for objects passed across thread boundaries (like `ItemStack`s or event data), the layer should prefer providing immutable snapshots or defensive copies to prevent unintended side effects from shared mutable state.
*   **Error Reporting (Clear):** When an operation cannot be safely performed or is inherently incompatible, the layer should provide clear error messages or warnings to the plugin developer or server administrator, rather than failing silently or causing obscure issues.
*   **Performance (Consider):** While safety is paramount, the layer should strive to minimize performance overhead. This will be a key consideration in choosing implementation mechanisms (e.g., bytecode manipulation vs. object wrapping).

## 3. Architectural Components

The compatibility layer will consist of several key components:

---

### 3.1. API Interceptors / Wrappers (Shim Layer)

*   **Mechanism:** A combination approach is likely best:
    *   **Bytecode Instrumentation (e.g., using Javassist or ASM):** For transparently intercepting calls to existing Bukkit/Spigot classes (e.g., `org.bukkit.World.getBlockAt()`). This allows modification of behavior without changing the plugin's bytecode directly and is crucial for transparently handling final classes or methods.
    *   **Wrapper Objects / Facades:** For Bukkit interfaces or non-final classes, the servercould provide custom implementations that wrap Folia's native objects. For example, when a plugin calls `player.getWorld()`, it might receive a `WorldWrapper` that internally delegates calls to the actual Folia world object, adding necessary safety checks and thread delegation.
    *   **Static Replacement:** For some static methods in the Bukkit API (e.g., `Bukkit.getScheduler()`), the compatibility layer might replace the classes providing these methods with its own implementations at classloading time.

*   **Key Responsibilities:**
    *   **Call Interception:** Detect when a plugin makes a call to a relevant Bukkit/Spigot API method.
    *   **Context Identification:** Determine the context of the call:
        *   Is it from a plugin thread, a global Folia thread, or a specific region thread?
        *   Does the call target a specific location, entity, or world? (e.g., `block.setType()` implies the block's location).
    *   **Safety Check:** Based on the method and context, determine if the call is safe to proceed directly, needs delegation, requires data snapshotting, or should be disallowed.
    *   **Delegation Initiation:** If an operation needs to run on a specific region thread, pass it to the "Region Task Delegator / Scheduler."
    *   **Result Marshalling:** When a delegated task completes, ensure its result is safely returned to the plugin, potentially wrapping returned objects (e.g., Folia entities) in Bukkit API proxies.
    *   **API Behavior Replication:** For methods that Folia might handle differently (e.g., an API that used to block might now be asynchronous), the shim layer may need to simulate the original behavior if plugins rely heavily on it, or clearly document the change.

---

### 3.2. Region Task Delegator / Scheduler

*   **Purpose:** This component is responsible for taking a piece of work (e.g., a lambda, a `Runnable`, or a `Callable` representing an API call) identified by the Shim Layer and ensuring it executes on the correct Folia region thread. It also handles cases where an operation might need to execute on a global server thread.

*   **Interface Ideas:**
    *   `Future<T> runOnRegion<T>(Location location, Callable<T> task)`
    *   `void runOnRegion(Location location, Runnable task)`
    *   `Future<T> runOnEntityRegion<T>(Entity entity, Callable<T> task)`
    *   `void runOnEntityRegion(Entity entity, Runnable task)`
    *   `Future<T> runOnGlobalThread<T>(Callable<T> task)` (for Bukkit API calls that are inherently global and safe)
    *   `void runOnGlobalThread(Runnable task)`

*   **Interaction with Folia's Native Schedulers:**
    *   This delegator will be a layer on top of Folia's own scheduling mechanisms.
    *   When `runOnRegion(Location, ...)` is called, it will use Folia's API to determine the region associated with the `Location`.
    *   It will then use Folia's equivalent of `region.getScheduler().execute(task)` or `region.getScheduler().call(task)` to schedule the work.
    *   It needs to handle the `Future` object returned by Folia's scheduler and make it available to the compatibility layer, which might, in turn, need to block and wait for this `Future` if the original Spigot call was synchronous. This is a critical point for maintaining API call semantics.
    *   For operations that don't have a specific location (e.g., some `Server` methods), it might default to a global Folia thread or a designated primary region thread if applicable.

*   **Key Responsibilities:**
    *   Determining the target execution context (specific region thread, global thread).
    *   Scheduling the task using Folia's native scheduling primitives.
    *   Managing `Future` objects for synchronous calls, potentially blocking the calling plugin thread until execution is complete. This is vital for mimicking synchronous Bukkit API behavior but also a potential performance bottleneck and source of deadlocks if not handled carefully (e.g., a region thread waiting for another region thread that's also waiting).
    *   Propagating exceptions from the task execution back to the original caller.

---

### 3.3. Thread-Safe Bukkit Object Proxies/Facades

*   **Purpose:** When a plugin obtains a Bukkit object (like `Player`, `Entity`, `World`, `Block`, `ItemStack`), it often receives a proxy or facade object instead of a direct reference to Folia's internal representation. These proxies ensure that interactions with the object are thread-safe and region-aware.

*   **Behavior for Read/Write Operations:**
    *   **Read Operations (e.g., `player.getName()`, `block.getType()`, `itemStack.getAmount()`):**
        *   If the data is considered immutable or globally consistent (e.g., `player.getUniqueId()`), the proxy might return it directly.
        *   If the data is region-specific and the calling thread is the owning region's thread, the call might be allowed to pass through to the underlying Folia object.
        *   If the data is region-specific and the calling thread is *not* the owning region's thread:
            *   The proxy may return a cached or snapshotted value (if stale data is acceptable for that API).
            *   The proxy may delegate the read to the correct region thread via the "Region Task Delegator" and block for the result. This ensures data consistency but has performance implications.
            *   For collections (e.g., `world.getEntities()`), it should return an immutable snapshot.
    *   **Write Operations (e.g., `player.setHealth()`, `block.setType()`, `itemStack.setAmount()`):**
        *   Almost all write operations on region-specific objects (World, Block, Entity, Player inventories) **must** be delegated to the object's owning region thread via the "Region Task Delegator." The proxy will initiate this delegation.
        *   If the original Bukkit API implies synchronous behavior, the calling thread will be blocked until the modification is complete on the region thread.
        *   Modifications to `ItemStack` proxies might modify a copy, requiring an explicit `inventory.setItem()` to persist, or they might be disallowed entirely on `ItemStack`s obtained from "foreign" inventories/entities, forcing plugins to use `inventory.setItem()`.

*   **Cross-Region Identification:**
    *   Proxies for objects like `Entity` or `Player` must retain information about which Folia region currently "owns" or ticks that object. This is crucial for the "Region Task Delegator."
    *   For `Block` or `Location` proxies, the world and coordinates directly provide the means to identify the region.
    *   `World` proxies would inherently know which world they represent, and Folia would map this to its regional data structures.

*   **Immutability Enforcement (e.g., for ItemStacks):**
    *   When a plugin gets an `ItemStack` from an inventory (especially one potentially in another region or a shared context like an event), the proxy layer should, by default, provide an immutable `ItemStack` facade or a deep copy.
    *   This prevents plugins from modifying an `ItemStack` instance that might be a direct reference used by another thread or the server itself.
    *   If a plugin attempts to call a mutating method on such an ItemStack (e.g., `itemStack.setAmount()`), the proxy can either:
        *   Throw an `UnsupportedOperationException`.
        *   Silently do nothing (less desirable as it hides errors).
        *   Operate on a copy, making it clear that the change isn't reflected in the original inventory slot until an `inventory.setItem()` call.
    *   This forces plugins to use proper inventory methods (`setItem`, `addItem`) for changes, which can then be correctly delegated by the inventory's proxy.

---

### 3.4. Event Bus Adapter

*   **Purpose:** Manages the firing and handling of Bukkit events in a way that is compatible with Folia's threading model and Spigot plugin expectations.

*   **Event Object Handling:**
    *   **Immutability/Copies:** When Folia fires an event that will be passed to Spigot plugins, the Event Bus Adapter should ensure that the event object itself, and any mutable objects it contains (e.g., `List<Entity>`, `ItemStack`), are immutable snapshots or defensive copies. This prevents plugin handlers running on different threads (or a Spigot plugin handler interfering with Folia's internal event processing) from causing issues with shared mutable state.
        *   For example, if `PlayerMoveEvent.getTo()` returns a `Location`, this `Location` should be a copy.
        *   If `EntityDeathEvent.getDrops()` returns a `List<ItemStack>`, this list and the `ItemStack`s within should be copies.
    *   **Proxying Event Data:** Entities, blocks, worlds, etc., within an event (e.g., `event.getPlayer()`, `event.getBlock()`) should be wrapped in the Thread-Safe Bukkit Object Proxies/Facades described earlier.

*   **Ensuring Correct Thread for Handlers:**
    *   **Folia-Originated Events:** Folia will likely fire events on the region thread where they occur (e.g., `BlockBreakEvent` on the region thread for that block's location). The Event Bus Adapter will pass these to Spigot plugin listeners. Spigot listeners will thus execute on that Folia region thread. This is a fundamental shift from traditional Spigot where most such events run on the main server thread. Plugins need to be coded defensively (the compatibility layer cannot magically make non-thread-safe handlers safe, but it can provide safe data).
    *   **Global Events:** Events that are inherently global (e.g., `ServerListPingEvent`) might be fired by Folia on a main/global thread. Spigot handlers would then also run on this thread.
    *   The adapter must clearly define and adhere to this contract.

*   **Handling `PluginManager.callEvent()` from Plugins:**
    *   If a Spigot plugin calls `Bukkit.getPluginManager().callEvent(someEvent)`:
        *   **Context:** The adapter needs to identify the thread calling `callEvent`.
        *   **If called from a Folia region thread:** The event handlers will be executed synchronously on that *same region thread*. This is consistent with how Folia itself would fire a region-specific event.
        *   **If called from a Folia global thread:** Handlers run on that global thread.
        *   **If called from an arbitrary plugin-created async thread:** This is problematic. Traditional Spigot `callEvent` from async is often unsafe. The adapter might:
            *   Log a warning.
            *   Attempt to schedule the event processing onto an appropriate main/global thread (similar to how `BukkitScheduler.runTask` works for API calls from async tasks). This could change the event's synchronicity from the caller's perspective.
            *   Disallow it by throwing an exception.
        *   The event object passed by the plugin should also be wrapped/snapshotted before being passed to handlers if it contains mutable data, to protect handlers from each other.

---

### 3.5. BukkitScheduler Adapter

*   **Purpose:** To provide a `org.bukkit.scheduler.BukkitScheduler` implementation that behaves predictably for plugins while ensuring tasks execute on appropriate Folia threads. This component will replace the standard Bukkit/Spigot scheduler.

*   **Interpreting Sync and Async Tasks:**
    *   **`runTask(Plugin, Runnable)` (and its variants `runTaskLater`, `runTaskTimer`):**
        *   Traditionally, these run on the main server thread.
        *   In Folia, the adapter needs to determine the "context" for this sync task if possible.
        *   **Option 1 (Simplest):** All such tasks are scheduled on a designated "Global Main Thread" in Folia. This is safest for compatibility but means region-specific operations within these tasks will still incur cross-thread overhead via API interceptors.
        *   **Option 2 (More Advanced):** Introduce new Folia-aware API extensions that plugins *could* use, e.g., `runTaskAt(Plugin, Location, Runnable)`. For the standard `runTask`, if the plugin code is currently executing on a Folia region thread (e.g., within an event handler), the adapter could schedule the sync task onto that *same region thread's next tick*. If called from an async task or global thread, it defaults to the Global Main Thread.
        *   **`callSyncMethod`**: This would execute the `Callable` on the Global Main Thread and return a `Future`.
    *   **`runTaskAsynchronously(Plugin, Runnable)` (and its variants):**
        *   These tasks are intended to run on a separate thread pool, off the main server/region ticks.
        *   The BukkitScheduler Adapter can delegate these to a generic Folia worker thread pool.
        *   **Crucially:** Any Bukkit API calls made from within these async tasks *must* still go through the API Interceptors. If an API call needs to run on a region thread or global main thread, the interceptor will use the Region Task Delegator to schedule it back appropriately and block the async task's thread for the result. This maintains the existing contract that Bukkit API is not safe to call directly from async tasks.

*   **Region-Aware Task Execution:**
    *   The adapter itself doesn't make tasks region-aware directly, but it ensures that when a "sync" task executes (wherever that may be - Global Main Thread or a specific region thread if Option 2 above is implemented for tasks scheduled from region contexts), any Bukkit API calls made *within* that task are correctly handled by the API Interceptors and Proxies.
    *   For example, if a `runTask` (executing on Global Main Thread) calls `block.setType()`, the `Block` proxy will delegate this to the correct region thread.
    *   If Folia offers its own explicit region-bound schedulers (e.g., `entity.getScheduler().runTask()`), the compatibility layer should encourage advanced plugins to use these directly for better performance where possible, perhaps by exposing them through the Proxies/Facades (e.g., `((PlayerProxy)player).getFoliaScheduler().runTask(...)`).

*   **Task Cancellation (`BukkitTask.cancel()`):**
    *   The adapter needs to manage `BukkitTask` objects and allow their cancellation. This requires thread-safe tracking of scheduled tasks and interaction with Folia's underlying scheduler to cancel the corresponding native task.

---

### 3.6. Configuration Module

*   **Purpose:** To provide server administrators with options to fine-tune the behavior of the compatibility layer, potentially on a per-plugin basis. This can help balance compatibility with performance or stricter safety models.

*   **Potential Options:**
    *   **Global Strictness Level:**
        *   `STRICT`: Prioritize safety above all. Force snapshotting, disallow potentially risky operations, heavy use of delegation. Might have performance costs.
        *   `COMPATIBLE` (Default): Aim for maximum Spigot compatibility, making reasonable assumptions about plugin behavior but still implementing necessary safety via delegation and snapshotting.
        *   `PERMISSIVE`: Relax some checks for trusted plugins if specific performance bottlenecks are identified. This would carry risks and should be used with extreme caution.
    *   **Per-Plugin Overrides:** Allow specifying a different strictness level or specific behaviors for individual plugins.
    *   **Logging Verbosity:** Control how much information the compatibility layer logs about its actions (e.g., delegated calls, warnings about risky operations).
    *   **API Behavior Toggles (Use Sparingly):**
        *   For specific Bukkit APIs known to cause issues or have ambiguous interpretations in a concurrent environment, offer toggles. For example, whether `someCollection.iterator()` returns a fail-fast iterator (detecting concurrent modification from other threads) or a snapshot iterator.
        *   Whether certain asynchronous operations performed by plugins should be automatically marshalled to a main thread or a region-specific thread.
    *   **Timeout Settings:** Configure timeouts for delegated tasks that are expected to return a `Future`, to prevent indefinite blocking of plugin threads.
    *   **Heuristics for Task Placement:** For Bukkit `runTask` calls that don't have explicit regional context, allow configuration of whether they default to the global main thread or attempt to infer a region (e.g., if the plugin is known to mostly interact with a single region).

*   **Implementation:**
    *   Could be a simple YAML configuration file.
    *   The compatibility layer components would query this module at startup and potentially at runtime (if dynamic reloading of configuration is supported, though this adds complexity).

---

### 3.7. Global State Access Controller

*   **Purpose:** To manage access to truly global server state that is not inherently tied to a specific world or region, ensuring thread safety. This includes things like the server-wide player list, scoreboards, server MOTD, whitelist, etc.

*   **Mechanism:**
    *   **Internal Locking/Synchronization:** For mutable global state (e.g., main scoreboard, server settings like whitelist), this controller will use appropriate Java concurrency primitives (locks, synchronized blocks, concurrent collections) to ensure that reads and writes are atomic and consistent, regardless of whether they are initiated from a region thread or a global thread.
    *   **Delegation to Global Thread:** For complex operations on global state that should not be performed directly on a region thread (or to avoid exposing complex locking to many call sites), the controller can use the "Region Task Delegator" to schedule the work onto a designated "Global Main Thread."
        *   Example: `Server.setWhitelist(boolean value)` might be queued to the Global Main Thread.
        *   Example: Modifying complex scoreboard objectives might be queued.
    *   **Snapshotting for Reads:** For frequently read global data (e.g., `Server.getOnlinePlayers()`, `Server.getWorlds()`), the controller, in conjunction with the API Interceptors/Proxies, will ensure that calling threads (especially region threads) receive immutable snapshots to avoid issues with concurrent modification during iteration. The creation of these snapshots will be synchronized by the controller.

*   **Key Responsibilities:**
    *   Acting as the gatekeeper for all Bukkit API calls that read or modify global, non-regional server state.
    *   Ensuring that modifications to this state are atomic and visible across all threads.
    *   Providing safe, often snapshotted, data for read operations.
    *   Coordinating with the `BukkitScheduler Adapter` if certain global operations need to be deferred to the main server tick (Global Main Thread).
    *   Example interactions:
        *   `Bukkit.getOnlinePlayers()`: The controller ensures a thread-safe, snapshotted collection is returned. The `Player` objects within are then proxied.
        *   `Bukkit.getServer().getMainScoreboard()`: Returns a proxy `Scoreboard` object. Operations on this proxy (e.g., `objective.getScore(entry).setScore()`) are then internally managed by the controller, possibly by queuing updates to a global thread or using fine-grained locks for specific scoreboard elements.
        *   `Bukkit.setWhitelist(true)`: The API interceptor for this call would route it through the Global State Access Controller, which would then likely schedule this modification to occur on the Global Main Thread.

---

## 4. Illustrative Interaction Flows

This section provides examples of how common API calls would be processed by the compatibility layer.

### Example 1: `Block.setType(Material)` (called from a plugin thread, e.g., command executor)

1.  **Plugin Code:** `myBlock.setType(Material.STONE);`
    *   `myBlock` is a `Thread-Safe Bukkit Object Proxy` for a `Block`.
    *   Assume this code is running on a thread that is *not* the Folia region thread for `myBlock`'s location (e.g., a command handler thread, or a BukkitRunnable scheduled to the global main thread).

2.  **API Interceptor / Proxy (`BlockProxy.setType()`):**
    *   The `setType()` method in `BlockProxy` is invoked.
    *   It identifies that `setType()` is a world-modifying operation.
    *   It retrieves the `Location` of the block from its internal state.
    *   It determines that the current thread is not the owner of this block's region.

3.  **Region Task Delegator / Scheduler:**
    *   `BlockProxy` calls `RegionTaskDelegator.runOnRegion(blockLocation, () -> { /* actual setType logic */ })`.
    *   The Delegator uses `blockLocation` to identify the correct Folia region (e.g., Region X).
    *   It schedules a task with Folia's scheduler for Region X to execute the lambda: `foliaRegionX.getScheduler().execute(() -> { underlyingFoliaBlock.setType(Material.STONE); });`
    *   Since `Block.setType()` in Bukkit is a `void` (synchronous) method, the `runOnRegion` call (or rather, the Future it returns) must be waited upon. The plugin thread that called `myBlock.setType()` blocks.

4.  **Execution on Region Thread:**
    *   On Region X's next tick, Folia's scheduler executes the task.
    *   The actual Folia block object's `setType` method is called. This is now happening on the correct, region-safe thread.
    *   Any internal Folia events related to block changes are fired on Region X's thread.

5.  **Return / Unblocking:**
    *   Once the task on Region X completes, the `Future` held by the Region Task Delegator (and waited upon by the plugin thread) is completed.
    *   The plugin thread unblocks and the `myBlock.setType()` call returns.

### Example 2: `Player.teleport(Location)` (called from a plugin thread)

1.  **Plugin Code:** `somePlayer.teleport(targetLocation);`
    *   `somePlayer` is a `PlayerProxy`. `targetLocation` could be in the same region, a different region, or even a different world.
    *   Assume the calling thread is not `somePlayer`'s current region thread.

2.  **API Interceptor / Proxy (`PlayerProxy.teleport()`):**
    *   The `teleport()` method in `PlayerProxy` is invoked.
    *   It identifies this as a significant entity state change.
    *   It retrieves the `Entity` object (the player) it's proxying.

3.  **Region Task Delegator / Scheduler:**
    *   `PlayerProxy` calls `RegionTaskDelegator.runOnEntityRegion(playerEntity, () -> { /* actual teleport logic */ })`.
    *   The Delegator determines `playerEntity`'s current region (e.g., Region A).
    *   It schedules a task with Folia's scheduler for Region A: `foliaRegionA.getScheduler().execute(() -> { underlyingFoliaPlayer.teleport(targetLocation); });`
        *   **Note:** The actual teleport logic within Folia might be complex. If `targetLocation` is in Region B, Folia's `underlyingFoliaPlayer.teleport()` will handle the cross-region handoff (removing from Region A's ticking, adding to Region B's ticking, updating player data structures, etc.). This internal Folia complexity is ideally hidden from the Spigot plugin.
    *   `Player.teleport()` in Bukkit is synchronous. The plugin thread blocks, waiting for the teleport task to complete.

4.  **Execution on Region Thread(s):**
    *   The task begins execution on Region A's thread.
    *   Folia's internal teleport logic takes over. This might involve coordination with `targetLocation`'s region (Region B) and potentially a global player management system. The entity's data is safely transferred.
    *   Events like `PlayerTeleportEvent` would be fired. The Event Bus Adapter ensures these are correctly processed (see component 3.4). If the teleport event is cancelled by a Spigot plugin, this cancellation needs to be communicated back to the Folia teleport logic.

5.  **Return / Unblocking:**
    *   Once Folia confirms the teleport is complete (or has failed but processed), the task on Region A (or wherever the final responsibility lies) completes.
    *   The `Future` is completed, and the plugin thread unblocks. The `somePlayer.teleport()` call returns. The `PlayerProxy` for `somePlayer` might also need its internal state updated if, for example, its owning region has changed.

---

## 5. Key Challenges / Open Questions

*   **Performance Overhead:**
    *   Widespread use of proxies, bytecode instrumentation, and task delegation (especially if it involves blocking for Futures) can introduce performance overhead. Profiling will be essential to identify and optimize critical paths.
    *   The cost of snapshotting collections or game objects frequently (e.g., in events or for API reads from foreign threads) needs to be balanced against safety.
*   **Complexity of Bytecode Instrumentation:**
    *   Reliably instrumenting a wide range of Bukkit/Spigot API calls is complex and error-prone. Thorough testing is required to ensure correctness and avoid unintended side effects.
    *   Maintaining these instrumentations across different Spigot/Minecraft versions can be challenging.
*   **Achieving True Synchronicity vs. Emulated Synchronicity:**
    *   Many Bukkit APIs are synchronous. If an operation is delegated to another thread, the calling plugin thread must block. This can lead to:
        *   Reduced server parallelism if plugin threads are frequently blocked.
        *   Potential for deadlocks if not carefully managed (e.g., Plugin on Thread A calls API X, which blocks waiting for Region B; meanwhile, code on Region B calls an API that tries to schedule to Thread A or waits on a lock held by Thread A). Careful design of locking and task dependencies is critical.
    *   Deciding when it's acceptable to make a call effectively asynchronous (returning a `Future` directly to the plugin) vs. strictly emulating synchronicity is a major design decision. The latter is better for transparency but worse for performance/complexity.
*   **ItemStack Mutability Handling:**
    *   The strategy for `ItemStack` (always copy, immutable facades, etc.) needs to be robust and consistently applied. Mutable `ItemStack`s obtained from API calls are a common source of plugin bugs even without regional threading.
*   **Eventual Consistency vs. Strict Consistency for Reads:**
    *   For some read operations (e.g., `player.getLocation()` from a foreign thread), is it acceptable to return slightly stale (but quickly obtainable) data, or must it always delegate to the owning region for the absolute latest data? This will be configurable via the Configuration Module to some extent, but defaults need careful consideration.
*   **Plugin ClassLoader Isolation and API Versioning:**
    *   Ensuring that the compatibility layer's classes (and any dependencies like ASM/Javassist) are isolated from plugin classloaders and don't conflict.
    *   Handling plugins built against different Bukkit API versions might require careful API surface management in the proxy/interceptor layer.
*   **Testing Complexity:**
    *   Testing the compatibility layer thoroughly with a wide range of existing Spigot plugins, under various load conditions and concurrency scenarios, will be a significant undertaking.
*   **Interaction with Folia-Native Plugins:**
    *   How does this layer interact if a "Spigot" plugin tries to communicate directly with a "Folia-native" plugin that is already region-aware? This is likely outside the direct scope of initial compatibility but a long-term consideration.
*   **Debugging and Diagnostics:**
    *   Providing good debugging tools and diagnostic information for plugin developers when things go wrong due to threading or delegation issues will be important. Stack traces might become more complex due to delegation.

---
