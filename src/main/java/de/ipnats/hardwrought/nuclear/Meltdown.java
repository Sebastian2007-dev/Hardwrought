package de.ipnats.hardwrought.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What an overheated core does.
 *
 * <p>Past {@value ReactorCore#CLADDING_FAILS} °C the zircaloy cladding swells, bursts and burns in the
 * steam, and fission products get out: now and then a lump of radioactive debris is thrown up round the
 * vessel. At {@value ReactorCore#MELTS} °C the core melts. The steam and hydrogen in the vessel blow it
 * open, the fuel is gone into a pool of corium at the bottom of the core, and debris is scattered far
 * round it. The controller is lost with it.
 */
public final class Meltdown {
    /** The explosion of steam and hydrogen, as a power: more than TNT, less than a charged creeper's square. */
    static final float BLAST = 7.0f;
    static final int DEBRIS = 40, DEBRIS_RANGE = 14;

    private Meltdown() { }

    /** A burst rod: a lump of debris by the vessel, and a cloud of steam. */
    static void leak(ServerLevel level, ReactorController reactor) {
        RandomSource random = level.getRandom();
        BlockPos centre = reactor.centre();
        scatter(level, centre, 1, 5, random);
        level.sendParticles(ParticleTypes.CLOUD, centre.getX() + 0.5, centre.getY() + 3.2, centre.getZ() + 0.5, 12, 0.6, 0.2, 0.6, 0.05);
        level.playSound(null, centre, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.4f);
    }

    /** The core melts: the vessel bursts, the fuel becomes corium, debris everywhere. */
    static void melt(ServerLevel level, ReactorController reactor) {
        BlockPos centre = reactor.centre();
        BlockPos controller = reactor.getBlockPos();
        reactor.channels().clear();
        Radiation.source(level, centre, 0);
        level.setBlock(controller, Blocks.AIR.defaultBlockState(), 3);
        // The core: a pool of corium at the bottom of it, the rest burnt out.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(centre.offset(dx, -1, dz), NuclearBlocks.CORIUM.defaultBlockState(), 3);
                level.setBlock(centre.offset(dx, 0, dz), (dx == 0 || dz == 0) ? NuclearBlocks.CORIUM.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(centre.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.explode(null, centre.getX() + 0.5, centre.getY() + 1.5, centre.getZ() + 0.5, BLAST, false, Level.ExplosionInteraction.BLOCK);
        scatter(level, centre, DEBRIS, DEBRIS_RANGE, level.getRandom());
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, centre.getX() + 0.5, centre.getY() + 3, centre.getZ() + 0.5,
                60, 2.0, 2.0, 2.0, 0.05);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(centre) < 128 * 128) {
                player.sendSystemMessage(Component.translatable("message.hardwrought.reactor.meltdown"));
            }
        }
    }

    /** Throws so many lumps of debris onto the ground round a place, within so far. */
    private static void scatter(ServerLevel level, BlockPos centre, int count, int range, RandomSource random) {
        for (int i = 0; i < count; i++) {
            int x = centre.getX() + random.nextInt(range * 2 + 1) - range;
            int z = centre.getZ() + random.nextInt(range * 2 + 1) - range;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (level.getBlockState(at).canBeReplaced() && !level.getBlockState(at.below()).isAir()) {
                level.setBlock(at, NuclearBlocks.RADIOACTIVE_DEBRIS.defaultBlockState(), 3);
            }
        }
    }
}
