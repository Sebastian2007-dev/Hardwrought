package de.ipnats.hardwrought.steam;

import de.ipnats.hardwrought.core.registry.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Live steam let out where it should not be: a burst line, a boiler that leaks, a boiler that bursts.
 * It is invisible at the leak and a white cloud a hand's breadth on, and it scalds whoever stands in it.
 */
public final class Scalding {
    /** How far a burst throws its steam, in blocks. */
    private static final double BURST_REACH = 2.5;
    /** How far a leak's jet reaches. */
    private static final double LEAK_REACH = 1.5;

    private Scalding() { }

    /** So many units of steam let out at once: a cloud, a roar, and burns for all close by. */
    public static void burst(ServerLevel level, BlockPos pos, double units) {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                30, 0.6, 0.6, 0.6, 0.15);
        scald(level, pos, BURST_REACH, 2.0f + (float) units / 4.0f);
    }

    /**
     * A leak's work for one second: a hiss and a jet, and a burn for whoever stands at it. Weaker
     * leaks and lower pressures hurt less.
     */
    public static void leak(ServerLevel level, BlockPos pos, double bar) {
        if (bar < 0.5) return;
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.6f);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                6, 0.3, 0.2, 0.3, 0.05);
        scald(level, pos, LEAK_REACH, (float) Math.min(4.0, 0.5 + bar / 4.0));
    }

    private static void scald(ServerLevel level, BlockPos pos, double reach, float damage) {
        AABB box = new AABB(pos).inflate(reach);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box)) {
            victim.hurtServer(level, level.damageSources().source(ModDamageTypes.SCALDING), damage);
        }
    }
}
