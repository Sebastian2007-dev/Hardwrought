package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Section 71: the first workbench is cut out of a standing tree.
 *
 * <p>Crouch against a log with a joiner's hatchet and keep the use button held: each repetition is
 * one stroke, the strokes add up on the log being worked, and they are forgotten the moment the
 * player turns to something else. When enough of them have landed, the log becomes a bench where it
 * stands, keeping the bark of its own tree.
 *
 * <p>Nothing else is worked out of a log here. Boards come from the ordinary recipe, and an axe used
 * on a log without crouching strips it exactly as it always did.
 */
public final class LogWorking {
    /** Strokes a bare edge needs. A tool's mining speed divides this down. */
    public static final int BASE_STROKES = 48;
    public static final int MIN_STROKES = 8;
    /** Strokes are forgotten after this long, so a bench is not cut over three sessions. */
    public static final long MEMORY_TICKS = 100;
    /** What one stroke costs the worker. */
    public static final double STAMINA_PER_STROKE = 0.35;
    /** What finishing a bench costs the hatchet. */
    public static final int TOOL_WEAR = 4;

    private record Progress(long position, int strokes, long lastTick) { }

    private static final Map<UUID, Progress> WORKING = new HashMap<>();

    private LogWorking() { }

    /** True where this block is timber that can be hewn at all. */
    public static boolean isTimber(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    /**
     * How many strokes this tool needs to cut a bench out of a log, or {@link Integer#MAX_VALUE}
     * where it cannot cut one at all. Pure, so the progression from one hatchet to the next is a
     * number that can be checked rather than a feeling.
     */
    public static int strokesNeeded(ItemStack tool, BlockState state) {
        if (!ToolCrafting.isCraftingTool(tool)) return Integer.MAX_VALUE;
        float speed = tool.getDestroySpeed(state);
        if (speed <= 1.0f) return Integer.MAX_VALUE;
        return Math.max(MIN_STROKES, Math.round(BASE_STROKES / speed));
    }

    /**
     * Whether this player is trying to cut a bench rather than to strip bark. Crouching is the
     * difference, and crouching against a log never strips it — see {@code ProgressionEvents}.
     */
    public static boolean hewsWorkbench(ServerPlayer player) {
        return player.isShiftKeyDown() && ToolCrafting.isCraftingTool(player.getMainHandItem());
    }

    /**
     * One stroke of the hatchet against a log. Returns true when the bench is finished, which is
     * also the only time anything is consumed.
     */
    public static boolean strike(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = player.getMainHandItem();
        int needed = strokesNeeded(tool, state);
        if (needed == Integer.MAX_VALUE) return false;

        long now = level.getGameTime();
        Progress previous = WORKING.get(player.getUUID());
        int strokes = previous != null && previous.position() == pos.asLong()
                && now - previous.lastTick() <= MEMORY_TICKS ? previous.strokes() + 1 : 1;

        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime != null) runtime.survival().spendStamina(player, STAMINA_PER_STROKE);
        level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 0.7f,
                0.8f + 0.4f * strokes / needed);

        if (strokes < needed) {
            WORKING.put(player.getUUID(), new Progress(pos.asLong(), strokes, now));
            player.sendSystemMessage(Component.translatable("message.hardwrought.hewing_workbench",
                    strokes * 100 / needed), true);
            return false;
        }

        // The log becomes the bench where it stands, in the wood it was cut from.
        WORKING.remove(player.getUUID());
        level.setBlockAndUpdate(pos, ModBlocks.HEWN_WORKBENCH.defaultBlockState()
                .setValue(HewnWorkbenchBlock.WOOD, HewnWood.of(state)));
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
        tool.hurtAndBreak(TOOL_WEAR, player, EquipmentSlot.MAINHAND);
        return true;
    }

    /** Forgets what a player was working on, so a disconnect leaves nothing behind. */
    public static void forget(UUID player) {
        WORKING.remove(player);
    }
}
