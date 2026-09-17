package de.ipnats.hardwrought.core.events;

import de.ipnats.hardwrought.core.CoreRuntime;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Modules register their bounded simulation jobs and diagnostics before the first tick. */
public final class CoreEvents {
    public static final Event<Ready> SERVER_READY = EventFactory.createArrayBacked(Ready.class,
            listeners -> runtime -> {
                for (Ready listener : listeners) listener.onReady(runtime);
            });

    @FunctionalInterface
    public interface Ready {
        void onReady(CoreRuntime runtime);
    }

    private CoreEvents() { }
}
