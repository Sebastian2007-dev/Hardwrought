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
 *
 * <p>Two rules meet here. A tool that is wrong for the material pays {@link BreakingVerdict}'s own
 * factor, and the right tool against material that has to be worked at all still pays
 * {@link BlockBreaking#laborFactor}. Nothing that is gathered rather than worked is touched by
 * either.
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements de.ipnats.hardwrought.survival.Crawling.Crawler {
    @org.spongepowered.asm.mixin.Unique
    private boolean hardwrought$crawling;

    @Override
    public boolean hardwrought$crawling() {
        return hardwrought$crawling;
    }

    @Override
    public void hardwrought$setCrawling(boolean crawling) {
        hardwrought$crawling = crawling;
    }

    /** A player who has chosen to crawl wants to be down on the ground; see {@code Crawling}. */
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getDesiredPose", at = @At("RETURN"))
    private net.minecraft.world.entity.Pose hardwrought$crawlWhenChosen(net.minecraft.world.entity.Pose pose) {
        return de.ipnats.hardwrought.survival.Crawling.desired((Player) (Object) this, pose);
    }

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void hardwrought$applyToolRules(BlockState state, CallbackInfoReturnable<Float> info) {
        float vanilla = info.getReturnValue();
        if (vanilla <= 0) return;
        Player player = (Player) (Object) this;
        if (player.isCreative()) return;
        BreakingVerdict verdict = BlockBreaking.verdict(player.getMainHandItem(), state);
        if (verdict == BreakingVerdict.LOOSE) return;
        double factor = verdict == BreakingVerdict.PROPER
                ? BlockBreaking.laborFactor(state) : verdict.speedFactor();
        if (factor >= 1.0) return;
        info.setReturnValue((float) (vanilla * factor));
    }
}
