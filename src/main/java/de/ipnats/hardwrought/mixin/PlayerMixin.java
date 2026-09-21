package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.progression.BlockBreaking;
import de.ipnats.hardwrought.progression.BreakingVerdict;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Specification section 71.1, applied where breaking speed is actually decided.
 *
 * <p>This has to be common code rather than a server event: the client computes the same number to
 * draw the cracks, and a block that visibly breaks on the client and then does not break on the
 * server is worse than no rule at all. Both sides ask the same pure function.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void hardwrought$applyToolRules(BlockState state, CallbackInfoReturnable<Float> info) {
        float vanilla = info.getReturnValue();
        if (vanilla <= 0) return;
        Player player = (Player) (Object) this;
        if (player.isCreative()) return;
        BreakingVerdict verdict = BlockBreaking.verdict(player.getMainHandItem(), state);
        if (verdict == BreakingVerdict.PROPER || verdict == BreakingVerdict.LOOSE) return;
        info.setReturnValue((float) (vanilla * verdict.speedFactor()));
    }
}
