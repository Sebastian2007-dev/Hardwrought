package de.ipnats.hardwrought.core.simulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/** One instance per logical server. Tasks must do bounded work, never scan entire worlds. */
public final class SimulationScheduler {
    private final de.ipnats.hardwrought.core.utilities.ThreadOwnership ownership =
            new de.ipnats.hardwrought.core.utilities.ThreadOwnership();
    public record Profile(String id, SimulationTier tier, long calls, long failures,
                          long totalNanos, long maxNanos, boolean disabled) {
        public double meanMicros() {
            return calls == 0 ? 0 : totalNanos / (double) calls / 1_000;
        }
    }

    private static final class Task {
        final String id;
        final SimulationTier tier;
        final Runnable action;
        long calls, failures, totalNanos, maxNanos;
        boolean disabled;

        Task(String id, SimulationTier tier, Runnable action) {
            this.id = id;
            this.tier = tier;
            this.action = action;
        }
    }

    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private final LongSupplier clock;
    private final BiConsumer<String, RuntimeException> onFailure;
    private long ticks;
    private boolean started;

    public SimulationScheduler(long ticks, LongSupplier clock, BiConsumer<String, RuntimeException> onFailure) {
        if (ticks < 0) throw new IllegalArgumentException("Negative simulation clock");
        this.ticks = ticks;
        this.clock = clock;
        this.onFailure = onFailure;
    }

    public void register(String id, SimulationTier tier, Runnable action) {
        ownership.require();
        if (started) throw new IllegalStateException("Register tasks before the first server tick");
        if (id == null || id.isBlank() || tier == null || action == null) {
            throw new IllegalArgumentException("Task id, tier and action are required");
        }
        if (tasks.putIfAbsent(id, new Task(id, tier, action)) != null) {
            throw new IllegalArgumentException("Duplicate simulation task: " + id);
        }
    }

    public void tick() {
        ownership.require();
        started = true;
        ticks = Math.incrementExact(ticks);
        for (Task task : tasks.values()) {
            if (task.disabled || ticks % task.tier.interval() != 0) continue;
            long start = clock.getAsLong();
            try {
                task.action.run();
            } catch (RuntimeException exception) {
                // A faulty optional system must not crash every tick or flood the log.
                task.failures++;
                task.disabled = true;
                onFailure.accept(task.id, exception);
            } finally {
                long elapsed = Math.max(0, clock.getAsLong() - start);
                task.calls++;
                task.totalNanos += elapsed;
                task.maxNanos = Math.max(task.maxNanos, elapsed);
            }
        }
    }

    public long ticks() {
        ownership.require();
        return ticks;
    }

    public List<Profile> profiles() {
        ownership.require();
        List<Profile> result = new ArrayList<>();
        for (Task task : tasks.values()) {
            result.add(new Profile(task.id, task.tier, task.calls, task.failures,
                    task.totalNanos, task.maxNanos, task.disabled));
        }
        return List.copyOf(result);
    }

    public void resetProfiles() {
        ownership.require();
        for (Task task : tasks.values()) {
            task.calls = task.failures = task.totalNanos = task.maxNanos = 0;
        }
    }
}
