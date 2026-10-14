package de.ipnats.hardwrought.fluids;

import de.ipnats.hardwrought.water.WaterDraw;
import de.ipnats.hardwrought.water.WaterStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A pump set in water draws it into its run. Pipes leave water alone (see {@link WorldFluid}); a pump
 * with water against any of its sides takes it up itself, as much as it pushes, and sends it along the
 * run to the nearest block that takes it.
 *
 * <p>Where the water it stands in is a body of at least {@value #ENDLESS} blocks — a lake, a river, the
 * sea — it is an endless source: nothing a pump takes from it is missed. A smaller pond is drawn from
 * by the millibucket, as a bucket draws from it, and runs dry. The water keeps what kind it is: a pump
 * in the sea sends seawater down its pipes, which a salt pan takes and a tank of fresh water does not.
 */
public final class WaterIntake {
    /** Water blocks joined together that count as a source no pump empties. */
    public static final int ENDLESS = 100;
    /** How far from the pump the water is counted, in blocks along each axis. */
    private static final int REACH = 12;
    /** How long a count of the water holds before it is made again. */
    private static final long SURVEY_TICKS = 100;
    private static final long MILLIBUCKET = FluidConstants.BUCKET / 1000;

    /** What a pump found of the water around it, and what it has pushed short of a whole millibucket. */
    private static final class Survey {
        long at = Long.MIN_VALUE;
        BlockPos water;
        boolean endless;
        /** The water as the run carries it: seawater stays seawater. */
        FluidVariant variant = FluidVariant.of(Fluids.WATER);
        double carried;
    }

    private static final Map<BlockEntity, Survey> SURVEYS = new WeakHashMap<>();

    private WaterIntake() { }

    /** One tick of a pump: draws water where it stands in some and sends it along its run. */
    public static void tick(ServerLevel level, BlockPos pos, BlockEntity entity) {
        if (!(entity instanceof FluidPump pump)) return;
        double rate = pump.pumpRate();
        if (rate <= 0) return;
        Survey survey = SURVEYS.computeIfAbsent(entity, ignored -> new Survey());
        if (level.getGameTime() - survey.at >= SURVEY_TICKS || !isWater(level, survey.water)) {
            survey(level, pos, survey);
        }
        if (survey.water == null) return;
        FluidRun run = FluidRun.of(level, pos);
        if (run.destinations.isEmpty()) return;
        survey.carried += Math.min(rate, run.left(level));
        int wanted = (int) survey.carried;
        if (wanted <= 0) return;
        FluidVariant water = survey.variant;
        // What the run will take, first, so a pond is never drawn for water that has nowhere to go.
        int room = (int) (deliver(run, water, wanted * MILLIBUCKET, false) / MILLIBUCKET);
        int taken = survey.endless ? room : WaterDraw.drawUpTo(level, survey.water, room);
        survey.carried -= wanted;
        if (taken <= 0) return;
        deliver(run, water, taken * MILLIBUCKET, true);
        run.spend(level, taken);
    }

    /** Puts up to so much into the run, nearest taker first; with commit false only says how much would go. */
    private static long deliver(FluidRun run, FluidVariant water, long most, boolean commit) {
        long moved = 0;
        try (Transaction transaction = Transaction.openOuter()) {
            for (FluidRun.Destination destination : run.destinations) {
                if (moved >= most) break;
                moved += destination.storage().insert(water, Math.min(most - moved, destination.narrowest()), transaction);
            }
            if (commit) transaction.commit();
        }
        return moved;
    }

    /** Finds the water against the pump, and how much of it there is joined together. */
    private static void survey(ServerLevel level, BlockPos pos, Survey survey) {
        survey.at = level.getGameTime();
        survey.water = null;
        survey.endless = false;
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (isWater(level, next)) {
                survey.water = next.immutable();
                break;
            }
        }
        if (survey.water == null) return;
        survey.endless = count(level, pos, survey.water) >= ENDLESS;
        var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(level.getServer());
        survey.variant = de.ipnats.hardwrought.chemistry.SaltPan.water(
                runtime == null ? null : runtime.water().qualityAt(level, survey.water));
    }

    /** The water joined to start within reach of the pump, counted up to {@value #ENDLESS}. */
    static int count(ServerLevel level, BlockPos pump, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        int found = 0;
        while (!queue.isEmpty() && found < ENDLESS) {
            BlockPos at = queue.poll();
            found++;
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (Math.abs(next.getX() - pump.getX()) > REACH || Math.abs(next.getY() - pump.getY()) > REACH
                        || Math.abs(next.getZ() - pump.getZ()) > REACH) continue;
                if (seen.add(next) && isWater(level, next)) queue.add(next);
            }
        }
        return found;
    }

    private static boolean isWater(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) return false;
        return level.getFluidState(pos).is(FluidTags.WATER) && WaterStorage.amount(level, pos) > 0;
    }
}
