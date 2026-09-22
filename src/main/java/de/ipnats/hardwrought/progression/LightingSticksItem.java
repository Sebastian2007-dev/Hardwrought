package de.ipnats.hardwrought.progression;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Fire by friction: the only way to light the first campfire.
 *
 * <p>A campfire is laid, not lit — see {@code CampfireBlockMixin}. Somebody has to kneel down and
 * work a spindle against a board to start it, and that is what this is. Flint and steel still works
 * for anybody who has iron; this is what comes before iron.
 */
public class LightingSticksItem extends Item {
    public LightingSticksItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!lightable(state)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                1.0f, level.getRandom().nextFloat() * 0.4f + 0.8f);
        Player player = context.getPlayer();
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        ItemStack stack = context.getItemInHand();
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            // Working a spindle wears the board down; the sticks are spent long before a steel is.
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        return InteractionResult.SUCCESS;
    }

    /** A campfire that has been laid but not lit, and nothing else. */
    public static boolean lightable(BlockState state) {
        return state.getBlock() instanceof CampfireBlock
                && state.hasProperty(BlockStateProperties.LIT)
                && !state.getValue(BlockStateProperties.LIT)
                && !state.getValue(BlockStateProperties.WATERLOGGED);
    }
}
