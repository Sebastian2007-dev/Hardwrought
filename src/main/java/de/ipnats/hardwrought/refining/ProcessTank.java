package de.ipnats.hardwrought.refining;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A tank inside a refining machine: an inlet that pipes fill with one liquid and never draw from, or an
 * outlet that the machine fills and pipes only draw from. Amounts in Fabric's droplets.
 */
public final class ProcessTank extends SingleVariantStorage<FluidVariant> {
    public static final long MILLIBUCKET = FluidConstants.BUCKET / 1000;

    private final long capacity;
    private final Fluid only;
    private final java.util.Set<Fluid> takes;
    private final boolean inlet;
    private final Runnable changed;

    private ProcessTank(long buckets, Fluid only, java.util.Set<Fluid> takes, boolean inlet, Runnable changed) {
        this.capacity = buckets * FluidConstants.BUCKET;
        this.only = only;
        this.takes = takes;
        this.inlet = inlet;
        this.changed = changed;
    }

    /** A tank pipes fill with this liquid. */
    public static ProcessTank inlet(long buckets, Fluid only, Runnable changed) {
        return new ProcessTank(buckets, only, java.util.Set.of(only), true, changed);
    }

    /** A tank pipes fill with any one of these liquids at a time, such as an engine's fuels. */
    public static ProcessTank inlet(long buckets, java.util.Set<Fluid> takes, Runnable changed) {
        return new ProcessTank(buckets, takes.iterator().next(), java.util.Set.copyOf(takes), true, changed);
    }

    /** A tank the machine fills with this liquid and pipes draw from. */
    public static ProcessTank outlet(long buckets, Fluid only, Runnable changed) {
        return new ProcessTank(buckets, only, java.util.Set.of(only), false, changed);
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
        return inlet && takes.contains(variant.getFluid());
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
        return amount / MILLIBUCKET;
    }

    /** Whether so many millibuckets more fit. */
    public boolean room(long millibuckets) {
        return capacity - amount >= millibuckets * MILLIBUCKET;
    }

    /** The machine takes from its inlet, or fills its outlet, outside any pipe's transaction. */
    public boolean take(long millibuckets) {
        if (amount < millibuckets * MILLIBUCKET) return false;
        amount -= millibuckets * MILLIBUCKET;
        if (amount == 0) variant = FluidVariant.blank();
        changed.run();
        return true;
    }

    public void fill(long millibuckets) {
        variant = FluidVariant.of(only);
        amount = Math.min(capacity, amount + millibuckets * MILLIBUCKET);
        changed.run();
    }

    public void save(ValueOutput output, String key) {
        if (amount <= 0) return;
        output.putLong(key, amount);
        if (takes.size() > 1) output.putString(key + "_fluid", net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(variant.getFluid()).toString());
    }

    public void load(ValueInput input, String key) {
        amount = input.getLongOr(key, 0);
        Fluid held = only;
        var id = net.minecraft.resources.Identifier.tryParse(input.getStringOr(key + "_fluid", ""));
        if (id != null) {
            Fluid named = net.minecraft.core.registries.BuiltInRegistries.FLUID.getValue(id);
            if (takes.contains(named)) held = named;
        }
        variant = amount > 0 ? FluidVariant.of(held) : FluidVariant.blank();
    }

    /** The liquid in it, or null where it is empty. */
    public Fluid held() {
        return variant.isBlank() ? null : variant.getFluid();
    }
}
