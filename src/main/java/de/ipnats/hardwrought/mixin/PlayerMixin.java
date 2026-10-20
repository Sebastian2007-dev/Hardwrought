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
        Player player = (Player) (Object) this;
        // Hovering on the mantle, sneak only sinks: the wearer stays upright.
        if (pose == net.minecraft.world.entity.Pose.CROUCHING && de.ipnats.hardwrought.witchking.MantleFlight.hovering(player)) {
            return net.minecraft.world.entity.Pose.STANDING;
        }
        return de.ipnats.hardwrought.survival.Crawling.desired(player, pose);
    }

    /** With adrenaline up an empty stomach does not stop a run; see {@code Adrenaline}. Both sides ask, so the client sprints too. */
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("RETURN"))
    private boolean hardwrought$sprintOnAdrenaline(boolean enough) {
        return enough || ((Player) (Object) this).hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.ADRENALINE);
    }

    /** The Witch King's mantle does not glide while it hovers, nor while it has given out; see {@code MantleFlight}. */
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "canGlide", at = @At("RETURN"))
    private boolean hardwrought$mantleHoldsOff(boolean can) {
        if (!can) return false;
        Player player = (Player) (Object) this;
        return !de.ipnats.hardwrought.witchking.MantleFlight.stalled(player) && !de.ipnats.hardwrought.witchking.MantleFlight.hovering(player);
    }

    /** In a fight hunger is felt less; see {@code Adrenaline}. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float hardwrought$lessHungerOnAdrenaline(float exhaustion) {
        return (Object) this instanceof net.minecraft.server.level.ServerPlayer player
                ? de.ipnats.hardwrought.survival.Adrenaline.maskHunger(player, exhaustion) : exhaustion;
    }

    /** Soulbound pieces are taken out before anything is dropped; see {@code Soulbound}. */
    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void hardwrought$keepSoulbound(net.minecraft.server.level.ServerLevel level, org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        if ((Object) this instanceof net.minecraft.server.level.ServerPlayer player) de.ipnats.hardwrought.enchanting.Soulbound.keep(player, level);
    }

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void hardwrought$applyToolRules(BlockState state, CallbackInfoReturnable<Float> info) {
        float vanilla = info.getReturnValue();
        if (vanilla <= 0) return;
        Player player = (Player) (Object) this;
        if (player.isCreative()) return;
        BreakingVerdict verdict = BlockBreaking.verdict(player.getMainHandItem(), state);
        if (verdict == BreakingVerdict.LOOSE) return;
        // A motor does the work of a powered drill or saw: the labour of the stroke is not the player's.
        if (verdict == BreakingVerdict.PROPER && player.getMainHandItem().getItem() instanceof de.ipnats.hardwrought.carried.PoweredItem
                && vanilla > 1.0f) {
            return;
        }
        double factor = verdict == BreakingVerdict.PROPER
                ? BlockBreaking.laborFactor(state) : verdict.speedFactor();
        if (factor >= 1.0) return;
        info.setReturnValue((float) (vanilla * factor));
    }
}
