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

    private ModDataComponents() { }

    private static <T> DataComponentType<T> register(String name, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Hardwrought.id(name), type);
    }

    public static void initialize() {
        // Loading the class registers the components above.
    }
}
