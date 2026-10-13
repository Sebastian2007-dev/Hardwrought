package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A light that has burnt out: a torch gone to charcoal, a lantern gone dark. It gives no light, burns
 * nothing, and can be lit again — with flint and steel, a fire charge, or a burning torch held to it —
 * which brings back exactly the light it was. See {@link LightBurn}.
 */
public interface BurntLight {
    /** What it is lit back into when nothing better is remembered. */
    BlockState relit(BlockState burnt);

    /** Lights a burnt-out light again with what the player holds, if that can light anything. */
    static InteractionResult light(ItemStack stack, BlockState burnt, Level level, BlockPos pos, Player player, InteractionHand hand) {
        boolean flint = stack.is(Items.FLINT_AND_STEEL), charge = stack.is(Items.FIRE_CHARGE);
        boolean flame = stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.COPPER_TORCH);
        if (!flint && !charge && !flame) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        LightBurn.relight(server, pos, burnt, ((BurntLight) burnt.getBlock()).relit(burnt));
        if (flint) {
            stack.hurtAndBreak(1, player, hand);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1, level.getRandom().nextFloat() * 0.4f + 0.8f);
        } else if (charge) {
            if (!(player instanceof ServerPlayer sp && sp.isCreative())) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1, level.getRandom().nextFloat() * 0.4f + 0.8f);
        } else {
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4f, 1.6f);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
