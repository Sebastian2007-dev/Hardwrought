package de.ipnats.hardwrought.client.fun;

import de.ipnats.hardwrought.nether.AshGrove;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;

/**
 * Water in the Ash Grove is always boiled ({@link AshGrove}), and looks it: steam rises off every open
 * surface of it near the player, and now and then it bubbles.
 */
public final class AshGroveSteam {
    /** How far around the player steaming water is looked for, and how many cells a tick. */
    private static final int RADIUS = 10;
    private static final int SAMPLES = 24;

    private AshGroveSteam() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(AshGroveSteam::tick);
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null || client.isPaused()) return;
        BlockPos around = client.player.blockPosition();
        if (!AshGrove.boils(level, around)) return;
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SAMPLES; i++) {
            pos.set(around.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS,
                    around.getY() + random.nextInt(9) - 4,
                    around.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS);
            if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getBlockState(pos.above()).isAir()) continue;
            if (!AshGrove.boils(level, pos)) continue;
            double x = pos.getX() + random.nextDouble(), y = pos.getY() + 0.95, z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0, 0.04 + random.nextDouble() * 0.03, 0);
            if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0, 0.02, 0);
            if (random.nextInt(200) == 0) {
                level.playLocalSound(x, y, z, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.AMBIENT, 0.3f, 0.8f, false);
            }
        }
    }
}
