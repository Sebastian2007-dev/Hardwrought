package de.ipnats.hardwrought.mobs;

import de.ipnats.hardwrought.progression.BlockBreaking;
import de.ipnats.hardwrought.progression.BreakingVerdict;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mob specification §§ 5.1, 5.2 and 43: what a mob can break, and how fast.
 *
 * <p>A mob breaks what its tool is made for, by the same rules a player does: an axe opens wood, a
 * pickaxe stone, a shovel soil — and a tool too soft for a material does not break it at all, so a
 * flint pickaxe does not get through an iron wall. Without a fitting tool, only what bare hands can
 * handle: loose soil, weak vegetation, glass. Nothing holding a block entity — a chest, a machine —
 * is ever broken this way, and nothing when mob griefing is off.
 *
 * <p>Mobs are slower at it than a player ({@value #EFFORT} of the pace). Several working at the same
 * block share its progress, with diminishing returns: two are half again as fast as one, five about
 * two and a half times, twenty six times — a horde does not delete a wall.
 */
public final class Breaching {
    /** A mob's pace against a player's with the same tool. */
    static final double EFFORT = 0.25;
    /** Together, n mobs work n^{@value #GROUP_EXPONENT} times as fast as one. */
    static final double GROUP_EXPONENT = 0.6;
    /** A block left alone loses this much of its progress each tick. */
    static final double DECAY_PER_TICK = 0.005;

    private static final class Work {
        double rate;
        final List<Mob> workers = new ArrayList<>(2);
    }

    private static final class LevelState {
        final Long2DoubleOpenHashMap progress = new Long2DoubleOpenHashMap();
        final Long2ObjectOpenHashMap<Work> work = new Long2ObjectOpenHashMap<>();
    }

    private static final Map<ServerLevel, LevelState> states = new WeakHashMap<>();

    private Breaching() { }

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(Breaching::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> states.clear());
    }

    /**
     * Progress per tick one mob holding this makes on this block, as a share of the whole; zero where
     * it cannot break it at all.
     */
    public static double rate(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return 0;
        if (state.isAir() || state.hasBlockEntity() || state.getCollisionShape(level, pos).isEmpty()) return 0;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return 0;
        if (hardness == 0) return 1;
        boolean affinity = !tool.isEmpty() && tool.getDestroySpeed(state) > 1.0f;
        if (affinity) {
            // A tool too soft for the material makes no impression on it: § 5.1, "the material still matters".
            if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) return 0;
            return tool.getDestroySpeed(state) / hardness / 30.0 * EFFORT;
        }
        // Without a fitting tool, what a player could manage with bare hands, and no more.
        if (BlockBreaking.verdict(ItemStack.EMPTY, state) == BreakingVerdict.IMPOSSIBLE) return 0;
        return 1.0 / hardness / 100.0 * EFFORT;
    }

    /** One tick of this mob working at this block. The progress is applied at the end of the tick. */
    public static void work(ServerLevel level, BlockPos pos, Mob mob, double rate) {
        if (rate <= 0) return;
        Work work = states.computeIfAbsent(level, ignored -> new LevelState()).work
                .computeIfAbsent(pos.asLong(), ignored -> new Work());
        work.rate += rate;
        work.workers.add(mob);
    }

    /** How far this block has been broken, from 0 to 1. */
    public static double progress(ServerLevel level, BlockPos pos) {
        LevelState state = states.get(level);
        return state == null ? 0 : state.progress.get(pos.asLong());
    }

    private static void tick(ServerLevel level) {
        LevelState state = states.get(level);
        if (state == null) return;
        // Left alone, a half-broken block settles back a little; the crack stays until it is gone.
        state.progress.long2DoubleEntrySet().removeIf(entry -> {
            if (state.work.containsKey(entry.getLongKey())) return false;
            double left = entry.getDoubleValue() - DECAY_PER_TICK;
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (left <= 0) {
                level.destroyBlockProgress(crackId(pos), pos, -1);
                return true;
            }
            entry.setValue(left);
            return false;
        });
        for (var entry : state.work.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            Work work = entry.getValue();
            int n = work.workers.size();
            double before = state.progress.get(entry.getLongKey());
            double after = before + work.rate / n * Math.pow(n, GROUP_EXPONENT);
            BlockState block = level.getBlockState(pos);
            if (after >= 1) {
                state.progress.remove(entry.getLongKey());
                level.destroyBlockProgress(crackId(pos), pos, -1);
                if (block.getBlock() instanceof DoorBlock) level.levelEvent(1021, pos, 0);
                level.destroyBlock(pos, true, work.workers.getFirst());
                // Every tool at the block wears for it; a worn-out tool breaks, and with it the threat.
                for (Mob worker : work.workers) {
                    ItemStack tool = worker.getMainHandItem();
                    if (tool.isDamageableItem()) tool.hurtAndBreak(1, worker, EquipmentSlot.MAINHAND);
                }
                continue;
            }
            state.progress.put(entry.getLongKey(), after);
            int stage = (int) (after * 10);
            if (stage != (int) (before * 10)) level.destroyBlockProgress(crackId(pos), pos, stage);
            if (level.getGameTime() % 20 == 0) {
                if (block.getBlock() instanceof DoorBlock) level.levelEvent(1019, pos, 0);
                else level.playSound(null, pos, block.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.8f, 0.8f);
            }
        }
        state.work.clear();
    }

    /** One crack mark per block, whoever works it; kept apart from the ids of entities and statics. */
    private static int crackId(BlockPos pos) {
        return Integer.MIN_VALUE + (Long.hashCode(pos.asLong()) & 0x3fffffff);
    }
}
