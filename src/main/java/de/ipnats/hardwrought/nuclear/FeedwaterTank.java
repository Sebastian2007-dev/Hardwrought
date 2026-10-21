package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.chemistry.SaltPan;
import de.ipnats.hardwrought.refining.ProcessTank;
import de.ipnats.hardwrought.water.WaterQuality;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A tank of purified water, the only water a steam cycle can take: anything else leaves salts on the hot
 * cladding and the turbine blades. A reactor's is an inlet pipes fill; a turbine's condenser is an outlet
 * pipes draw from.
 */
public final class FeedwaterTank extends SingleVariantStorage<FluidVariant> {
    private static final FluidVariant PURIFIED = SaltPan.water(WaterQuality.PURIFIED);

    private final long capacity;
    private final boolean inlet;
    private final Runnable changed;

    public FeedwaterTank(long buckets, boolean inlet, Runnable changed) {
        this.capacity = buckets * FluidConstants.BUCKET;
        this.inlet = inlet;
        this.changed = changed;
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
        return inlet && SaltPan.quality(variant) == WaterQuality.PURIFIED;
    }

    @Override
    protected boolean canExtract(FluidVariant variant) {
        return !inlet;
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

    /** The machine takes from it outside any pipe's transaction; false where there is not so much. */
    public boolean take(long millibuckets) {
        long droplets = millibuckets * ProcessTank.MILLIBUCKET;
        if (amount < droplets) return false;
        amount -= droplets;
        if (amount == 0) variant = FluidVariant.blank();
        changed.run();
        return true;
    }

    /** The machine fills it; what does not fit is lost. */
    public void fill(long millibuckets) {
        if (millibuckets <= 0) return;
        variant = PURIFIED;
        amount = Math.min(capacity, amount + millibuckets * ProcessTank.MILLIBUCKET);
        changed.run();
    }

    public void save(ValueOutput output, String key) {
        if (amount > 0) output.putLong(key, amount);
    }

    public void load(ValueInput input, String key) {
        amount = Math.min(capacity, input.getLongOr(key, 0));
        variant = amount > 0 ? PURIFIED : FluidVariant.blank();
    }
}
