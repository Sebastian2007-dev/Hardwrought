package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The short questions the rest of the mod asks about turning parts, answered by {@link Kinetics}.
 *
 * <p>This was once the whole of the mechanics: a shaft turned or it did not. The lines now have
 * speeds and strength; what remains here is the vocabulary the older callers use.
 */
public final class Driveline {
    /** How many parts one line may have; past this it stops carrying. */
    public static final int MAX_LENGTH = Kinetics.MAX_PARTS;

    private Driveline() { }

    /** Works out again every line that touches this position. */
    public static void update(Level level, BlockPos origin) {
        Kinetics.update(level, origin);
    }

    /** Works out again the line this block belongs to. */
    public static void refresh(Level level, BlockPos pos) {
        Kinetics.refresh(level, pos);
    }

    /** Whether the machine at this position is being turned at all. */
    public static boolean isDrivenInto(Level level, BlockPos machine) {
        return Kinetics.speed(level, machine) != 0.0f;
    }
}
