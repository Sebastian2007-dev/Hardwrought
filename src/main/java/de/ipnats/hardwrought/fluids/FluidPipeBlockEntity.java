package de.ipnats.hardwrought.fluids;

import de.ipnats.hardwrought.machinery.ConnectedPipeBlock;
import de.ipnats.hardwrought.machinery.ConnectedPipeBlockEntity;
import de.ipnats.hardwrought.machinery.PipeLink;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A fluid pipe at work. A pipe holds nothing: on every side set to draw, it takes what the run's pumps
 * allow this tick and sends it at once along the run to the nearest block that takes it, and on to
 * the next where that one is full — no faster than the narrowest pipe on the way lets through.
 *
 * <p>A lake is drawn a bucket at a time: the pipe gathers what the pumps push until it comes to a
 * bucket, then takes the source block and passes the bucket on, all of it or none.
 */
public class FluidPipeBlockEntity extends ConnectedPipeBlockEntity {
    private static final long MILLIBUCKET = FluidConstants.BUCKET / 1000;

    /** What the pumps have pushed towards drawing a lake, until it comes to a whole bucket. In droplets. */
    private long gathered;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(FluidBlocks.PIPE_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidPipeBlockEntity pipe) {
        FluidRun run = null;
        for (Direction side : Direction.values()) {
            if (ConnectedPipeBlock.link(state, side) != PipeLink.EXTRACT) continue;
            if (run == null) run = FluidRun.of(level, pos);
            double left = run.left(level);
            if (left <= 0) return;
            BlockPos from = pos.relative(side);
            Storage<FluidVariant> source = FluidStorage.SIDED.find(level, from, side.getOpposite());
            if (source != null) {
                long moved = pipe.pass(source, from, run, (long) (left * MILLIBUCKET));
                run.spend(level, (double) moved / MILLIBUCKET);
            } else {
                pipe.drawLake(level, from, run, left);
            }
        }
    }

    /** Sends up to so much from the source along the run, nearest taker first. How much went, in droplets. */
    private long pass(Storage<FluidVariant> source, BlockPos from, FluidRun run, long most) {
        long moved = 0;
        for (FluidRun.Destination destination : run.destinations) {
            if (moved >= most) break;
            if (destination.pos().equals(from)) continue;
            moved += StorageUtil.move(source, destination.storage(), variant -> true,
                    Math.min(most - moved, destination.narrowest()), null);
        }
        if (moved > 0) setChanged();
        return moved;
    }

    private void drawLake(Level level, BlockPos from, FluidRun run, double left) {
        FluidVariant variant = WorldFluid.source(level, from);
        if (variant == null) return;
        long narrowest = run.destinations.stream().mapToLong(FluidRun.Destination::narrowest).max().orElse(0);
        long push = Math.min((long) (left * MILLIBUCKET), narrowest);
        if (push <= 0) return;
        gathered = Math.min(FluidConstants.BUCKET, gathered + push);
        run.spend(level, (double) push / MILLIBUCKET);
        setChanged();
        if (gathered < FluidConstants.BUCKET) return;
        for (FluidRun.Destination destination : run.destinations) {
            try (Transaction transaction = Transaction.openOuter()) {
                if (destination.storage().insert(variant, FluidConstants.BUCKET, transaction) == FluidConstants.BUCKET) {
                    transaction.commit();
                    WorldFluid.take(level, from);
                    gathered = 0;
                    return;
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (gathered > 0) output.putLong("gathered", gathered);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        gathered = input.getLongOr("gathered", 0);
    }
}
