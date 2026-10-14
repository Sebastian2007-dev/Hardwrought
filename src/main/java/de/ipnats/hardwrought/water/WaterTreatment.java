package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.chemistry.SaltPan;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * What is common to everything that cleans water: water goes into one tank, comes out of another a
 * little at a time, and is better for it. What it becomes, and how fast, is the stage's
 * ({@link Stage}). Water poured on top soaks in, and what has come through runs out underneath — into a
 * tank there, or out onto the ground — so a filter can simply stand under a spring. Pipes and buckets
 * fill the first tank and draw from the second as well.
 */
public final class WaterTreatment {
    /** What each tank holds. */
    public static final long CAPACITY = 4 * FluidConstants.BUCKET;
    private static final long MILLIBUCKET = FluidConstants.BUCKET / 1000;
    /** The most that soaks in from above, or runs out below, in a tick, in millibuckets. */
    private static final int FLOW_PER_TICK = 50;

    /** The stages of cleaning water, from a sand bed to a machine. */
    public enum Stage {
        /**
         * Sand and charcoal in a barrel: river and swamp water come out as fresh water, slowly, and
         * the charcoal is used up. Salt goes straight through sand.
         */
        SAND(50, 0, 4) {
            @Override
            public WaterQuality treat(WaterQuality in) {
                return switch (in) {
                    case SALT -> null;
                    case RIVER, SWAMP -> WaterQuality.FRESH;
                    default -> in;
                };
            }
        },
        /** A fired clay candle with charcoal in it: anything but seawater comes out purified, faster. */
        CERAMIC(150, 0, 16) {
            @Override
            public WaterQuality treat(WaterQuality in) {
                return in == WaterQuality.SALT ? null : WaterQuality.PURIFIED;
            }
        },
        /**
         * The machine: purifies anything at its rated voltage, seawater too, though desalting it takes
         * four times as long.
         */
        ELECTRIC(1000, 250, 0) {
            @Override
            public WaterQuality treat(WaterQuality in) {
                return WaterQuality.PURIFIED;
            }
        };

        private final int millibucketsPerSecond;
        private final int saltPerSecond;
        private final int bucketsPerCharcoal;

        Stage(int millibucketsPerSecond, int saltPerSecond, int bucketsPerCharcoal) {
            this.millibucketsPerSecond = millibucketsPerSecond;
            this.saltPerSecond = saltPerSecond;
            this.bucketsPerCharcoal = bucketsPerCharcoal;
        }

        /** What water of this kind comes out as, or null where this stage cannot clean it at all. */
        public abstract WaterQuality treat(WaterQuality in);

        /** How much it cleans a second at full strength, of water of this kind. */
        public int millibucketsPerSecond(WaterQuality in) {
            return in == WaterQuality.SALT ? saltPerSecond : millibucketsPerSecond;
        }

        /** How much one piece of charcoal lets through, in buckets; 0 for a stage that uses none. */
        public int bucketsPerCharcoal() {
            return bucketsPerCharcoal;
        }

        /** The most charcoal-worth a filter holds, in buckets. */
        public int mostBuckets() {
            return bucketsPerCharcoal * 4;
        }
    }

    private final Stage stage;
    private final Runnable changed;
    /** What the filter bed has left in it, in droplets; unused by a stage that needs none. */
    private long medium;
    /** Cleaned short of a whole millibucket, carried to the next tick. */
    private double carried;

    /** The dirty side: water of any kind goes in, nothing is drawn back out. */
    public final SingleVariantStorage<FluidVariant> input = new SingleVariantStorage<>() {
        @Override
        protected FluidVariant getBlankVariant() {
            return FluidVariant.blank();
        }

        @Override
        protected long getCapacity(FluidVariant variant) {
            return CAPACITY;
        }

        @Override
        protected boolean canInsert(FluidVariant variant) {
            return SaltPan.quality(variant) != null;
        }

        @Override
        protected boolean canExtract(FluidVariant variant) {
            return false;
        }

        @Override
        protected void onFinalCommit() {
            changed.run();
        }
    };

    /** The clean side: drawn from, never filled from outside. */
    public final SingleVariantStorage<FluidVariant> output = new SingleVariantStorage<>() {
        @Override
        protected FluidVariant getBlankVariant() {
            return FluidVariant.blank();
        }

        @Override
        protected long getCapacity(FluidVariant variant) {
            return CAPACITY;
        }

        @Override
        protected boolean canInsert(FluidVariant variant) {
            return false;
        }

        @Override
        protected void onFinalCommit() {
            changed.run();
        }
    };

    /** Both tanks as one, for a pipe: what it puts in goes to the dirty side, what it draws comes from the clean. */
    public final Storage<FluidVariant> both = new CombinedStorage<>(List.of(input, output));

    public WaterTreatment(Stage stage, Runnable changed) {
        this.stage = stage;
        this.changed = changed;
    }

    public Stage stage() {
        return stage;
    }

    public long mediumMillibuckets() {
        return medium / MILLIBUCKET;
    }

    public long inputMillibuckets() {
        return input.amount / MILLIBUCKET;
    }

    public long outputMillibuckets() {
        return output.amount / MILLIBUCKET;
    }

    public WaterQuality inputQuality() {
        return input.amount <= 0 ? null : SaltPan.quality(input.variant);
    }

    public WaterQuality outputQuality() {
        return output.amount <= 0 ? null : SaltPan.quality(output.variant);
    }

    /** Charcoal put into the bed. False where the bed is full, or this stage uses none. */
    public boolean addCharcoal() {
        long most = (long) stage.mostBuckets() * FluidConstants.BUCKET;
        if (stage.bucketsPerCharcoal() <= 0 || medium + (long) stage.bucketsPerCharcoal() * FluidConstants.BUCKET > most) {
            return false;
        }
        medium += (long) stage.bucketsPerCharcoal() * FluidConstants.BUCKET;
        changed.run();
        return true;
    }

    /**
     * One tick of cleaning at this share of full strength (1 for a filter, the voltage's share for the
     * machine). Moves what it may from the dirty side to the clean, changed as the stage changes it.
     * Returns what moved, in millibuckets.
     */
    public long work(double strength) {
        WaterQuality in = inputQuality();
        if (in == null || strength <= 0) return 0;
        WaterQuality out = stage.treat(in);
        if (out == null) return 0;
        FluidVariant cleaned = SaltPan.water(out);
        if (output.amount > 0 && !output.variant.equals(cleaned)) return 0;
        carried += stage.millibucketsPerSecond(in) / 20.0 * strength;
        long wanted = (long) carried;
        if (wanted <= 0) return 0;
        carried -= wanted;
        long droplets = Math.min(wanted * MILLIBUCKET, Math.min(input.amount, CAPACITY - output.amount));
        if (stage.bucketsPerCharcoal() > 0) droplets = Math.min(droplets, medium);
        if (droplets <= 0) return 0;
        input.amount -= droplets;
        if (input.amount <= 0) input.variant = FluidVariant.blank();
        output.variant = cleaned;
        output.amount += droplets;
        if (stage.bucketsPerCharcoal() > 0) medium -= droplets;
        changed.run();
        return droplets / MILLIBUCKET;
    }

    /**
     * Water standing on top soaks into the dirty side, as much as there is room for. Water of another
     * kind than what is already waiting waits on top until that has gone through.
     */
    public void soak(ServerLevel level, BlockPos pos) {
        BlockPos above = pos.above();
        int present = WaterStorage.amount(level, above);
        long room = (CAPACITY - input.amount) / MILLIBUCKET;
        if (present <= 0 || room <= 0) return;
        var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(level.getServer());
        FluidVariant water = SaltPan.water(runtime == null ? WaterQuality.FRESH : runtime.water().qualityAt(level, above));
        if (input.amount > 0 && !input.variant.equals(water)) return;
        int taken = (int) Math.min(Math.min(room, FLOW_PER_TICK), present);
        WaterStorage.setAmount(level, above, present - taken);
        WaterFlow.disturb(level, above);
        input.variant = water;
        input.amount += taken * MILLIBUCKET;
        changed.run();
    }

    /**
     * What has come through runs out underneath: into a tank there, if there is one, and otherwise out
     * onto the ground below as water of its new kind.
     */
    public void drain(ServerLevel level, BlockPos pos) {
        if (output.amount <= 0) return;
        BlockPos below = pos.below();
        Storage<FluidVariant> tank = FluidStorage.SIDED.find(level, below, Direction.UP);
        if (tank != null) {
            if (StorageUtil.move(output, tank, variant -> true, FLOW_PER_TICK * MILLIBUCKET, null) > 0) changed.run();
            return;
        }
        if (!WaterStorage.canHold(level, below)) return;
        int present = WaterStorage.amount(level, below);
        int poured = (int) Math.min(Math.min(WaterAmounts.BLOCK - present, FLOW_PER_TICK), output.amount / MILLIBUCKET);
        if (poured <= 0) return;
        WaterQualityStorage.add(level, below, outputQuality(), present, poured);
        WaterStorage.setAmount(level, below, present + poured);
        WaterFlow.disturb(level, below);
        output.amount -= poured * MILLIBUCKET;
        if (output.amount <= 0) output.variant = FluidVariant.blank();
        changed.run();
    }

    public void save(ValueOutput out) {
        if (input.amount > 0) {
            out.store("input", FluidVariant.CODEC, input.variant);
            out.putLong("input_amount", input.amount);
        }
        if (output.amount > 0) {
            out.store("output", FluidVariant.CODEC, output.variant);
            out.putLong("output_amount", output.amount);
        }
        if (medium > 0) out.putLong("medium", medium);
    }

    public void load(ValueInput in) {
        input.variant = in.read("input", FluidVariant.CODEC).orElse(FluidVariant.blank());
        input.amount = input.variant.isBlank() ? 0 : in.getLongOr("input_amount", 0);
        output.variant = in.read("output", FluidVariant.CODEC).orElse(FluidVariant.blank());
        output.amount = output.variant.isBlank() ? 0 : in.getLongOr("output_amount", 0);
        medium = in.getLongOr("medium", 0);
    }
}
