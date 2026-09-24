package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.water.WaterQuality;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Item state Hardwrought adds. Registered before any item that carries it. */
public final class ModDataComponents {
    /**
     * Section 23.2: a container remembers what was put into it. Without this, water would silently
     * become clean the moment it left the block it was taken from.
     */
    public static final DataComponentType<WaterQuality> WATER_QUALITY = register("water_quality",
            DataComponentType.<WaterQuality>builder()
                    .persistent(WaterQuality.CODEC)
                    .networkSynchronized(StreamCodec.of(
                            (buffer, quality) -> ByteBufCodecs.VAR_INT.encode(buffer, quality.ordinal()),
                            buffer -> WaterQuality.byOrdinal(ByteBufCodecs.VAR_INT.decode(buffer))))
                    .build());

    /** Section 38: how hot a piece of metal is. Cools on its own; see {@code Heat}. */
    public static final DataComponentType<de.ipnats.hardwrought.smithing.Heat> HEAT = register("heat",
            DataComponentType.<de.ipnats.hardwrought.smithing.Heat>builder()
                    .persistent(de.ipnats.hardwrought.smithing.Heat.CODEC)
                    .networkSynchronized(de.ipnats.hardwrought.smithing.Heat.STREAM_CODEC)
                    .build());

    /** Sections 37 and 38: how well a forged piece was made, and how it was treated after. */
    public static final DataComponentType<de.ipnats.hardwrought.smithing.ForgeQuality> FORGE_QUALITY =
            register("forge_quality", DataComponentType.<de.ipnats.hardwrought.smithing.ForgeQuality>builder()
                    .persistent(de.ipnats.hardwrought.smithing.ForgeQuality.CODEC)
                    .networkSynchronized(de.ipnats.hardwrought.smithing.ForgeQuality.STREAM_CODEC)
                    .build());

    /** A piece half-way through the anvil, and what it is becoming. */
    public static final DataComponentType<de.ipnats.hardwrought.smithing.ForgingState> FORGING_STATE =
            register("forging_state", DataComponentType.<de.ipnats.hardwrought.smithing.ForgingState>builder()
                    .persistent(de.ipnats.hardwrought.smithing.ForgingState.CODEC)
                    .networkSynchronized(de.ipnats.hardwrought.smithing.ForgingState.STREAM_CODEC)
                    .build());

    /** The shaft a belt in hand was first laid against, waiting for the second. */
    public static final DataComponentType<net.minecraft.core.BlockPos> BELT_START =
            register("belt_start", DataComponentType.<net.minecraft.core.BlockPos>builder()
                    .persistent(net.minecraft.core.BlockPos.CODEC)
                    .networkSynchronized(net.minecraft.core.BlockPos.STREAM_CODEC)
                    .build());

    private ModDataComponents() { }

    private static <T> DataComponentType<T> register(String name, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Hardwrought.id(name), type);
    }

    public static void initialize() {
        // Loading the class registers the components above.
    }
}
