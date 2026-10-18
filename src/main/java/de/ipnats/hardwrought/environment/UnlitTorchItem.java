package de.ipnats.hardwrought.environment;

import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A torch as it comes out of the grid: a stick with its head dipped, ready to take a flame and giving
 * none. It can be set down as it is, on the ground or on a wall, and lit there like a burnt-out one; or it
 * can be lit in the hand, held to anything burning or with something to strike a flame in the other
 * hand. See {@link Torches}.
 */
public class UnlitTorchItem extends StandingAndWallBlockItem {
    public UnlitTorchItem(Block standing, Block wall, Properties properties) {
        super(standing, wall, Direction.DOWN, properties);
    }

    /** Held to a fire, lava, a lit furnace or a burning light it catches; against anything else it is set down. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        boolean flame = Torches.flame(level.getBlockState(context.getClickedPos()))
                || Torches.flame(level.getBlockState(context.getClickedPos().relative(context.getClickedFace())));
        if (player == null || !flame) return super.useOn(context);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Torches.lightOne(player, context.getHand());
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Lit in the hand, from flint and steel, the lighting sticks or a burning torch in the other hand. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack striker = player.getItemInHand(other);
        boolean flint = striker.is(Items.FLINT_AND_STEEL), sticks = striker.is(ModItems.LIGHTING_STICKS);
        boolean charge = striker.is(Items.FIRE_CHARGE), torch = Torches.isLit(striker);
        if (!flint && !sticks && !charge && !torch) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (flint || sticks) striker.hurtAndBreak(1, player, other);
        else if (charge && !(player instanceof ServerPlayer sp && sp.isCreative())) striker.shrink(1);
        return InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(Torches.lightOne(player, hand));
    }
}
