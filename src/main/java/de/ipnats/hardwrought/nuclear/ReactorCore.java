package de.ipnats.hardwrought.nuclear;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The physics of the core, kept apart from the block so it can be stepped and tested on its own.
 *
 * <h2>Neutrons</h2>
 * The chain reaction is followed by the point kinetics of one group of delayed neutrons, in the
 * prompt-jump approximation. The precursors {@code C} carry the slow part of the power; the power
 * itself follows the reactivity at once:
 * <pre>
 *   P = C · β / (β − ρ)          dC/dt = λ (P − C)
 * </pre>
 * so a little positive reactivity gives a period of (β − ρ) / (λ ρ) — a minute at a tenth of a dollar,
 * seconds at most of one — and a scram drops the power at once to a few percent, and then slowly. At
 * {@code ρ ≥ β} the delayed neutrons no longer matter: the core is prompt critical and the power runs
 * away in fractions of a second. A startup source keeps a loaded core from ever reading exactly zero.
 *
 * <h2>Reactivity</h2>
 * <pre>
 *   k = (0.12 + Σ fresh share of each assembly · 0.125 − 0.025 per breeder rod) · moderation
 *   ρ = (k − 1) / k − rods − xenon + α (T − 20 °C)
 * </pre>
 * Nine fresh assemblies give a k of 1.245 cold, eight 1.12; seven will not go critical. A burnt-out
 * assembly is still worth 82 % of a fresh one: enough that a full core holds full power, hot and with
 * its xenon, to the end of its fuel, and the rods have that much less to hold back as it burns. The water in the core is its moderator: without it the
 * neutrons are not slowed and the reaction stops, as in every water-moderated reactor. The temperature
 * coefficient is negative, so a hotter core holds itself back.
 *
 * <h2>Heat</h2>
 * The fission power, and the decay heat of the fission products, heat the core. Decay heat is followed
 * in three groups, a minute, twenty minutes and ten hours long, which together give 6.5 % of the power
 * at equilibrium and fall away after a shutdown roughly as the real curve does: a few percent at once,
 * one percent after an hour. The longest group fills only over hours of running, so a core that has run
 * long has the more to get rid of. The coolant takes heat out as steam above 100 °C, as much as its flow
 * allows. The main pumps give full flow; without them only natural circulation, enough for decay heat
 * but not for power. The vessel is well insulated: a core with no water left heats on its decay heat
 * alone until its cladding fails at {@value #CLADDING_FAILS} °C, and it melts at {@value #MELTS} °C.
 *
 * <h2>Rod control</h2>
 * The rods can be driven by hand, or the controller can be given a power to hold. Then it drives them
 * itself, as a reactor's automatic rod control does: out while the power is below the setpoint and the
 * reactivity small — a little larger while starting up from the source — and in when the power is above
 * it or rising too fast. The margin between critical and prompt critical is a couple of percent of rod
 * travel; by hand it is easily overshot.
 *
 * <h2>Xenon</h2>
 * Iodine-135 follows the power and decays into xenon-135, which swallows neutrons and is burnt away by
 * them. After a shutdown the xenon builds up for a while before it decays: a reactor shut down from full
 * power may not be restartable for some time, however far its rods are drawn.
 */
public final class ReactorCore {
    /** Thermal power at 100 %, in watts. */
    public static final double NOMINAL_WATTS = 120_000;
    public static final double BETA = 0.0065, LAMBDA = 0.08, PROMPT_GENERATION = 1.0e-3;
    /** The slowest the power can fall: the longest-lived precursors' period. */
    static final double SLOWEST_FALL = 1.0 / 80.0;
    static final double SOURCE = 1.0e-5;
    /** What the automatic control lets the reactivity rise to: while starting up, and near its setpoint. */
    static final double STARTUP_REACTIVITY = 0.003, RUNNING_REACTIVITY = 0.0015;
    /** k of the core's structure, and what each fresh assembly adds. */
    static final double BASE_K = 0.12, PER_ASSEMBLY = 0.125, BURNT_SHARE = 0.18, PER_BREEDER = 0.025;
    /** Reactivity of one control rod drive's rod, fully in. */
    public static final double ROD_WORTH = 0.05;
    /** Fraction the rods move a second under their drives, and fall a second when they are let go. */
    static final double ROD_SPEED = 0.02, ROD_DROP = 0.5;
    static final double TEMPERATURE_COEFFICIENT = -5.0e-5;
    static final double XENON_WORTH = 0.09, IODINE_TIME = 400, XENON_TIME = 600, XENON_BURN = 2.0;
    /** The three groups of decay heat: share of the power at equilibrium, and time, in seconds. */
    static final double[] DECAY_SHARE = {0.035, 0.02, 0.01}, DECAY_TIME = {60, 1200, 36_000};
    /**
     * Heat capacity of the core, J/K; what the coolant carries away per kelvin over boiling at full flow,
     * W/K; and what leaks through the insulation, W/K.
     */
    static final double HEAT_CAPACITY = 4_000, COOLING = 600, LOSS = 0.5;
    /** The most the power is followed to: past it the core has long since gone. */
    static final double MOST_POWER = 1.0e4;
    /** Flow without the pumps: natural circulation. */
    public static final double NATURAL_FLOW = 0.15;
    public static final double SCRAM_TEMPERATURE = 350, SCRAM_POWER = 1.15, CLADDING_FAILS = 1200, MELTS = 2200;
    /** Seconds a fresh assembly lasts at full power. */
    static final double LIFETIME_SECONDS = 7200;

    /** What holds a channel. */
    public enum Load { EMPTY, FUEL, BREEDER, SPENT }

    /** Why the rods were dropped. */
    public enum Trip { NONE, MANUAL, TEMPERATURE, POWER, LOST_POWER, LOST_FLOW, LOST_WATER, SIGNAL }

    // ---------------------------------------------------------------- state

    private double precursors;
    private double power;
    private double temperature = 20;
    private final double[] decay = new double[DECAY_SHARE.length];
    private double iodine, xenon;
    private double rods = 1.0, rodsSet = 1.0;
    /** The power the controller holds, or a negative number while the rods are driven by hand. */
    private double setpoint = -1;
    private Trip trip = Trip.NONE;
    private double reactivity, k;

    /** What the core was given for one step. */
    public record Conditions(Load[] loads, float[] burnup, int rodDrives, boolean drivesPowered, double flow,
                             boolean water, boolean signal) { }

    /** What a step did, for the block: heat carried off as steam, burnup per channel. */
    public record Result(double steamJoules, double heatWatts, double burnPerAssembly) { }

    public Result step(Conditions conditions, double seconds) {
        // The rods: under their drives towards where they are set; let go, they fall in.
        Trip tripped = check(conditions);
        if (tripped != Trip.NONE && trip == Trip.NONE) {
            trip = tripped;
            setpoint = -1;
        }
        if (trip == Trip.NONE && conditions.drivesPowered() && setpoint >= 0) regulate(seconds);
        if (trip != Trip.NONE || !conditions.drivesPowered()) {
            rodsSet = 1.0;
            rods = Math.min(1.0, rods + ROD_DROP * seconds);
        } else if (rods < rodsSet) {
            rods = Math.min(rodsSet, rods + ROD_SPEED * seconds);
        } else if (rods > rodsSet) {
            rods = Math.max(rodsSet, rods - ROD_SPEED * seconds);
        }

        int fuel = 0;
        double fresh = 0;
        int breeders = 0;
        for (int i = 0; i < conditions.loads().length; i++) {
            switch (conditions.loads()[i]) {
                case FUEL -> {
                    fuel++;
                    fresh += 1.0 - BURNT_SHARE * conditions.burnup()[i];
                }
                case BREEDER -> breeders++;
                default -> { }
            }
        }
        double moderation = conditions.water() ? 1.0 : 0.6;
        k = Math.max(0.0, (BASE_K + PER_ASSEMBLY * fresh - PER_BREEDER * breeders) * moderation);
        reactivity = (fuel == 0 ? -1.0 : (k - 1.0) / Math.max(k, 1e-6))
                - ROD_WORTH * conditions.rodDrives() * rods
                - XENON_WORTH * xenon
                + TEMPERATURE_COEFFICIENT * (temperature - 20);

        // The neutrons.
        if (fuel == 0) {
            precursors = 0;
            power = 0;
        } else if (reactivity >= BETA) {
            // Prompt critical: no waiting on the delayed neutrons any more.
            power = Math.min(MOST_POWER, Math.max(power, SOURCE) * Math.exp(Math.min(30.0, (reactivity - BETA) / PROMPT_GENERATION * seconds)));
            precursors = power;
        } else {
            double rate = LAMBDA * reactivity / (BETA - reactivity);
            if (rate < -SLOWEST_FALL) rate = -SLOWEST_FALL;
            precursors = Math.max(SOURCE * fuel / 9.0, precursors * Math.exp(rate * seconds));
            power = precursors * BETA / (BETA - reactivity);
        }

        // Iodine and xenon.
        iodine += (power - iodine) / IODINE_TIME * seconds;
        xenon += (iodine - xenon - XENON_BURN * power * xenon) / XENON_TIME * seconds;
        xenon = Math.max(0, xenon);

        // Heat.
        for (int group = 0; group < decay.length; group++) {
            decay[group] += (DECAY_SHARE[group] * power - decay[group]) * Math.min(1.0, seconds / DECAY_TIME[group]);
        }
        double heat = (power + decay()) * NOMINAL_WATTS;
        double carried = conditions.water() ? COOLING * conditions.flow() * Math.max(0, temperature - 100) : 0.0;
        double lost = LOSS * Math.max(0, temperature - 20);
        temperature += (heat - carried - lost) * seconds / HEAT_CAPACITY;
        temperature = Math.max(20, temperature);

        // Each assembly carries its share of the power: the fewer there are, the faster each burns.
        double burn = fuel == 0 ? 0.0 : Math.min(power, 2.0) * seconds / LIFETIME_SECONDS * 9.0 / fuel;
        return new Result(carried * seconds, heat, burn);
    }

    /** The automatic rod control: one step of it, on the readings of the step before. */
    private void regulate(double seconds) {
        double limit = power < 0.01 ? STARTUP_REACTIVITY : RUNNING_REACTIVITY;
        double move = ROD_SPEED * seconds;
        if (power > setpoint * 1.02 || reactivity > limit * 1.3) rodsSet = Math.min(1.0, rods + move);
        else if (power < setpoint * 0.98 && reactivity < limit) rodsSet = Math.max(0.0, rods - move);
        else rodsSet = rods;
    }

    /** What, if anything, drops the rods now. */
    private Trip check(Conditions conditions) {
        if (conditions.signal()) return Trip.SIGNAL;
        if (temperature > SCRAM_TEMPERATURE) return Trip.TEMPERATURE;
        if (power > SCRAM_POWER) return Trip.POWER;
        if (!conditions.drivesPowered() && rodsSet < 1.0) return Trip.LOST_POWER;
        if (!conditions.water() && power > 0.01) return Trip.LOST_WATER;
        if (conditions.flow() < 0.5 && power > 0.2) return Trip.LOST_FLOW;
        return Trip.NONE;
    }

    // ---------------------------------------------------------------- the operator

    public void scram() {
        if (trip == Trip.NONE) trip = Trip.MANUAL;
        setpoint = -1;
    }

    /** Clears a trip, once whatever caused it has passed. False if it still stands. */
    public boolean reset(Conditions conditions) {
        if (trip == Trip.NONE) return true;
        if (check(conditions) != Trip.NONE && check(conditions) != Trip.LOST_POWER) return false;
        trip = Trip.NONE;
        return true;
    }

    /** Sets where the rods are to go, from 0 drawn fully out to 1 fully in, by hand; nothing while tripped. */
    public void setRods(double target) {
        if (trip != Trip.NONE) return;
        setpoint = -1;
        rodsSet = Math.clamp(target, 0.0, 1.0);
    }

    /** Gives the automatic control a power to hold, as a share of the nominal; a negative one hands the rods back. */
    public void setSetpoint(double share) {
        if (trip != Trip.NONE) return;
        setpoint = share < 0 ? -1 : Math.clamp(share, 0.0, 1.0);
    }

    /** The power held automatically, or a negative number while the rods are driven by hand. */
    public double setpoint() {
        return setpoint;
    }

    // ---------------------------------------------------------------- readings

    public double power() { return power; }

    public double temperature() { return temperature; }

    /** Decay heat, as a share of the nominal power. */
    public double decay() {
        double sum = 0;
        for (double group : decay) sum += group;
        return sum;
    }

    public double xenon() { return xenon; }

    public double rods() { return rods; }

    public double rodsSet() { return rodsSet; }

    public Trip trip() { return trip; }

    public double reactivity() { return reactivity; }

    public double k() { return k; }

    /**
     * The reactor period: the seconds in which the power grows by a factor of e, while it is rising on
     * the delayed neutrons; zero while it is not rising, or rising faster than they allow.
     */
    public double period() {
        if (power <= 0 || reactivity <= 1e-6 || reactivity >= BETA) return 0.0;
        return (BETA - reactivity) / (LAMBDA * reactivity);
    }

    /** The saturation pressure of water at the core's temperature, in bar: what a gauge on the vessel would read. */
    public double pressure() {
        return temperature <= 100 ? 1.0 : Math.pow(temperature / 100.0, 4);
    }

    /** Whether the rods are in and the core shut down: safe to open for fuel. */
    public boolean shutDown() {
        return rods >= 0.99 && power < 0.01;
    }

    /** For tests and the debugger: the core at a temperature. */
    public void setTemperature(double celsius) {
        temperature = celsius;
    }

    public void save(ValueOutput output) {
        output.putDouble("precursors", precursors);
        output.putDouble("power", power);
        output.putDouble("temperature", temperature);
        for (int group = 0; group < decay.length; group++) output.putDouble("decay_" + group, decay[group]);
        output.putDouble("iodine", iodine);
        output.putDouble("xenon", xenon);
        output.putDouble("rods", rods);
        output.putDouble("rods_set", rodsSet);
        output.putString("trip", trip.name());
        output.putDouble("setpoint", setpoint);
    }

    public void load(ValueInput input) {
        precursors = input.getDoubleOr("precursors", 0);
        power = input.getDoubleOr("power", 0);
        temperature = input.getDoubleOr("temperature", 20);
        for (int group = 0; group < decay.length; group++) decay[group] = input.getDoubleOr("decay_" + group, 0);
        iodine = input.getDoubleOr("iodine", 0);
        xenon = input.getDoubleOr("xenon", 0);
        rods = input.getDoubleOr("rods", 1);
        rodsSet = input.getDoubleOr("rods_set", 1);
        setpoint = input.getDoubleOr("setpoint", -1);
        try {
            trip = Trip.valueOf(input.getStringOr("trip", "NONE"));
        } catch (IllegalArgumentException unknown) {
            trip = Trip.NONE;
        }
    }
}
