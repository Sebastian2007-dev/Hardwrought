package de.ipnats.hardwrought.mobs;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Mob specification § 20: phantoms punish neglected recovery, not staying awake. Vanilla sends them
 * after anyone who has not lain down for three days, which punishes exactly the player out in the
 * night. Here it is the other way round: a night slept badly adds to a sleep debt, a good night pays
 * it off, and phantoms come for a player deep in debt. Staying up adds nothing.
 *
 * <ul>
 *   <li>a good night (quality 70 % and up) pays off {@value #GOOD_NIGHT_RELIEF} nights of debt;</li>
 *   <li>a mediocre one (40 to 69 %) adds {@value #MEDIOCRE_NIGHT_DEBT};</li>
 *   <li>a poor one (below 40 %) adds a whole night.</li>
 * </ul>
 * A single bad night is never enough; phantoms may come from two nights of debt on, and more likely
 * the deeper it goes. A nap too short to count as a night changes nothing.
 */
public final class SleepDebt {
    public static final AttachmentType<Double> DEBT = AttachmentRegistry.<Double>create(
            Hardwrought.id("sleep_debt"), builder -> builder.persistent(Codec.DOUBLE).copyOnDeath());

    static final double GOOD_QUALITY = 0.7, POOR_QUALITY = 0.4;
    static final double GOOD_NIGHT_RELIEF = 1.5;
    static final double MEDIOCRE_NIGHT_DEBT = 0.25;
    static final double MAX_DEBT = 6;
    /** Debt below which phantoms never come. */
    public static final double PHANTOM_THRESHOLD = 1.5;
    /** Sleep shorter than this, in world ticks, is a nap and counts for nothing. */
    public static final long MIN_NIGHT_TICKS = 2400;
    /** Vanilla's phantom spawner only acts past this much time since rest. */
    private static final int VANILLA_THRESHOLD = 72_000;

    private SleepDebt() { }

    /** Loading the class registers the attachment before any player is read. */
    public static void initialize() { }

    public static double debt(ServerPlayer player) {
        return player.getAttachedOrElse(DEBT, 0.0);
    }

    /** The debt after one more night of this quality. */
    public static double afterNight(double debt, double quality) {
        double next = quality >= GOOD_QUALITY ? debt - GOOD_NIGHT_RELIEF
                : quality >= POOR_QUALITY ? debt + MEDIOCRE_NIGHT_DEBT : debt + 1;
        return Mth.clamp(next, 0, MAX_DEBT);
    }

    /** Called when a player gets up, with how well and how long they slept. */
    public static void wokeUp(ServerPlayer player, double quality, long sleptTicks) {
        if (sleptTicks < MIN_NIGHT_TICKS) return;
        double next = afterNight(debt(player), quality);
        if (next <= 0) player.removeAttached(DEBT);
        else player.setAttached(DEBT, next);
    }

    /**
     * What vanilla's phantom spawner reads in place of the time since rest. It only spawns past three
     * days, with a chance of {@code (value - 72000) / value}: nothing below the threshold, about one
     * in five at two nights of debt, and better than even from four on.
     */
    public static int phantomPressure(double debt) {
        if (debt < PHANTOM_THRESHOLD) return 0;
        return (int) (VANILLA_THRESHOLD * (1 + (debt - PHANTOM_THRESHOLD) * 0.5));
    }
}
