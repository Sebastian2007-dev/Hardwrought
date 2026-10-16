package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.nether.CastObsidian;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Water on a lava source makes cast obsidian ({@link CastObsidian}), not obsidian: the mechanic stays, but
 * a portal frame can no longer be poured in place.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockCastObsidianMixin {
    @Redirect(method = "shouldSpreadLiquid", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/block/Blocks;OBSIDIAN:Lnet/minecraft/world/level/block/Block;"))
    private Block hardwrought$castObsidian() {
        return CastObsidian.BLOCK;
    }
}
