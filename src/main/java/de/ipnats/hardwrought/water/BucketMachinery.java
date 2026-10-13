package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The two ways a bucket is used without a hand on it, taught to count.
 *
 * <p>A bucket in this mod holds a measured amount, and everything that knows the vanilla item knows
 * only full or empty. Left alone, a dispenser would pour a whole block out of a bucket holding
 * fifty millibuckets, and a cauldron would take the same bucket and give back a full one. Both are
 * ways of making water out of nothing, which is the one thing the finite-water system exists to
 * prevent — section 23.1 removes infinite water, and leaving two machines able to mint it would
 * make the rest of it decorative.
 *
 * <p>The dispenser is taught rather than blocked: it pours what the bucket has into the cell in
 * front of it and keeps the rest, and an empty one dips for as much as it can reach. That is what
 * the hand does, and a machine doing something different from the hand is a bug waiting to be
 * found. The cauldron is simply held to the full bucket it was always written for.
 */
public final class BucketMachinery {
    private BucketMachinery() { }

    public static void initialize() {
        net.minecraft.world.level.block.DispenserBlock.registerBehavior(Items.WATER_BUCKET, new Pour());
        net.minecraft.world.level.block.DispenserBlock.registerBehavior(Items.BUCKET, new Dip());
    }

    /** Pours what the bucket holds into the cell in front, and keeps whatever did not fit. */
    private static final class Pour extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            ServerLevel level = source.level();
            BlockPos target = source.pos().relative(source.state().getValue(BlockStateProperties.FACING));
            int carried = BucketWater.held(stack);
            if (carried <= 0 || !WaterStorage.canHold(level, target)) {
                return super.execute(source, stack);
            }
            // Onto the surface and up to a block, exactly as a hand does it: see WaterEvents.
            for (int step = 0; step < 16; step++) {
                if (WaterStorage.amount(level, target) < WaterAmounts.BLOCK) break;
                if (!WaterStorage.canHold(level, target.above())) break;
                target = target.above();
            }
            int present = WaterStorage.amount(level, target);
            int poured = Math.min(carried, Math.max(0, WaterAmounts.BLOCK - present));
            if (poured <= 0) return super.execute(source, stack);

            WaterStorage.setAmount(level, target, present + poured);
            WaterQuality quality = stack.get(de.ipnats.hardwrought.core.registry.ModDataComponents.WATER_QUALITY);
            if (quality != null) WaterQualityStorage.add(level, target, quality, present, poured);
            WaterFlow.disturbImmediately(level, target);
            level.playSound(null, target, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
            return BucketWater.with(stack, carried - poured, quality);
        }
    }

    /** Dips into the cell in front for as much as it can reach, up to a bucketful. */
    private static final class Dip extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            ServerLevel level = source.level();
            Direction facing = source.state().getValue(BlockStateProperties.FACING);
            BlockPos target = source.pos().relative(facing);
            if (WaterStorage.amount(level, target) <= 0) return super.execute(source, stack);

            var runtime = CoreLifecycle.find(level.getServer());
            WaterQuality quality = runtime == null
                    ? WaterQuality.FRESH : runtime.water().qualityAt(level, target);
            int drawn = WaterDraw.drawUpTo(level, target, BucketWater.CAPACITY);
            if (drawn <= 0) return super.execute(source, stack);
            level.playSound(null, target, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            // One bucket of the stack is filled; the others stay where they are.
            ItemStack filled = BucketWater.with(stack, drawn, quality);
            if (stack.getCount() <= 1) return filled;
            // One of the stack is filled and thrown clear; the rest stay in the dispenser.
            stack.shrink(1);
            super.execute(source, filled);
            return stack;
        }
    }
}
