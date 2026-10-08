package de.ipnats.hardwrought.magic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spells that land a moment after they are cast: a bolt in flight, a circle about to strike. Kept
 * in memory only; a server stopping mid-flight simply loses the spell.
 */
final class MagicTasks {
    private record Task(ServerLevel level, long due, Runnable action) { }

    private static final List<Task> TASKS = new ArrayList<>();

    private MagicTasks() { }

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TASKS.isEmpty()) return;
            List<Task> due = new ArrayList<>();
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
                Task task = it.next();
                if (task.level().getServer() != server) continue;
                if (task.level().getGameTime() >= task.due()) {
                    it.remove();
                    due.add(task);
                }
            }
            // Run after the sweep, since an action may itself schedule another.
            for (Task task : due) task.action().run();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> TASKS.removeIf(task -> task.level().getServer() == server));
    }

    static void later(ServerLevel level, int ticks, Runnable action) {
        if (ticks <= 0) {
            action.run();
            return;
        }
        TASKS.add(new Task(level, level.getGameTime() + ticks, action));
    }
}
