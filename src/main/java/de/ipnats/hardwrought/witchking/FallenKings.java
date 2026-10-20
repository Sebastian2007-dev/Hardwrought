package de.ipnats.hardwrought.witchking;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The halls whose Witch King has been beaten, by the middle of the hall, saved with the Underworld. A
 * throne knows it has gone cold, but only while its chunk is loaded; a soul compass has to know it of
 * halls a thousand blocks away, so the fall is written down here when it happens.
 */
public final class FallenKings extends SavedData {
    private static final Codec<FallenKings> CODEC = Codec.LONG.listOf().xmap(FallenKings::new, kings -> List.copyOf(kings.halls));
    public static final SavedDataType<FallenKings> TYPE = new SavedDataType<>(Hardwrought.id("fallen_kings"), FallenKings::new, CODEC, null);

    private final Set<Long> halls = new HashSet<>();

    private FallenKings() { }

    private FallenKings(List<Long> saved) {
        halls.addAll(saved);
    }

    private static FallenKings of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Whether the king of the hall with this middle has been beaten. */
    public static boolean fallen(ServerLevel level, BlockPos home) {
        return of(level).halls.contains(home.asLong());
    }

    /** The king of the hall with this middle has been beaten. */
    public static void fall(ServerLevel level, BlockPos home) {
        FallenKings kings = of(level);
        if (kings.halls.add(home.asLong())) kings.setDirty();
    }

    /** Takes a fall back out of the record; for tests and operators. */
    public static void raise(ServerLevel level, BlockPos home) {
        FallenKings kings = of(level);
        if (kings.halls.remove(home.asLong())) kings.setDirty();
    }
}
