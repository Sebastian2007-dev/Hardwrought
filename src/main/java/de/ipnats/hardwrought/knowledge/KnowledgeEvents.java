package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Section 82: the ways a player comes to know a thing by working with it.
 *
 * <p>Holding something is enough to discover it, and the knowledge system finds that out by looking
 * in the inventory. Studying is different: it has to be an act. Breaking rock with a pick teaches
 * the pick and the rock; crafting teaches what was made; eating teaches the food. Each of those is
 * hooked where it already happens rather than being asked for separately. Creatures are learned the
 * same way: a fight makes one's name real, and killing it is what studies it.
 */
public final class KnowledgeEvents {
    private static boolean initialized;

    private KnowledgeEvents() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;
            var runtime = CoreLifecycle.find(serverPlayer.level().getServer());
            if (runtime == null) return;
            // The rock is learned by breaking it, and the tool by what it did to the rock.
            runtime.knowledge().study(serverPlayer, state.getBlock());
            ItemStack tool = serverPlayer.getMainHandItem();
            if (!tool.isEmpty()) runtime.knowledge().study(serverPlayer, tool.getItem());
        });
        // A creature is met by trading blows with it, whichever of the two landed the blow.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
            if (entity instanceof ServerPlayer victim) {
                if (source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player)) {
                    var runtime = CoreLifecycle.find(victim.level().getServer());
                    if (runtime != null) runtime.knowledge().meet(victim, attacker.getType());
                }
            } else if (source.getEntity() instanceof ServerPlayer attacker) {
                var runtime = CoreLifecycle.find(attacker.level().getServer());
                if (runtime != null) runtime.knowledge().meet(attacker, entity.getType());
            }
        });
        // And it is studied by the one who kills it.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Player || !(source.getEntity() instanceof ServerPlayer killer)) return;
            var runtime = CoreLifecycle.find(killer.level().getServer());
            if (runtime != null) runtime.knowledge().slay(killer, entity.getType());
        });
    }
}
