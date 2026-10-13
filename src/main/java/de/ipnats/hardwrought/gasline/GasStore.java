package de.ipnats.hardwrought.gasline;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A plain gas store for a block entity: so much room, built for so much pressure, filled from outside
 * or only drawn from. Tells its owner when it changed.
 */
public class GasStore implements GasStorage {
    private final double capacity;
    private final double rating;
    private final boolean takesIn;
    private final Runnable changed;
    private final java.util.function.Predicate<GasKind> takes;
    private GasKind kind;
    private double amount;

    /**
     * @param takesIn whether lines may fill it; a source, such as a column's top, is only drawn from
     */
    public GasStore(double capacity, double rating, boolean takesIn, Runnable changed) {
        this(capacity, rating, takesIn, gas -> true, changed);
    }

    /** A store that lines fill only with the gases this lets in: a burner takes only what burns. */
    public GasStore(double capacity, double rating, boolean takesIn, java.util.function.Predicate<GasKind> takes, Runnable changed) {
        this.takes = takes;
        this.capacity = capacity;
        this.rating = rating;
        this.takesIn = takesIn;
        this.changed = changed;
    }

    @Override
    public GasKind kind() {
        return amount > 0 ? kind : null;
    }

    @Override
    public double amount() {
        return amount;
    }

    @Override
    public double capacity() {
        return capacity;
    }

    @Override
    public double rating() {
        return rating;
    }

    @Override
    public boolean accepts(GasKind gas) {
        return takesIn && takes.test(gas) && GasStorage.super.accepts(gas);
    }

    @Override
    public double insert(GasKind gas, double most) {
        return add(gas, most, false);
    }

    /** Fills it whether or not lines may: what makes the gas in it puts it there this way. */
    public double produce(GasKind gas, double most) {
        return add(gas, most, true);
    }

    private double add(GasKind gas, double most, boolean own) {
        if (!(own || (takesIn && takes.test(gas))) || most <= 0 || (kind() != null && kind() != gas)) return 0;
        double put = Math.min(most, capacity - amount);
        if (put <= 0) return 0;
        kind = gas;
        amount += put;
        changed.run();
        return put;
    }

    @Override
    public double extract(double most) {
        double taken = Math.min(most, amount);
        if (taken <= 0) return 0;
        amount -= taken;
        changed.run();
        return taken;
    }

    public void save(ValueOutput output, String key) {
        if (amount > 0 && kind != null) {
            output.putString(key + "_kind", kind.getSerializedName());
            output.putDouble(key + "_amount", amount);
        }
    }

    public void load(ValueInput input, String key) {
        String name = input.getStringOr(key + "_kind", "");
        kind = null;
        for (GasKind gas : GasKind.values()) if (gas.getSerializedName().equals(name)) kind = gas;
        amount = kind == null ? 0 : input.getDoubleOr(key + "_amount", 0);
    }
}
