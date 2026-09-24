package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nailing the hewn bench together: bronze nails, and a hammer to drive them.
 *
 * <p>The same shape of work as hewing the bench out of the trunk. Hammer in hand, nails in the bag,
 * and the use button held on the bench: every repetition is one blow, the blows add up on the bench
 * being worked, and they are forgotten when the player turns to something else. When enough of them
 * have landed the bench is rebuilt as the nailed one, and only then are the nails used up.
 */
public final class NailDriving {
    /** Blows it takes. About three seconds of steady work. */
    public static final int STROKES = 16;
    /** Blows are forgotten after this long, so a bench is not nailed over three sessions. */
    public static final long MEMORY_TICKS = 60;
    public static final double STAMINA_PER_STROKE = 0.25;
    /** What finishing a bench costs the hammer. */
    public static final int TOOL_WEAR = 4;

    private record Progress(long position, int strokes, long lastTick) { }

    private static final Map<UUID, Progress> WORKING = new HashMap<>();

    private NailDriving() { }

    /**
     * One blow of the hammer against a hewn bench. Returns true when the bench is finished. Without
     * nails there is nothing to drive, and the player is told so rather than left hammering wood.
     */
    public static boolean strike(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
                                 InteractionHand hand) {
        ItemStack nails = findNails(player);
        if (nails.isEmpty()) {
            WORKING.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.hardwrought.hammer_needs_nails"), true);
            return false;
        }

        long now = level.getGameTime();
        Progress previous = WORKING.get(player.getUUID());
        int strokes = previous != null && previous.position() == pos.asLong()
                && now - previous.lastTick() <= MEMORY_TICKS ? previous.strokes() + 1 : 1;

        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime != null) runtime.survival().spendStamina(player, STAMINA_PER_STROKE);
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.25f,
                1.7f + level.getRandom().nextFloat() * 0.2f);

        if (strokes < STROKES) {
            WORKING.put(player.getUUID(), new Progress(pos.asLong(), strokes, now));
            player.sendSystemMessage(Component.translatable("message.hardwrought.nailing_workbench",
                    strokes * 100 / STROKES), true);
            return false;
        }

        WORKING.remove(player.getUUID());
        if (!HewnWorkbenchBlock.nail(level, pos, state, player, nails)) return false;
        ItemStack hammer = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) {
            hammer.hurtAndBreak(TOOL_WEAR, player, hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        return true;
    }

    /** The nails to use: the other hand first, then anywhere in the inventory. */
    private static ItemStack findNails(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(ModItems.BRONZE_NAILS)) return offhand;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.BRONZE_NAILS)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** How the compendium shows this: nails, the hammer that drives them, and the bench they go into. */
    public static java.util.List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> worldRecipes() {
        return java.util.List.of(new de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe(
                de.ipnats.hardwrought.Hardwrought.id("nailed_workbench_in_world"),
                java.util.List.of(java.util.List.of(new ItemStack(ModItems.BRONZE_NAILS)),
                        // Any hammer drives a nail; the plainest is shown first.
                        java.util.List.of(new ItemStack(ModItems.WOODEN_HAMMER), new ItemStack(ModItems.HAMMER),
                                new ItemStack(ModItems.IRON_HAMMER)),
                        java.util.List.of(new ItemStack(de.ipnats.hardwrought.core.registry.ModBlocks.HEWN_WORKBENCH))),
                new ItemStack(de.ipnats.hardwrought.core.registry.ModBlocks.NAILED_WORKBENCH), ItemStack.EMPTY));
    }

    /** Forgets what a player was working on, so a disconnect leaves nothing behind. */
    public static void forget(UUID player) {
        WORKING.remove(player);
    }
}
