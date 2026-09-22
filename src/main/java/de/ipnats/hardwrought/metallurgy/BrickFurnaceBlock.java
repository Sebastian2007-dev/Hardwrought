package de.ipnats.hardwrought.metallurgy;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The block half of the {@link BrickFurnaceBlockEntity}: a furnace made of fired clay. */
public class BrickFurnaceBlock extends AbstractFurnaceBlock {
    public BrickFurnaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrickFurnaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.BRICK_FURNACE,
                (ticked, pos, ticketState, furnace) -> {
                    if (ticked instanceof net.minecraft.server.level.ServerLevel server) {
                        AbstractFurnaceBlockEntity.serverTick(server, pos, ticketState, furnace);
                    }
                });
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof BrickFurnaceBlockEntity furnace) {
            player.openMenu(furnace);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    1.0f, 1.0f, false);
        }
        // A brick furnace leaks: the smoke does not all go where it should.
        level.addParticle(ParticleTypes.SMOKE, x + random.nextDouble() * 0.4 - 0.2, y + 1.0,
                z + random.nextDouble() * 0.4 - 0.2, 0.0, 0.0, 0.0);
    }
}
