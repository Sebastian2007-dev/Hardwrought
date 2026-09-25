package de.ipnats.hardwrought.oil;

import de.ipnats.hardwrought.core.debug.DiagnosticRegistry;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.geology.Reservoir;
import de.ipnats.hardwrought.geology.Reservoirs;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Locale;

/**
 * The rules of a well: how fast a rig drills, how much a reservoir gives, and how that falls as it
 * empties. Pure arithmetic, so it can be checked without a world.
 *
 * <p>Section 60's "the rate is the limit, not the amount" from the drill of Milestone 6 holds only
 * half here. A reservoir is finite — what has been drawn out of it is saved — and a well into it
 * gives less as it empties, because the pressure that pushes the oil up falls with it. An old field
 * is still worth pumping, just not as much.
 */
public final class Oilfield {
    /** The speed a rig is built for, in turns per minute: a water wheel in a river, a steady windmill. */
    public static final float RATED_SPEED = 16.0f;
    /** Blocks of rock a rig bores through per second at its rated speed. */
    public static final double BORE_PER_SECOND = 0.5;
    /** Oil a fresh reservoir at full pressure gives per second at the rated speed, in millibuckets. */
    public static final double OIL_PER_SECOND = 50.0;
    /** Gas a fresh field at full pressure gives per second at the rated speed, in gas units. */
    public static final double GAS_PER_SECOND = 1.0;
    /** The gas that comes up with oil out of its cap, as a share of the oil well's gas. */
    public static final double ASSOCIATED_GAS = 0.2;
    /** A reservoir never quite stops giving: this much of its first pressure is left at the end. */
    public static final double RESIDUAL_PRESSURE = 0.05;

    private Oilfield() { }

    /** Blocks bored per tick at a speed. Direction does not matter; slower drills slower. */
    public static double borePerTick(float speed) {
        return BORE_PER_SECOND / 20.0 * Math.abs(speed) / RATED_SPEED;
    }

    /** What is left of a reservoir, 0 to 1. */
    public static double remaining(Reservoir reservoir, long drawn) {
        long capacity = reservoir.capacity();
        if (capacity <= 0) return 0;
        return Math.max(0.0, 1.0 - drawn / (double) capacity);
    }

    /** How hard it pushes now: its first pressure, falling as it empties, never quite to nothing while any is left. */
    public static double pressure(Reservoir reservoir, long drawn) {
        double left = remaining(reservoir, drawn);
        if (left <= 0) return 0;
        return reservoir.pressure() * (RESIDUAL_PRESSURE + (1.0 - RESIDUAL_PRESSURE) * left);
    }

    /** What a well gives per tick: oil in millibuckets, or gas in units, by what it is sunk into. */
    public static double perTick(Reservoir reservoir, long drawn, float speed) {
        double base = reservoir.kind() == Reservoir.Kind.OIL ? OIL_PER_SECOND : GAS_PER_SECOND;
        return base / 20.0 * pressure(reservoir, drawn) * Math.abs(speed) / RATED_SPEED;
    }

    /** Operator diagnostics on the ore channel: the reservoir under the player, or the nearest one. */
    public static void registerDiagnostics(DiagnosticRegistry registry, CoreSaveData save) {
        registry.register("hardwrought:reservoirs", DiagnosticRegistry.Channel.ORE, (level, pos) -> {
            long seed = level.getSeed();
            Reservoir under = Reservoirs.under(seed, pos.getX(), pos.getZ());
            if (under != null) return describe("over", under, pos, save);
            List<Reservoir> near = Reservoirs.nearby(seed, pos, 384);
            if (near.isEmpty()) return "no oil or gas within reach";
            return describe(Math.round(near.getFirst().horizontalDistance(pos)) + " blocks from", near.getFirst(), pos, save);
        });
    }

    private static String describe(String where, Reservoir reservoir, BlockPos pos, CoreSaveData save) {
        long drawn = save.reservoirDrawn(reservoir.key());
        return String.format(Locale.ROOT, "%s %s reservoir | top Y %d, centre Y %d, %d across | pressure %.2f, %.0f%% left",
                where, reservoir.kind().serializedName(), reservoir.topAt(reservoir.centre().getX(), reservoir.centre().getZ()),
                reservoir.centre().getY(), reservoir.horizontalRadius() * 2, pressure(reservoir, drawn),
                remaining(reservoir, drawn) * 100);
    }
}
