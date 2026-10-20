package de.ipnats.hardwrought.party;

import de.ipnats.hardwrought.core.CoreRuntime;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The hooks parties need in the world: no harm between members unless their leader allows it, word of
 * a member's death, and of them coming and going. The rest is in {@link PartySystem}.
 */
public final class Parties {
    private Parties() { }

    public static void initialize() {
        PartyCommands.initialize();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            if (!(victim instanceof ServerPlayer player)) return true;
            Player attacker = owner(source.getEntity());
            if (attacker == null || attacker == victim) return true;
            PartySystem parties = system(player);
            return parties == null || !parties.shielded(attacker.getUUID(), player.getUUID());
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            PartySystem parties = system(player);
            if (parties != null) parties.died(player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CoreRuntime runtime = CoreLifecycle.find(server);
            if (runtime != null) runtime.party().joined(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CoreRuntime runtime = CoreLifecycle.find(server);
            if (runtime != null) runtime.party().disconnected(handler.player);
        });
    }

    /**
     * Whether two players are in one party. Safe to ask from anywhere on the server thread; false
     * before the server's services are up.
     */
    public static boolean together(Player a, Player b) {
        if (a == b) return true;
        if (!(a instanceof ServerPlayer player)) return false;
        PartySystem parties = system(player);
        return parties != null && parties.together(a.getUUID(), b.getUUID());
    }

    /** The player behind a blow: the striker, the archer of an arrow, the master of a wolf. */
    private static Player owner(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof OwnableEntity owned && owned.getOwner() instanceof Player player) return player;
        return null;
    }

    private static PartySystem system(ServerPlayer player) {
        CoreRuntime runtime = CoreLifecycle.find(player.level().getServer());
        return runtime == null ? null : runtime.party();
    }
}
