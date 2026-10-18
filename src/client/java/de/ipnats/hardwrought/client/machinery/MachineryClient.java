package de.ipnats.hardwrought.client.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.machinery.CogwheelBlock;
import de.ipnats.hardwrought.machinery.WaterWheelBlock;
import de.ipnats.hardwrought.machinery.WindmillBlock;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.core.Direction;

/** Hooks the machinery renderers up on the client. */
public final class MachineryClient {
    /** Small gears have eight teeth: half a tooth is this many degrees. */
    private static final float HALF_TOOTH = 22.5f;
    /** Sails five blocks across, drawn from a model three across. */
    private static final float SAIL_SCALE = 5.0f / 3.0f;

    private MachineryClient() { }

    public static void initialize() {
        BlockEntityRendererRegistry.register(ModBlockEntities.SHAFT, ShaftRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.HAND_CRANK, HandCrankRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.KINETIC, KineticPartRenderer.of(
                state -> state.getBlock() instanceof CogwheelBlock ? state.getValue(CogwheelBlock.AXIS) : Direction.Axis.Y,
                state -> state.getBlock() instanceof CogwheelBlock cog
                        ? (cog.isLarge() ? ModBlocks.LARGE_COGWHEEL_GEAR : ModBlocks.COGWHEEL_GEAR) : null,
                1.0f, HALF_TOOTH));
        BlockEntityRendererRegistry.register(ModBlockEntities.WATER_WHEEL, KineticPartRenderer.of(
                state -> state.getValue(WaterWheelBlock.AXIS), state -> ModBlocks.WATER_WHEEL_RIM, 1.0f, 0.0f));
        BlockEntityRendererRegistry.register(ModBlockEntities.WINDMILL, KineticPartRenderer.of(
                state -> state.getValue(WindmillBlock.FACING).getAxis(), state -> ModBlocks.WINDMILL_SAILS,
                SAIL_SCALE, 0.0f));
    }
}
