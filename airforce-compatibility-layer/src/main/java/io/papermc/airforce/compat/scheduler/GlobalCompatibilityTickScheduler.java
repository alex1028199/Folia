package io.papermc.airforce.compat.scheduler;

import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GlobalCompatibilityTickScheduler {
    private static final Logger LOGGER = Logger.getLogger(GlobalCompatibilityTickScheduler.class.getName());
    private static final long TICK_PERIOD_MS = 50; // 20 ticks per second

    private final ScheduledExecutorService executor;
    private final ExecutorService singleThreadExecutorForImmediateSync; // For non-delayed sync tasks
    private final Map<Integer, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();
    private final Map<Integer, WrappedBukkitTask> activeTasks = new ConcurrentHashMap<>();
    private final AtomicLong currentTick = new AtomicLong(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);


    public GlobalCompatibilityTickScheduler() {
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GlobalCompatScheduler-TickThread");
            t.setDaemon(true);
            return t;
        });
        this.singleThreadExecutorForImmediateSync = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GlobalCompatScheduler-ImmediateSync");
            t.setDaemon(true);
            return t;
        });

        // Start a conceptual tick advancement task
        this.executor.scheduleAtFixedRate(() -> {
            if (!isShuttingDown.get()) {
                currentTick.incrementAndGet();
                // In a more complex system, this is where we would iterate over tasks
                // and run those whose `nextRunTick` has arrived.
                // However, ScheduledExecutorService handles the timing for us.
            }
        }, TICK_PERIOD_MS, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    public <T> Future<T> submitCallable(Plugin plugin, Callable<T> callable) {
        if (isShuttingDown.get()) {
            LOGGER.warning("Scheduler shutting down, not submitting callable from plugin " + plugin.getName());
            return CompletableFuture.failedFuture(new RejectedExecutionException("Scheduler is shutting down."));
        }
        // Callables in Bukkit are always "run next tick" effectively
        return singleThreadExecutorForImmediateSync.submit(() -> {
            try {
                return callable.call();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing callable for plugin " + plugin.getName(), e);
                throw e;
            }
        });
    }

    public WrappedBukkitTask scheduleSync(WrappedBukkitScheduler mainScheduler, Plugin owner, Runnable task, long delayTicks, long periodTicks) {
        if (isShuttingDown.get()) {
            LOGGER.warning("Scheduler shutting down, not scheduling task from plugin " + owner.getName());
            return null;
        }

        WrappedBukkitTask bukkitTask = new WrappedBukkitTask(mainScheduler, owner, task, true, periodTicks);
        activeTasks.put(bukkitTask.getTaskId(), bukkitTask);

        Runnable executionWrapper = () -> {
            if (bukkitTask.isCancelled() || isShuttingDown.get()) {
                removeTask(bukkitTask.getTaskId());
                return;
            }
            bukkitTask.setThread(Thread.currentThread());
            try {
                if (task != null) task.run();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Task " + bukkitTask.getTaskId() + " generated an exception", e);
            } finally {
                bukkitTask.setThread(null);
                if (bukkitTask.getPeriod() < 0) { // Not repeating
                    removeTask(bukkitTask.getTaskId());
                } else if (bukkitTask.getPeriod() == 0) { // Run next tick then done (effectively non-repeating after first)
                     // This case should be handled by periodTicks < 0 for non-repeating
                     // if periodTicks == 0, it means run ASAP and repeat ASAP (effectively a busy loop, bad)
                     // Bukkit interprets period 0 for runTaskTimer as run every tick.
                     // For scheduleSyncRepeatingTask, period 0 is invalid (must be >0).
                     // For simplicity here, period 0 in WrappedBukkitTask means run every tick if it's a timer.
                     // If it was a runTaskLater with delay and period 0, it becomes non-repeating.
                     // This logic is simplified; Bukkit API has specific interpretations.
                    removeTask(bukkitTask.getTaskId());
                }
            }
        };

        ScheduledFuture<?> future;
        if (periodTicks > 0) { // Repeating task
            future = executor.scheduleAtFixedRate(executionWrapper, delayTicks * TICK_PERIOD_MS, periodTicks * TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
        } else if (delayTicks > 0) { // Delayed non-repeating task
            future = executor.schedule(executionWrapper, delayTicks * TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
        } else { // Immediate (or "next tick") non-repeating task
            // Use the immediate executor for tasks with no delay
            singleThreadExecutorForImmediateSync.submit(executionWrapper);
            future = CompletableFuture.completedFuture(null); // Placeholder, not really used for cancellation like this
        }
        scheduledFutures.put(bukkitTask.getTaskId(), future);
        return bukkitTask;
    }


    public void cancelTask(int taskId) {
        WrappedBukkitTask task = activeTasks.get(taskId);
        if (task != null) {
            task.cancel(); // Mark as cancelled in BukkitTask object
        }
        ScheduledFuture<?> future = scheduledFutures.remove(taskId);
        if (future != null) {
            future.cancel(false); // false: don't interrupt if running, just prevent future runs
        }
        activeTasks.remove(taskId); // Remove from active tracking
        LOGGER.fine("Cancelled task " + taskId);
    }

    private void removeTask(int taskId) {
        activeTasks.remove(taskId);
        scheduledFutures.remove(taskId);
    }

    public boolean isCurrentlyRunning(int taskId) {
        WrappedBukkitTask task = activeTasks.get(taskId);
        return task != null && task.getThread() != null && task.getThread().isAlive();
    }

    public boolean isQueued(int taskId) {
        return activeTasks.containsKey(taskId) && !isCurrentlyRunning(taskId);
    }

    public void shutdown() {
        if (isShuttingDown.compareAndSet(false, true)) {
            LOGGER.info("GlobalCompatibilityTickScheduler shutting down...");
            executor.shutdown();
            singleThreadExecutorForImmediateSync.shutdown();
            try {
                if (!executor.awaitTermination(TICK_PERIOD_MS * 2, TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
                if (!singleThreadExecutorForImmediateSync.awaitTermination(TICK_PERIOD_MS * 2, TimeUnit.MILLISECONDS)) {
                    singleThreadExecutorForImmediateSync.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                singleThreadExecutorForImmediateSync.shutdownNow();
                Thread.currentThread().interrupt();
            }
            activeTasks.clear();
            scheduledFutures.clear();
            LOGGER.info("GlobalCompatibilityTickScheduler shut down.");
        }
    }

    public Map<Integer, WrappedBukkitTask> getActiveTasks() {
        return new HashMap<>(activeTasks); // Return a copy
    }
}
