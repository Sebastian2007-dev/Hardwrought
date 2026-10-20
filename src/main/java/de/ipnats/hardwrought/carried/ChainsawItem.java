package de.ipnats.hardwrought.carried;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A chainsaw (stage E4): a motor, a bar and a chain. It cuts wood faster than any axe, and a log cut at
 * the foot of a tree brings the whole tree down — every log joined to it, up to {@link #MOST_LOGS} —
 * for {@link #JOULES_PER_LOG} of charge a log. Sneaking, it cuts one log only. The chain blunts a little
 * with every log, and a new chain is clicked onto it.
 */
public class ChainsawItem extends PoweredItem {
    public static final int MOST_LOGS = 160;
    public static final double JOULES_PER_LOG = 150.0;
    public static final float SPEED = 12.0f;

    public ChainsawItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean usesPart() {
        return true;
    }

    @Override
    public boolean fits(ItemStack part) {
        return part.is(CarriedItems.SAW_CHAIN);
    }

    private static boolean works(ItemStack stack) {
        return Cells.charged(stack) && !Cells.part(stack).isEmpty();
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return works(stack) && state.is(BlockTags.MINEABLE_WITH_AXE) ? SPEED : 1.0f;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_AXE);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (!(level instanceof ServerLevel server) || !works(stack) || !state.is(BlockTags.MINEABLE_WITH_AXE)) return true;
        Cells.draw(stack, JOULES_PER_LOG);
        Cells.wearPart(stack);
        if (!state.is(BlockTags.LOGS) || owner.isShiftKeyDown()) return true;
        int felled = 0;
        for (BlockPos log : tree(server, pos)) {
            if (!works(stack)) break;
            server.destroyBlock(log, true, owner);
            Cells.draw(stack, JOULES_PER_LOG);
            Cells.wearPart(stack);
            felled++;
        }
        if (felled > 0) level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
        if (owner instanceof Player player) player.causeFoodExhaustion(0.02f * felled);
        return true;
    }

    /** The logs joined to one just cut, through faces and corners, above it and beside it — never below. */
    public static List<BlockPos> tree(ServerLevel level, BlockPos cut) {
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(cut);
        seen.add(cut);
        while (!queue.isEmpty() && logs.size() < MOST_LOGS) {
            BlockPos at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos next = at.offset(dx, dy, dz);
                if (next.getY() < cut.getY() || !seen.add(next) || !level.getBlockState(next).is(BlockTags.LOGS)) continue;
                logs.add(next);
                queue.add(next);
            }
        }
        return logs;
    }
}
