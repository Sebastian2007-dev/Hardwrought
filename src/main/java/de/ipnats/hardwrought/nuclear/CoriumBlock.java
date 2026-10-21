package de.ipnats.hardwrought.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Corium: what a core that melted through becomes once it sets. It is still hot from its own decay, and
 * burns whatever stands on it, as magma does.
 */
public class CoriumBlock extends Block {
    public CoriumBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel server && !entity.isSteppingCarefully()) {
            entity.hurtServer(server, level.damageSources().hotFloor(), 2.0f);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0 && level.getBlockState(pos.above()).isAir()) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
                    0.0, 0.02, 0.0);
        }
    }
}
