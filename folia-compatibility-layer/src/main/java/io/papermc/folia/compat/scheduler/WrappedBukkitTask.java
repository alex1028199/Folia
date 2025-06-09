package io.papermc.folia.compat.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WrappedBukkitTask implements BukkitTask {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

    private final int taskId;
    private final Plugin owner;
    private final boolean sync;
    private final Runnable runnable; // Can be null if it's just a placeholder for a Future from Callable
    private volatile Thread thread; // The thread executing the task, if applicable

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final WrappedBukkitScheduler scheduler; // For notifying on cancellation

    // For repeating tasks
    private final long period; // -1 for non-repeating, 0 for runNextTick (effectively non-repeating after first run)
    private volatile long nextRunTick; // Conceptual tick for next execution

    // Constructor for runnables
    public WrappedBukkitTask(WrappedBukkitScheduler scheduler, Plugin owner, Runnable runnable, boolean sync, long period) {
        this.taskId = NEXT_ID.getAndIncrement();
        this.owner = owner;
        this.runnable = runnable;
        this.sync = sync;
        this.scheduler = scheduler;
        this.period = period;
    }

    // Constructor for Callables (where runnable might be null or a wrapper)
    public WrappedBukkitTask(WrappedBukkitScheduler scheduler, Plugin owner, boolean sync) {
        this.taskId = NEXT_ID.getAndIncrement();
        this.owner = owner;
        this.runnable = null; // Runnable is not directly here, managed by Future
        this.sync = sync;
        this.scheduler = scheduler;
        this.period = -1; // Callables are not typically repeating in BukkitScheduler public API
    }


    @Override
    public int getTaskId() {
        return taskId;
    }

    @Override
    public Plugin getOwner() {
        return owner;
    }

    @Override
    public boolean isSync() {
        return sync;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            if (scheduler != null) {
                scheduler.performCancel(this.taskId);
            }
        }
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public Thread getThread() {
        return thread;
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public long getPeriod() {
        return period;
    }

    public long getNextRunTick() {
        return nextRunTick;
    }

    public void setNextRunTick(long nextRunTick) {
        this.nextRunTick = nextRunTick;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WrappedBukkitTask that = (WrappedBukkitTask) o;
        return taskId == that.taskId;
    }

    @Override
    public int hashCode() {
        return taskId;
    }
}
