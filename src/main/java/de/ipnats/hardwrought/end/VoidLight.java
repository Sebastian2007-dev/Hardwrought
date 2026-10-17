package de.ipnats.hardwrought.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The floor of light a traveller arrives on in the End (endgame § 4). It holds like stone, and is
 * a thin plate of light, runes pulsing in it, lit from within, motes rising along its edges ({@code END_PLATFORM});
 * in its last ten seconds it flickers — and after {@link EndRules#PLATFORM_TICKS} it is gone.
 */
public class VoidLight extends BaseEntityBlock {
    /** Its last seconds: it flickers. */
    public static final BooleanProperty FADING = BooleanProperty.create("fading");
    /** How long before it goes out it starts to flicker. */
    static final int FADING_TICKS = 200;

    public VoidLight(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FADING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FADING);
    }

    /** One floor, not a row of plates: no faces drawn between two pieces of it. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, EndRules.VOID_LIGHT_ENTITY, (l, pos, s, light) -> light.tick((ServerLevel) l));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) != 0) return;
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
                0, 0.02 + random.nextDouble() * 0.02, 0);
    }

    /** Keeps the time its light goes out. */
    public static class Entity extends BlockEntity {
        long until;

        public Entity(BlockPos pos, BlockState state) {
            super(EndRules.VOID_LIGHT_ENTITY, pos, state);
        }

        void tick(ServerLevel level) {
            if (until == 0) until = level.getGameTime() + EndRules.PLATFORM_TICKS;
            long left = until - level.getGameTime();
            BlockState state = getBlockState();
            if (left > 0 && left <= FADING_TICKS && !state.getValue(FADING)) level.setBlock(getBlockPos(), state.setValue(FADING, true), Block.UPDATE_CLIENTS);
            if (left > FADING_TICKS && state.getValue(FADING)) level.setBlock(getBlockPos(), state.setValue(FADING, false), Block.UPDATE_CLIENTS);
            if (left > 0) return;
            level.removeBlock(getBlockPos(), false);
        }

        /** Makes it go out this many ticks from now; for tests. */
        public void testFadeIn(ServerLevel level, int ticks) {
            until = level.getGameTime() + ticks;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putLong("until", until);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            until = input.getLongOr("until", 0);
        }
    }
}
