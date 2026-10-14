package de.ipnats.hardwrought.gasline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * The working of a gas pump, whatever drives it ({@link GasPumpBlock} on a shaft,
 * {@link ElectricGasPumpBlock} on current): gas goes in at the back and out at the front, and never the
 * other way.
 *
 * <p>Gas lines otherwise only even out — from where the pressure stands high to where it stands low — so a
 * pump is what fills a tank that is already fuller than the well, or keeps gas from running back into the
 * line it came from. Running, it draws from the line behind it, as much a tick as it pushes, and presses
 * into the line in front a little harder than the highest pressure standing there, so whatever is on that
 * line fills. Standing, it lets nothing through either way: a check valve.
 */
public final class GasCompressor {
    /** What it holds between drawing and pressing, in units. */
    static final double BUFFER = 8.0;
    /** How much harder than the line in front it presses, in bar. */
    static final double OVERPRESSURE = 1.0;

    /** How much it draws a tick, in units, as fast as it is driven just now. */
    private final DoubleSupplier rate;
    private final LongSupplier time;
    private final Runnable changed;
    private GasKind kind;
    private double held;
    /** What it has drawn this tick, so the line behind cannot outrun the pump. */
    private double drawn;
    private long drawnTick = Long.MIN_VALUE;
    /** The pressure it presses at, worked out each tick from the line in front. */
    private double outlet;

    GasCompressor(DoubleSupplier rate, LongSupplier time, Runnable changed) {
        this.rate = rate;
        this.time = time;
        this.changed = changed;
    }

    /** The back face: takes gas in while it runs, gives none back. */
    final GasStorage inlet = new GasStorage() {
        @Override
        public GasKind kind() {
            return kind;
        }

        @Override
        public double amount() {
            return 0;
        }

        @Override
        public double capacity() {
            return BUFFER;
        }

        @Override
        public double rating() {
            return 1;
        }

        @Override
        public boolean accepts(GasKind gas) {
            return rate.getAsDouble() > 0 && (kind == null || kind == gas) && held < BUFFER;
        }

        @Override
        public double insert(GasKind gas, double amount) {
            if (!accepts(gas)) return 0;
            long now = time.getAsLong();
            if (drawnTick != now) {
                drawnTick = now;
                drawn = 0;
            }
            double taken = Math.max(0, Math.min(amount, Math.min(BUFFER - held, rate.getAsDouble() - drawn)));
            if (taken <= 0) return 0;
            kind = gas;
            held += taken;
            drawn += taken;
            changed.run();
            return taken;
        }

        @Override
        public double extract(double amount) {
            return 0;
        }
    };

    /** The front face: gives gas out at a little over what stands on the line, takes none in. */
    final GasStorage outletStore = new GasStorage() {
        @Override
        public GasKind kind() {
            return held > 0 ? kind : null;
        }

        @Override
        public double amount() {
            return held;
        }

        @Override
        public double capacity() {
            return BUFFER;
        }

        @Override
        public double rating() {
            return 1;
        }

        @Override
        public double pressure() {
            return held > 0 ? outlet : 0;
        }

        @Override
        public boolean accepts(GasKind gas) {
            return false;
        }

        @Override
        public double insert(GasKind gas, double amount) {
            return 0;
        }

        @Override
        public double extract(double amount) {
            double given = Math.min(amount, held);
            held -= given;
            if (held <= 1e-9) {
                held = 0;
                kind = null;
            }
            if (given > 0) changed.run();
            return given;
        }
    };

    /** What a gas line finds on a side: the inlet behind, the outlet in front, nothing elsewhere. */
    public GasStorage storage(Direction facing, Direction side) {
        if (side == facing) return outletStore;
        if (side == facing.getOpposite()) return inlet;
        return null;
    }

    public double held() {
        return held;
    }

    public GasKind kind() {
        return kind;
    }

    /** Each tick it looks at the line in front, and presses a little harder than the highest pressure on it. */
    public void tick(ServerLevel level, BlockPos pos, Direction facing) {
        if (held <= 0) return;
        BlockPos front = pos.relative(facing);
        if (!(level.getBlockState(front).getBlock() instanceof GasLineBlock)) return;
        List<BlockPos> pipes = new ArrayList<>();
        List<GasStorage> stores = new ArrayList<>();
        GasNetwork.collect(level, front, pipes, stores);
        double highest = 0;
        for (GasStorage store : stores) if (store != outletStore) highest = Math.max(highest, store.pressure());
        outlet = highest + OVERPRESSURE;
    }

    /** What an empty hand reads off it: its pace a second, and what it holds. */
    public Component describe() {
        return Component.translatable("message.hardwrought.gas_pump.reads",
                String.format(java.util.Locale.ROOT, "%.1f", rate.getAsDouble() * 20),
                held <= 0 ? Component.translatable("message.hardwrought.gas_pump.nothing")
                        : Component.translatable(kind.translationKey()));
    }

    public void save(ValueOutput output) {
        if (held > 0 && kind != null) {
            output.putDouble("held", held);
            output.putString("gas", kind.getSerializedName());
        }
    }

    public void load(ValueInput input) {
        held = input.getDoubleOr("held", 0);
        String gas = input.getStringOr("gas", "");
        kind = null;
        for (GasKind value : GasKind.values()) if (value.getSerializedName().equals(gas)) kind = value;
        if (kind == null) held = 0;
    }
}
