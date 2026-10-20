package de.ipnats.hardwrought.witchking;

import de.ipnats.hardwrought.underworld.Underworld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A soul compass, found in the ruins of the Underworld: its needle turns to the nearest hall whose Witch
 * King still lives. His citadel lies buried at the bottom of the Ossuary, and the Ossuary is wide; this
 * is how a player who was not there when the last one fell finds a king, and with him a mantle, of
 * their own.
 *
 * <p>It works the way a compass bound to a lodestone does, so the client needs nothing of its own: the
 * server writes where it points into the stack's lodestone tracker, untracked so no lodestone is looked
 * for, and the vanilla compass needle turns to it. In the Underworld it looks again every
 * {@link #LOOK_TICKS}; beaten kings drop out ({@link FallenKings}). Anywhere else the place it points at
 * is in another world, and the needle spins, as a compass does in the Nether.
 */
public class SoulCompass extends Item {
    /** How often it looks again for the nearest living king, in ticks. */
    static final int LOOK_TICKS = 40;
    /** How far it looks, in grid squares of 256 blocks: about six thousand blocks. */
    static final int SQUARES = 24;

    public SoulCompass(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
        if (level.dimension() != Underworld.LEVEL || (level.getGameTime() + owner.getId()) % LOOK_TICKS != 0) return;
        point(stack, level, owner.blockPosition());
    }

    /** Turns the needle to the nearest living king from here, or lets it spin when there is none. Public for tests. */
    public static void point(ItemStack stack, ServerLevel level, BlockPos from) {
        aim(stack, level.dimension(), Citadel.nearestStanding(level, from, SQUARES));
    }

    /** Sets the needle on this hall in this world, or lets it spin with none. Public for tests. */
    public static void aim(ItemStack stack, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> world, BlockPos hall) {
        LodestoneTracker now = stack.get(DataComponents.LODESTONE_TRACKER);
        if (hall == null) {
            if (now != null) stack.remove(DataComponents.LODESTONE_TRACKER);
            return;
        }
        LodestoneTracker want = new LodestoneTracker(Optional.of(GlobalPos.of(world, hall)), false);
        if (!want.equals(now)) stack.set(DataComponents.LODESTONE_TRACKER, want);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(stack.has(DataComponents.LODESTONE_TRACKER)
                ? "item.hardwrought.soul_compass.pointing" : "item.hardwrought.soul_compass.spinning").withStyle(ChatFormatting.GRAY));
    }
}
