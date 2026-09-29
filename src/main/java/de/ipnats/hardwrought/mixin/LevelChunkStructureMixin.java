package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.building.BuildingPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every change of block on the server, whatever caused it — a player, an explosion, a flood, a
 * collapse. The statics must hear of a pillar mined away as surely as of one placed.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkStructureMixin {
    @Shadow @Final private Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void hardwrought$blockChanged(BlockPos pos, BlockState state, int flags,
                                          CallbackInfoReturnable<BlockState> callback) {
        BlockState before = callback.getReturnValue();
        if (before == null || !(level instanceof ServerLevel server)) return;
        BuildingPhysics.changed(server, (LevelChunk) (Object) this, pos, before, state);
    }
}
