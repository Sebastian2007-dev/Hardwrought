package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What one region's aquifer is holding, and when that was last true.
 *
 * <p>Only regions that have been drawn on are saved. A region nobody has taken water out of is full
 * by definition, which is what keeps the save file from growing a table for the whole world.
 *
 * <p>All of the arithmetic is here and it is pure, so what a drought does can be checked without a
 * world: a region that has been drawn down refills from its saved timestamp the next time anyone
 * asks about it, exactly the way an unattended room catches up in the environment model.
 *
 * @param stored      millibuckets still in the ground
 * @param updatedTick the simulation tick that amount was measured at
 */
public record AquiferState(int stored, long updatedTick) {
    public static final Codec<AquiferState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("stored").forGetter(AquiferState::stored),
            Codec.LONG.fieldOf("updated_tick").forGetter(AquiferState::updatedTick)
    ).apply(instance, AquiferState::new));

    public AquiferState {
        if (stored < 0 || updatedTick < 0) throw new IllegalArgumentException("Invalid aquifer state");
    }

    /**
     * The same aquifer brought forward to {@code now}. Rain and meltwater soak in at a steady rate
     * while nobody is watching; past {@code maxCatchUpTicks} the catch-up stops, so a region that
     * has been left alone for a year is not simulated for a year in one step.
     */
    public AquiferState rechargedTo(long now, int capacity, double perTick, long maxCatchUpTicks) {
        if (capacity < 0 || perTick < 0 || maxCatchUpTicks < 0) {
            throw new IllegalArgumentException("Invalid recharge parameters");
        }
        if (now <= updatedTick) return new AquiferState(Math.min(stored, capacity), updatedTick);
        long elapsed = Math.min(now - updatedTick, maxCatchUpTicks);
        long gained = Math.round(elapsed * perTick);
        int filled = (int) Math.min(capacity, (long) stored + gained);
        return new AquiferState(filled, now);
    }

    /** Takes what is there, never more. The caller learns what it actually got from {@link #stored}. */
    public AquiferState drawn(int millibuckets, long now) {
        if (millibuckets < 0) throw new IllegalArgumentException("Cannot draw a negative amount");
        return new AquiferState(Math.max(0, stored - millibuckets), Math.max(now, updatedTick));
    }

    /** How much of this aquifer is left, 0 to 1. */
    public double fill(int capacity) {
        if (capacity <= 0) return 0;
        return Math.min(1.0, stored / (double) capacity);
    }
}
