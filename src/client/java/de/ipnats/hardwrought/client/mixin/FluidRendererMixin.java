package de.ipnats.hardwrought.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import de.ipnats.hardwrought.client.environment.CarrierWater;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Water held inside another block is drawn at the height of the water it really holds.
 *
 * <p>Vanilla gives a waterlogged block the height of a full source, whatever the server says is in
 * it. Where the server has sent a partial step for the position, the surface is lowered to the same
 * eighth free water with that amount would show. Water standing on top still makes it a full block,
 * exactly as vanilla does for free water.
 */
@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixin {
    @ModifyReturnValue(method = "getHeight(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)F",
            at = @At("RETURN"))
    private float hardwrought$carrierHeight(float height, BlockAndTintGetter level, Fluid fluid, BlockPos pos,
                                            BlockState state, FluidState fluidState) {
        if (height <= 0.0f || height >= 1.0f || state.getBlock() instanceof LiquidBlock) return height;
        int step = CarrierWater.level(pos);
        if (step <= 0) return height;
        return (8 - step) / 9.0f;
    }
}
