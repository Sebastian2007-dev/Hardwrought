package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.ipnats.hardwrought.mobs.TreeHeartBlock;
import de.ipnats.hardwrought.mobs.TreeHearts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * A creaking called by a tree heart is made of that tree's wood, and when it is hurt the heart
 * bleeds resin out along its own wood rather than pale oak. See {@link TreeHearts}.
 */
@Mixin(CreakingHeartBlockEntity.class)
public abstract class CreakingHeartBlockEntityMixin {
    /**
     * The logs resin creeps along while {@code spreadResin} runs. Its walk happens in static lambdas
     * that cannot see the heart, so the heart's wood is set aside here for them; null means pale oak.
     */
    @Unique
    private static @Nullable TagKey<Block> hardwrought$resinLogs;

    @Inject(method = "spawnProtector", at = @At("RETURN"))
    private static void hardwrought$creakingOfTheTree(ServerLevel level, CreakingHeartBlockEntity heart,
                                                      CallbackInfoReturnable<Creaking> callback) {
        Creaking creaking = callback.getReturnValue();
        BlockState state = heart.getBlockState();
        if (creaking != null && state.getBlock() instanceof TreeHeartBlock) {
            creaking.setAttached(TreeHearts.CREAKING_WOOD, state.getValue(TreeHeartBlock.WOOD));
        }
    }

    @Inject(method = "spreadResin", at = @At("HEAD"))
    private void hardwrought$resinOfTheTree(ServerLevel level, CallbackInfoReturnable<Optional<?>> callback) {
        BlockState state = ((CreakingHeartBlockEntity) (Object) this).getBlockState();
        hardwrought$resinLogs = state.getBlock() instanceof TreeHeartBlock
                ? state.getValue(TreeHeartBlock.WOOD).logs() : null;
    }

    @Inject(method = "spreadResin", at = @At("RETURN"))
    private void hardwrought$resinDone(ServerLevel level, CallbackInfoReturnable<Optional<?>> callback) {
        hardwrought$resinLogs = null;
    }

    @ModifyExpressionValue(method = {"lambda$spreadResin$0", "lambda$spreadResin$1"}, require = 2,
            at = @At(value = "FIELD", target = "Lnet/minecraft/tags/BlockTags;PALE_OAK_LOGS:Lnet/minecraft/tags/TagKey;"))
    private static TagKey<Block> hardwrought$resinAlongOwnWood(TagKey<Block> paleOak) {
        return hardwrought$resinLogs != null ? hardwrought$resinLogs : paleOak;
    }
}
