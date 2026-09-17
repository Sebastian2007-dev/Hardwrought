package de.ipnats.hardwrought.core.debug;

import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Locale;

public final class VanillaDiagnostics {
    private VanillaDiagnostics() { }

    public static void register(DiagnosticRegistry registry) {
        registry.register("hardwrought:environment", DiagnosticRegistry.Channel.ENVIRONMENT, (level, pos) ->
                "biome=" + level.getBiome(pos).unwrapKey().map(key -> key.identifier().toString()).orElse("unknown")
                        + " sky=" + level.canSeeSky(pos) + " rain=" + level.isRainingAt(pos));
        registry.register("hardwrought:temperature", DiagnosticRegistry.Channel.TEMPERATURE, (level, pos) ->
                String.format(Locale.ROOT, "vanilla biome baseline=%.2f (not Celsius)", level.getBiome(pos).value().getBaseTemperature()));
        registry.register("hardwrought:water", DiagnosticRegistry.Channel.WATER, (level, pos) -> {
            var fluid = level.getFluidState(pos);
            return "at feet=" + BuiltInRegistries.FLUID.getKey(fluid.getType())
                    + " level=" + fluid.getAmount() + "/8 source=" + fluid.isSource();
        });
    }
}
