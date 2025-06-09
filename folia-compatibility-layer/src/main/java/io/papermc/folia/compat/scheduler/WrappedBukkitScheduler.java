package io.papermc.folia.compat.scheduler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WrappedBukkitScheduler implements BukkitScheduler {
    private static final Logger LOGGER = Logger.getLogger(WrappedBukkitScheduler.class.getName());

    private final GlobalCompatibilityTickScheduler globalSyncScheduler;
    private final ExecutorService asyncTaskExecutor;
    // We need a reference to the plugin that owns this scheduler instance if we want to manage tasks on its behalf for cancellation
    // However, BukkitScheduler is typically a single server-wide instance.
    // So, tasks are associated with plugins, but the scheduler itself is global.

    public WrappedBukkitScheduler(GlobalCompatibilityTickScheduler globalSyncScheduler) {
        this.globalSyncScheduler = globalSyncScheduler;
        this.asyncTaskExecutor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("WrappedBukkitScheduler-Async-%d").setDaemon(true).build()
        );
    }

    // --- Synchronous Tasks ---
    // These will all run on the GlobalCompatibilityTickScheduler's single thread.

    @Override
    public BukkitTask runTask(Plugin plugin, Runnable task) throws IllegalArgumentException {
        return globalSyncScheduler.scheduleSync(this, plugin, task, 0, -1);
    }

    @Override
    public BukkitTask runTask(Plugin plugin, BukkitRunnable task) throws IllegalArgumentException {
        return runTask(plugin, (Runnable) task);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) throws IllegalArgumentException {
        return globalSyncScheduler.scheduleSync(this, plugin, task, delay, -1);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, BukkitRunnable task, long delay) throws IllegalArgumentException {
        return runTaskLater(plugin, (Runnable) task, delay);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) throws IllegalArgumentException {
        if (period <= 0) throw new IllegalArgumentException("Period must be positive");
        return globalSyncScheduler.scheduleSync(this, plugin, task, delay, period);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, BukkitRunnable task, long delay, long period) throws IllegalArgumentException {
        return runTaskTimer(plugin, (Runnable) task, delay, period);
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delay) {
        BukkitTask bukkitTask = runTaskLater(plugin, task, delay);
        return bukkitTask != null ? bukkitTask.getTaskId() : -1;
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task, long delay) {
        return scheduleSyncDelayedTask(plugin, (Runnable) task, delay);
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task) {
        BukkitTask bukkitTask = runTask(plugin, task);
        return bukkitTask != null ? bukkitTask.getTaskId() : -1;
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task) {
        return scheduleSyncDelayedTask(plugin, (Runnable) task);
    }

    @Override
    public int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        BukkitTask bukkitTask = runTaskTimer(plugin, task, delay, period);
        return bukkitTask != null ? bukkitTask.getTaskId() : -1;
    }

    @Override
    public int scheduleSyncRepeatingTask(Plugin plugin, BukkitRunnable task, long delay, long period) {
        return scheduleSyncRepeatingTask(plugin, (Runnable) task, delay, period);
    }

    // --- Asynchronous Tasks ---

    private BukkitTask runAsyncTask(Plugin plugin, Runnable task, long delay, long period) {
        final WrappedBukkitTask bukkitTask = new WrappedBukkitTask(this, plugin, task, false, period);
        Runnable executionWrapper = () -> {
            if (bukkitTask.isCancelled()) {
                performCancel(bukkitTask.getTaskId()); // Ensure it's fully removed if cancelled before first run
                return;
            }
            bukkitTask.setThread(Thread.currentThread());
            try {
                task.run();
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Async Task " + bukkitTask.getTaskId() + " for " + plugin.getName() + " generated an exception", e);
            } finally {
                bukkitTask.setThread(null);
                if (bukkitTask.getPeriod() < 0) { // Not repeating
                     performCancel(bukkitTask.getTaskId()); // Remove from tracking after one run
                }
            }
        };

        // For async tasks, we can use a ScheduledExecutorService or manage delays manually.
        // For simplicity with an ExecutorService, we'll use a CompletableFuture for delay.
        // A dedicated ScheduledExecutorService would be cleaner for async timed tasks.
        // For this example, let's assume async tasks are submitted to a cached thread pool
        // and delays/periods are handled by submitting new tasks. This is not ideal for timers.
        // A better async timer would use a ScheduledExecutorService.
        // Let's simplify: async timers are not perfectly implemented here, focusing on runTaskAsynchronously.

        if (delay > 0 || period > 0) {
            LOGGER.warning("Async timed/repeating tasks are not fully precise in this WrappedBukkitScheduler version. Using basic ExecutorService.");
            // This is a very simplified handling for delayed/repeating async.
            // A real implementation would use ScheduledExecutorService for async timers too.
            CompletableFuture.runAsync(() -> {
                if (delay > 0) {
                    try {
                        Thread.sleep(delay * 50); // Approximate tick delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (bukkitTask.isCancelled()) return;

                asyncTaskExecutor.submit(executionWrapper);

                if (period > 0) { // Very crude repeating
                    // This doesn't work well for real timers, as it keeps submitting.
                    // This part of Bukkit API is complex to replicate without a full scheduler.
                    LOGGER.warning("Async repeating task period handling is very basic for task " + bukkitTask.getTaskId());
                }

            }, asyncTaskExecutor);

        } else {
            asyncTaskExecutor.submit(executionWrapper);
        }
        // Need to add to a list for cancellation tracking if not done by global scheduler
        // For now, this is conceptual for async task management within WrappedBukkitScheduler
        // globalSyncScheduler.getActiveTasks().put(bukkitTask.getTaskId(), bukkitTask); // Incorrect for async
        // We need a separate list for async tasks if globalSyncScheduler only handles sync.
        // For now, cancellation of async tasks might be limited.
        return bukkitTask;
    }


    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) throws IllegalArgumentException {
        return runAsyncTask(plugin, task, 0, -1);
    }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, BukkitRunnable task) throws IllegalArgumentException {
        return runTaskAsynchronously(plugin, (Runnable) task);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) throws IllegalArgumentException {
        return runAsyncTask(plugin, task, delay, -1);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, BukkitRunnable task, long delay) throws IllegalArgumentException {
        return runTaskLaterAsynchronously(plugin, (Runnable) task, delay);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) throws IllegalArgumentException {
        if (period <= 0) throw new IllegalArgumentException("Period must be positive");
        return runAsyncTask(plugin, task, delay, period);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, BukkitRunnable task, long delay, long period) throws IllegalArgumentException {
        return runTaskTimerAsynchronously(plugin, (Runnable) task, delay, period);
    }


    // --- Callable and Cancellation ---
    @Override
    public <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> task) {
        return globalSyncScheduler.submitCallable(plugin, task);
    }

    @Override
    public void cancelTask(int taskId) {
        // This should check both sync and async task lists if they were separate.
        // For now, assume globalSyncScheduler handles all cancellable tasks it knows about.
        globalSyncScheduler.cancelTask(taskId);
        // If async tasks were managed separately, would cancel here too.
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        // Iterate over tasks and cancel those owned by the plugin.
        // Needs access to the task list from GlobalCompatibilityTickScheduler.
        globalSyncScheduler.getActiveTasks().values().stream()
            .filter(t -> t.getOwner().equals(plugin))
            .forEach(WrappedBukkitTask::cancel); // This calls WrappedBukkitTask.cancel -> performCancel
    }

    protected void performCancel(int taskId) {
        // This method is called by WrappedBukkitTask.cancel()
        globalSyncScheduler.cancelTask(taskId);
        // Also handle async task cancellation if managed separately
    }


    @Override
    public boolean isCurrentlyRunning(int taskId) {
        return globalSyncScheduler.isCurrentlyRunning(taskId);
        // + async check
    }

    @Override
    public boolean isQueued(int taskId) {
        return globalSyncScheduler.isQueued(taskId);
        // + async check
    }

    @Override
    public List<BukkitWorker> getActiveWorkers() {
        // This is for more advanced async task management (BukkitWorker)
        return new ArrayList<>(); // Placeholder
    }

    @Override
    public List<BukkitTask> getPendingTasks() {
        return new ArrayList<>(globalSyncScheduler.getActiveTasks().values().stream()
            .filter(t -> !t.isCancelled()) // Could also check if !isCurrentlyRunning
            .collect(Collectors.toList()));
        // + async tasks
    }

    public void shutdown() {
        LOGGER.info("WrappedBukkitScheduler shutting down...");
        asyncTaskExecutor.shutdown();
        try {
            if (!asyncTaskExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                asyncTaskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncTaskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // GlobalCompatibilityTickScheduler is shut down separately by the server, not by this scheduler.
        LOGGER.info("WrappedBukkitScheduler's async executor shut down.");
    }
}
