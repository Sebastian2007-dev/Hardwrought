package de.ipnats.hardwrought.machinery;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.Locale;

/**
 * Section 92: an engineer reads a line by looking at it. Crouching and using an empty hand on any
 * turning part tells how fast it runs and how much of its line's strength is taken.
 */
public final class KineticReadout {
    private static boolean initialized;

    private KineticReadout() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
                return InteractionResult.PASS;
            }
            var pos = hit.getBlockPos();
            if (!(level.getBlockState(pos).getBlock() instanceof KineticBlock)) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            player.sendOverlayMessage(describe(Kinetics.solve(level, pos), pos));
            return InteractionResult.SUCCESS;
        });
    }

    static Component describe(Kinetics.Network network, net.minecraft.core.BlockPos pos) {
        if (network == null) return Component.empty();
        String load = String.format(Locale.ROOT, "%.0f", network.stress());
        String capacity = String.format(Locale.ROOT, "%.0f", network.capacity());
        return switch (network.status()) {
            case STILL -> Component.translatable("message.hardwrought.kinetic.still");
            case JAMMED -> Component.translatable("message.hardwrought.kinetic.jammed");
            case OVERSTRESSED -> Component.translatable("message.hardwrought.kinetic.overstressed", load, capacity);
            case RUNNING -> Component.translatable("message.hardwrought.kinetic.running",
                    String.format(Locale.ROOT, "%.1f", Math.abs(network.speedAt(pos))), load, capacity);
        };
    }
}
