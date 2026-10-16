package de.ipnats.hardwrought.witchking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One of the four soul crystals the Witch King raises in his second phase, each feeding his ward. Brittle:
 * a few blows break it, or an arrow. It leaves nothing behind.
 */
public class WitchKingCrystal extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public WitchKingCrystal(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel server && !(projectile instanceof HexBlade) && !(projectile instanceof SoulOrb)) {
            server.destroyBlock(hit.getBlockPos(), false);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + random.nextDouble(),
                pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.05, 0);
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.04, 0);
        }
    }
}
