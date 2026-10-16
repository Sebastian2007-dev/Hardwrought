package de.ipnats.hardwrought.client.fun;

import de.ipnats.hardwrought.fun.StampiEasterEgg;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Has the makers' skins ready before anyone calls one up ({@link StampiEasterEgg}), so he joins in his own
 * skin straight away rather than as Steve while it is still being fetched from Mojang.
 *
 * <p>Fetched when a world is joined, and looked up again every few minutes to keep it in the skin and
 * profile caches, which forget what has not been asked for in five and ten minutes. A failed fetch is
 * cached as well, so after one the skins are left alone long enough for both caches to forget it, and
 * then fetched afresh.
 */
public final class StampiSkins {
    /** Within the five minutes the skin cache keeps an entry it is asked for. */
    private static final int KEEP_WARM_TICKS = 4 * 60 * 20;
    /** Past the ten minutes the profile cache keeps a failed fetch. */
    private static final int RETRY_TICKS = 11 * 60 * 20;

    private static final List<ResolvableProfile> PROFILES = List.of(
            ResolvableProfile.createUnresolved(StampiEasterEgg.CRAFT),
            ResolvableProfile.createUnresolved(StampiEasterEgg.GAMES));

    private static int untilNext;
    private static final AtomicBoolean failed = new AtomicBoolean();

    private StampiSkins() { }

    public static void initialize() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> untilNext = 0);
        ClientTickEvents.END_CLIENT_TICK.register(StampiSkins::tick);
    }

    private static void tick(Minecraft client) {
        if (client.level == null || --untilNext > 0) return;
        failed.set(false);
        CompletableFuture<?>[] lookups = PROFILES.stream()
                .map(profile -> client.playerSkinRenderCache().lookup(profile).thenAccept(info -> {
                    // A profile found without its textures is a fetch that timed out, as much as none found.
                    if (info.isEmpty() || !info.get().gameProfile().properties().containsKey("textures")) {
                        failed.set(true);
                    }
                }))
                .toArray(CompletableFuture[]::new);
        untilNext = KEEP_WARM_TICKS;
        CompletableFuture.allOf(lookups).thenRun(() -> client.execute(() -> {
            if (failed.get()) untilNext = RETRY_TICKS;
        }));
    }
}
