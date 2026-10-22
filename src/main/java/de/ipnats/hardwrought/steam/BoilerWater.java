package de.ipnats.hardwrought.steam;

import de.ipnats.hardwrought.chemistry.SaltPan;
import de.ipnats.hardwrought.refining.ProcessTank;
import de.ipnats.hardwrought.water.WaterQuality;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The water in a boiler. It takes water of every kind, by pipe or bucket, and does not keep them apart:
 * what is boiled off is pure, and what the water carried stays behind on the plates as scale. So each
 * bucket put in leaves its share of scale for later, the more the dirtier it was — nothing for purified
 * water, a great deal for seawater. Nothing draws water out of a boiler; it leaves as steam.
 */
public final class BoilerWater extends SingleVariantStorage<FluidVariant> {
    private static final FluidVariant WATER = FluidVariant.of(Fluids.WATER);

    private final long capacity;
    private final Runnable changed;
    /** Scale still to settle out of the water put in, as a share of a fully scaled boiler. */
    private final Minerals minerals = new Minerals();

    public BoilerWater(long buckets, Runnable changed) {
        this.capacity = buckets * FluidConstants.BUCKET;
        this.changed = changed;
    }

    /** The scale a bucket of this water leaves, as a share of a fully scaled boiler. */
    public static double scalePerBucket(WaterQuality quality) {
        return switch (quality) {
            case PURIFIED -> 0.0;
            case FRESH -> 0.02;
            case RIVER -> 0.03;
            case SWAMP -> 0.08;
            case SALT -> 0.25;
        };
    }

    @Override
    protected FluidVariant getBlankVariant() {
        return FluidVariant.blank();
    }

    @Override
    protected long getCapacity(FluidVariant variant) {
        return capacity;
    }

    @Override
    protected boolean canInsert(FluidVariant variant) {
        return variant.isOf(Fluids.WATER);
    }

    @Override
    protected boolean canExtract(FluidVariant variant) {
        return false;
    }

    /** Any water goes in on top of any other; it is all one water inside. */
    @Override
    public long insert(FluidVariant inserted, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(inserted, maxAmount);
        if (!canInsert(inserted)) return 0;
        long put = Math.min(maxAmount, capacity - amount);
        if (put <= 0) return 0;
        updateSnapshots(transaction);
        minerals.updateSnapshots(transaction);
        amount += put;
        variant = WATER;
        WaterQuality quality = SaltPan.quality(inserted);
        minerals.value += scalePerBucket(quality == null ? WaterQuality.FRESH : quality) * put / FluidConstants.BUCKET;
        return put;
    }

    @Override
    protected void onFinalCommit() {
        changed.run();
    }

    public long millibuckets() {
        return amount / ProcessTank.MILLIBUCKET;
    }

    public long capacityMillibuckets() {
        return capacity / ProcessTank.MILLIBUCKET;
    }

    /**
     * Boils so many millibuckets off, outside any pipe's transaction. Returns the scale that leaves
     * behind: the water's share of what it carried in.
     */
    public double boil(long millibuckets) {
        long droplets = Math.min(amount, millibuckets * ProcessTank.MILLIBUCKET);
        if (droplets <= 0) return 0;
        double share = (double) droplets / amount;
        double scale = minerals.value * share;
        minerals.value -= scale;
        amount -= droplets;
        if (amount == 0) {
            variant = FluidVariant.blank();
            minerals.value = 0;
        }
        changed.run();
        return scale;
    }

    /** Fills it straight, for tests and the book: water of this kind. */
    public void fill(long millibuckets, WaterQuality quality) {
        long droplets = Math.min(capacity - amount, millibuckets * ProcessTank.MILLIBUCKET);
        if (droplets <= 0) return;
        amount += droplets;
        variant = WATER;
        minerals.value += scalePerBucket(quality) * droplets / FluidConstants.BUCKET;
        changed.run();
    }

    public void save(ValueOutput output, String key) {
        if (amount > 0) {
            output.putLong(key, amount);
            output.putDouble(key + "_minerals", minerals.value);
        }
    }

    public void load(ValueInput input, String key) {
        amount = Math.min(capacity, input.getLongOr(key, 0));
        variant = amount > 0 ? WATER : FluidVariant.blank();
        minerals.value = amount > 0 ? input.getDoubleOr(key + "_minerals", 0) : 0;
    }

    /** What the water carries, rolled back with the rest when a pipe's transaction is. */
    private static final class Minerals extends SnapshotParticipant<Double> {
        private double value;

        @Override
        protected Double createSnapshot() {
            return value;
        }

        @Override
        protected void readSnapshot(Double snapshot) {
            value = snapshot;
        }
    }
}
