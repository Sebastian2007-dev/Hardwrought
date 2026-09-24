package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Section 73: a leather belt, laid round two parallel shafts so that one turns the other.
 *
 * <p>Used on a shaft, it remembers that shaft; used on a second one beside it — same axis, up to
 * {@link ShaftBlockEntity#MAX_BELT_LENGTH} blocks away — it is laid round both. Used anywhere else,
 * or on the same shaft again, it lets go of the first.
 */
public class BeltItem extends Item {
    public BeltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();
        if (!(level.getBlockState(clicked).getBlock() instanceof ShaftBlock)) {
            stack.remove(ModDataComponents.BELT_START);
            return InteractionResult.PASS;
        }
        BlockPos start = stack.get(ModDataComponents.BELT_START);
        if (start == null || start.equals(clicked)) {
            stack.set(ModDataComponents.BELT_START, clicked.immutable());
            tell(player, "message.hardwrought.belt_started");
            return InteractionResult.SUCCESS;
        }
        if (!ShaftBlockEntity.canBelt(level, start, clicked)) {
            tell(player, "message.hardwrought.belt_cannot");
            return InteractionResult.FAIL;
        }
        if (level.getBlockEntity(start) instanceof ShaftBlockEntity first
                && level.getBlockEntity(clicked) instanceof ShaftBlockEntity second) {
            first.setBelt(clicked);
            second.setBelt(start);
            stack.remove(ModDataComponents.BELT_START);
            if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
            Kinetics.update(level, start);
            Kinetics.update(level, clicked);
            tell(player, "message.hardwrought.belt_laid");
        }
        return InteractionResult.SUCCESS;
    }

    private static void tell(Player player, String key) {
        if (player != null) player.sendOverlayMessage(Component.translatable(key));
    }
}
