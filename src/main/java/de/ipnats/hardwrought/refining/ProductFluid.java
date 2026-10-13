package de.ipnats.hardwrought.refining;

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
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * One of the liquids of refining, standing in the world. Like crude oil it is lighter than water and
 * keeps on top of it, never makes new sources, and drags at what wades in it; how fast it creeps and
 * how hard it drags is its {@link RefineryProduct}'s.
 */
public abstract class ProductFluid extends FlowingFluid {
    protected final RefineryProduct product;

    protected ProductFluid(RefineryProduct product) {
        this.product = product;
    }

    @Override
    public Fluid getFlowing() {
        return product.flowing();
    }

    @Override
    public Fluid getSource() {
        return product.source();
    }

    @Override
    public Item getBucket() {
        return product.bucket();
    }

    @Override
    public boolean isSame(Fluid fluid) {
        return fluid == product.source() || fluid == product.flowing();
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

    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluid) {
        if (direction == Direction.DOWN && level.getFluidState(pos).is(FluidTags.WATER)) return;
        super.spreadTo(level, pos, state, direction, fluid);
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return direction == Direction.DOWN && !isSame(fluid) && !fluid.is(FluidTags.WATER);
    }

    @Override
    protected int getSlopeFindDistance(LevelReader level) {
        return product.tickDelay() > 20 ? 2 : 4;
    }

    @Override
    protected int getDropOff(LevelReader level) {
        return product.tickDelay() > 20 ? 2 : 1;
    }

    @Override
    public int getTickDelay(LevelReader level) {
        return product.tickDelay();
    }

    @Override
    protected float getExplosionResistance() {
        return 100.0f;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return product.block().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
    }

    @Override
    protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects) {
        entity.makeStuckInBlock(product.block().defaultBlockState(), new Vec3(product.drag(), 0.9, product.drag()));
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }

    public static class Flowing extends ProductFluid {
        public Flowing(RefineryProduct product) {
            super(product);
        }

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

    public static class Source extends ProductFluid {
        public Source(RefineryProduct product) {
            super(product);
        }

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
