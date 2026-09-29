package de.ipnats.hardwrought.oil;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Crude oil as it comes out of the ground: black, thick and slow.
 *
 * <p>It creeps rather than runs, a few blocks from where it is poured, and two pools of it never make
 * a third. It is lighter than water and does not mix with it: poured onto water it spreads over the
 * top, and water poured onto it does not sink through. It burns (see {@link CrudeOilBlock}), and
 * whatever wades into it is dragged at by it.
 */
public abstract class CrudeOilFluid extends FlowingFluid {
    /** Ticks between two steps of its spread: lava's pace, not water's. */
    public static final int TICK_DELAY = 25;
    /** How much of its speed a body wading through keeps each tick, across and up. */
    private static final Vec3 DRAG = new Vec3(0.55, 0.9, 0.55);

    @Override
    public Fluid getFlowing() {
        return ModFluids.FLOWING_CRUDE_OIL;
    }

    @Override
    public Fluid getSource() {
        return ModFluids.CRUDE_OIL;
    }

    @Override
    public Item getBucket() {
        return ModItems.CRUDE_OIL_BUCKET;
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == ModFluids.CRUDE_OIL || fluid == ModFluids.FLOWING_CRUDE_OIL;
    }

    @Override
    protected boolean canConvertToSource(ServerLevel level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockEntity entity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, entity);
    }

    /** Lighter than water: it spreads out over it rather than pushing down into it. */
    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluid) {
        if (direction == Direction.DOWN && level.getFluidState(pos).is(FluidTags.WATER)) return;
        super.spreadTo(level, pos, state, direction, fluid);
    }

    /** Water poured onto oil stays on top of it too; anything else falling onto it may take its place. */
    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return direction == Direction.DOWN && !isSame(fluid) && !fluid.is(FluidTags.WATER);
    }

    @Override
    protected int getSlopeFindDistance(LevelReader level) {
        return 2;
    }

    @Override
    protected int getDropOff(LevelReader level) {
        return 2;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return TICK_DELAY;
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0f;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return ModBlocks.CRUDE_OIL.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects) {
        entity.makeStuckInBlock(ModBlocks.CRUDE_OIL.defaultBlockState(), DRAG);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_LAVA);
    }

    public static class Flowing extends CrudeOilFluid {
        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }

    public static class Source extends CrudeOilFluid {
        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }
}
