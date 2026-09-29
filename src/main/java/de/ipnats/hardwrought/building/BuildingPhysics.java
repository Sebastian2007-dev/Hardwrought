package de.ipnats.hardwrought.building;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.debug.DiagnosticRegistry;
import de.ipnats.hardwrought.environment.Leaves;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Section 39 in the world: built blocks are judged by {@link Statics} whenever something next to them
 * changes, and whatever fails takes stress, cracks, and falls.
 *
 * <p>Nothing is scanned: a structure is only looked at when a block in or beside it is placed or
 * removed, and then only as far as it is connected. Stress then grows tick by tick on the few blocks
 * that are failing, so there is a moment to see the cracks and shore the structure up:
 * <ul>
 *   <li>an unsupported block falls after {@value #UNSUPPORTED_SECONDS} seconds;</li>
 *   <li>an overloaded block takes longer the less it is overloaded — twenty seconds just past its
 *   strength, two seconds at ten times it.</li>
 * </ul>
 * A block that was stressed and is relieved keeps its cracks but takes no more stress.
 */
public final class BuildingPhysics {
    /** Structures bigger than this are left alone rather than judged by part. */
    static final int STRUCTURE_LIMIT = 4096;
    static final int UNSUPPORTED_SECONDS = 2;
    /** Stress per second of an overloaded block, per unit of load over capacity. */
    private static final float OVERLOAD_RATE = 0.05f, MAX_OVERLOAD_RATE = 0.5f;
    private static final int MAX_COLLAPSES_PER_TICK = 64;
    private static final int MAX_STRUCTURES_PER_TICK = 16;
    /** A natural piece bigger than this is part of the land, and never comes loose. */
    static final int LOOSE_LIMIT = 1024;
    private static final int MAX_LOOSE_CHECKS_PER_TICK = 64;
    /** The client forgets a crack mark it has not heard of for 400 ticks. */
    private static final int CRACK_REFRESH_TICKS = 300;
    /** Fall damage of collapsing blocks, as for an anvil. */
    private static final float FALL_DAMAGE_PER_BLOCK = 2f;
    private static final int MAX_FALL_DAMAGE = 40;

    private static final class LevelState {
        final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
        /** Stress per tick of each failing block. */
        final Long2FloatOpenHashMap failing = new Long2FloatOpenHashMap();
        /** Every loaded block with a crack mark to keep showing. */
        final LongOpenHashSet cracked = new LongOpenHashSet();
        /** Where natural ground was taken away: what hung on it may have come loose. */
        final LongLinkedOpenHashSet loose = new LongLinkedOpenHashSet();
    }

    private static final Map<ServerLevel, LevelState> states = new WeakHashMap<>();

    private BuildingPhysics() { }

    public static void initialize() {
        BuiltBlocks.initialize();
        StructuralMaterials.initialize();
        ServerTickEvents.END_LEVEL_TICK.register(BuildingPhysics::tick);
        ServerChunkEvents.CHUNK_LOAD.register(BuildingPhysics::chunkLoaded);
        ServerChunkEvents.CHUNK_UNLOAD.register(BuildingPhysics::chunkUnloaded);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> states.clear());
        // A block a player breaks may have been what held a piece of natural ground: a tree cut at
        // the foot, a boulder undermined. Only breaks by a player are followed up, not every block
        // that changes: commands, worldgen and flowing water would otherwise flood the check.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            if (level instanceof ServerLevel server) state(server).loose.add(pos.asLong());
        });
    }

    private static LevelState state(ServerLevel level) {
        return states.computeIfAbsent(level, ignored -> new LevelState());
    }

    // ---------------------------------------------------------------- what changes the world

    /** A block was placed from an item: a player's hand or a dispenser. */
    public static void placed(ServerLevel level, BlockPos pos, BlockState state) {
        if (StructuralMaterials.of(level.getServer(), state).isEmpty()) return;
        BuiltBlocks.markBuilt(level, pos);
        LevelState levelState = state(level);
        levelState.dirty.add(pos.asLong());
        // Built beside a natural piece that is coming down, it may be what props the piece up.
        for (Direction direction : Direction.values()) {
            BlockPos other = pos.relative(direction);
            if (levelState.failing.containsKey(other.asLong()) && !BuiltBlocks.isBuilt(level, other)) {
                levelState.loose.add(pos.asLong());
                break;
            }
        }
    }

    /**
     * Ties a built block to its neighbours with a structural anchor. False when there is nothing to
     * tie: the block is terrain, carries nothing, or is already anchored.
     */
    public static boolean anchor(ServerLevel level, BlockPos pos) {
        if (StructuralMaterials.of(level.getServer(), structural(level, pos)).isEmpty()) return false;
        if (!BuiltBlocks.markAnchored(level, pos)) return false;
        state(level).dirty.add(pos.asLong());
        return true;
    }

    /**
     * A cell whose block was swapped for another without being built or taken down — a pane set into
     * a stair, or taken out again — keeps being built, and anchored, if it was.
     */
    public static void carryOver(ServerLevel level, BlockPos pos, boolean built, boolean anchored) {
        if (!built) return;
        BuiltBlocks.markBuilt(level, pos);
        if (anchored) BuiltBlocks.markAnchored(level, pos);
        state(level).dirty.add(pos.asLong());
    }

    /** The block the statics see here: in a shared cell, the half block that carries. */
    static BlockState structural(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SharedCellBlock && level.getBlockEntity(pos) instanceof SharedCellBlockEntity cell) {
            return cell.host();
        }
        return state;
    }

    /** A block fell and landed where a built block had fallen from: it is still built. */
    public static void landed(ServerLevel level, BlockPos pos, BlockState state) {
        placed(level, pos, state);
    }

    /** Any change of block in a loaded chunk on the server, however it came about. */
    public static void changed(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState before, BlockState after) {
        if (before.getBlock() == after.getBlock()) return;
        if (!BuiltBlocks.hasBuilt(chunk) && !nearChunkEdge(pos)) return;
        boolean wasBuilt = BuiltBlocks.isBuilt(level, pos);
        if (wasBuilt) {
            BuiltBlocks.unmark(chunk, pos);
            LevelState state = state(level);
            state.failing.remove(pos.asLong());
            if (state.cracked.remove(pos.asLong())) level.destroyBlockProgress(crackId(pos), pos, -1);
        }
        boolean touchesBuilt = wasBuilt;
        for (Direction direction : Direction.values()) {
            if (touchesBuilt) break;
            touchesBuilt = BuiltBlocks.isBuilt(level, pos.relative(direction));
        }
        if (touchesBuilt) state(level).dirty.add(pos.asLong());
    }

    private static boolean nearChunkEdge(BlockPos pos) {
        int x = pos.getX() & 15, z = pos.getZ() & 15;
        return x == 0 || x == 15 || z == 0 || z == 15;
    }

    // ---------------------------------------------------------------- every tick

    private static void tick(ServerLevel level) {
        if (!level.getServer().tickRateManager().runsNormally()) return;
        LevelState state = states.get(level);
        if (state == null) return;
        judgeLoose(level, state);
        judgeDirty(level, state);
        growStress(level, state);
        if (level.getGameTime() % CRACK_REFRESH_TICKS == 0) refreshCracks(level, state);
    }

    private static void judgeDirty(ServerLevel level, LevelState state) {
        LongSet judged = new LongOpenHashSet();
        int structures = 0;
        while (!state.dirty.isEmpty() && structures < MAX_STRUCTURES_PER_TICK) {
            BlockPos pos = BlockPos.of(state.dirty.removeFirstLong());
            if (judged.contains(pos.asLong())) continue;
            List<BlockPos> starts = new ArrayList<>();
            if (BuiltBlocks.isBuilt(level, pos)) starts.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos other = pos.relative(direction);
                if (!judged.contains(other.asLong()) && BuiltBlocks.isBuilt(level, other)) starts.add(other);
            }
            if (starts.isEmpty()) continue;
            structures++;
            Map<BlockPos, Statics.Result> results = Statics.solve(view(level), starts, STRUCTURE_LIMIT);
            if (results == null) {
                Hardwrought.LOGGER.debug("Structure at {} exceeds {} blocks; not judged", pos, STRUCTURE_LIMIT);
                continue;
            }
            results.forEach((member, result) -> {
                judged.add(member.asLong());
                float rate = stressPerTick(result);
                if (rate > 0) state.failing.put(member.asLong(), rate);
                else state.failing.remove(member.asLong());
            });
        }
    }

    /**
     * Natural blocks next to where ground was taken away. Each is followed through the natural blocks
     * it touches: a piece that runs on past {@value #LOOSE_LIMIT} blocks is part of the land and
     * stays, however it has been dug at. A smaller piece is on its own - a tree cut at the foot, a
     * boulder undermined - and is judged like a building: it holds only if something built props it
     * up, and otherwise cracks and falls.
     */
    private static void judgeLoose(ServerLevel level, LevelState state) {
        if (state.loose.isEmpty()) return;
        // Within one tick, what was found to be land stays land: an explosion asks the same thing many times.
        LongOpenHashSet land = new LongOpenHashSet();
        LongOpenHashSet judged = new LongOpenHashSet();
        int checks = 0;
        while (!state.loose.isEmpty() && checks < MAX_LOOSE_CHECKS_PER_TICK) {
            BlockPos removed = BlockPos.of(state.loose.removeFirstLong());
            checks++;
            for (Direction direction : Direction.values()) {
                BlockPos start = removed.relative(direction);
                if (judged.contains(start.asLong()) || land.contains(start.asLong())) continue;
                LongOpenHashSet piece = loosePiece(level, start, land);
                if (piece == null) continue;
                judged.addAll(piece);
                judgePiece(level, state, piece);
            }
        }
    }

    /** The natural piece this block belongs to, or null where it is land or not natural ground. */
    private static LongOpenHashSet loosePiece(ServerLevel level, BlockPos start, LongOpenHashSet land) {
        if (!naturalAt(level, start)) return null;
        LongOpenHashSet piece = new LongOpenHashSet();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        piece.add(start.asLong());
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                long packed = next.asLong();
                if (piece.contains(packed)) continue;
                // Bedrock's floor, the unloaded world and anything already known to be land all hold.
                if (next.getY() < level.getMinY() || BuiltBlocks.loadedChunk(level, next) == null || land.contains(packed)) {
                    land.addAll(piece);
                    return null;
                }
                if (!naturalAt(level, next)) continue;
                piece.add(packed);
                if (piece.size() > LOOSE_LIMIT) {
                    land.addAll(piece);
                    return null;
                }
                queue.add(next);
            }
        }
        return piece;
    }

    /** Judges a loose natural piece with whatever is built against it, its blocks as members. */
    private static void judgePiece(ServerLevel level, LevelState state, LongOpenHashSet piece) {
        Statics.View world = view(level);
        Statics.View view = pos -> {
            if (!piece.contains(pos.asLong())) return world.at(pos);
            return StructuralMaterials.of(level.getServer(), level.getBlockState(pos))
                    .<Statics.Cell>map(member -> member).orElse(Statics.EMPTY);
        };
        List<BlockPos> starts = new ArrayList<>();
        piece.forEach(packed -> starts.add(BlockPos.of(packed)));
        Map<BlockPos, Statics.Result> results = Statics.solve(view, starts, STRUCTURE_LIMIT);
        if (results == null) return;
        results.forEach((member, result) -> {
            float rate = stressPerTick(result);
            if (rate > 0) state.failing.put(member.asLong(), rate);
            else state.failing.remove(member.asLong());
        });
    }

    /** Natural ground as the statics count it: a whole block, not leaves. */
    private static boolean natural(BlockState state) {
        return !state.isAir() && !Leaves.isLeaves(state)
                && state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static boolean naturalAt(ServerLevel level, BlockPos pos) {
        return natural(level.getBlockState(pos)) && !BuiltBlocks.isBuilt(level, pos);
    }

    /** Stress per tick for a verdict; zero for a block that holds. */
    static float stressPerTick(Statics.Result result) {
        if (!result.supported()) return 1f / (UNSUPPORTED_SECONDS * 20f);
        double utilisation = result.utilisation();
        if (utilisation <= 1) return 0;
        return Mth.clamp((float) (OVERLOAD_RATE * utilisation), OVERLOAD_RATE, MAX_OVERLOAD_RATE) / 20f;
    }

    private static void growStress(ServerLevel level, LevelState state) {
        if (state.failing.isEmpty()) return;
        List<BlockPos> collapsing = new ArrayList<>();
        for (Long2FloatMap.Entry entry : state.failing.long2FloatEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            LevelChunk chunk = BuiltBlocks.loadedChunk(level, pos);
            if (chunk == null) continue;
            float before = BuiltBlocks.stress(level, pos);
            float after = before + entry.getFloatValue();
            if (after >= 1) {
                collapsing.add(pos);
                continue;
            }
            BuiltBlocks.setStress(chunk, pos, after);
            state.cracked.add(pos.asLong());
            if (crackStage(after) != crackStage(before)) {
                level.destroyBlockProgress(crackId(pos), pos, crackStage(after));
                BlockState block = level.getBlockState(pos);
                level.playSound(null, pos, block.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.7f, 0.5f);
            }
        }
        // Lowest first: a column falls together, each block into the room the one below it left.
        // Taken top first, every block but the lowest would still stand on another and be crushed.
        collapsing.sort(java.util.Comparator.comparingInt(BlockPos::getY));
        collapsing.stream().limit(MAX_COLLAPSES_PER_TICK).forEach(pos -> collapse(level, state, pos));
    }

    private static void collapse(ServerLevel level, LevelState state, BlockPos pos) {
        state.failing.remove(pos.asLong());
        state.cracked.remove(pos.asLong());
        level.destroyBlockProgress(crackId(pos), pos, -1);
        BlockState block = level.getBlockState(pos);
        level.playSound(null, pos, block.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1f, 0.6f);
        // Mob specification section 37: a structure coming down is felt far off, by a warden too.
        level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.BLOCK_DESTROY, pos);
        // A block with something under it cannot fall: it is crushed where it stands. As a falling
        // block it would land in its own place again, whole and uncracked, and fail over and over.
        // A falling block cannot carry a chest's contents safely either; it breaks and spills them.
        if (block.hasBlockEntity() || !FallingBlock.isFree(level.getBlockState(pos.below()))) {
            level.destroyBlock(pos, true);
            return;
        }
        // The block is removed here, which reaches changed() and judges its neighbours again: a
        // collapse spreads through whatever depended on it.
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, block);
        falling.setHurtsEntities(FALL_DAMAGE_PER_BLOCK, MAX_FALL_DAMAGE);
    }

    private static void refreshCracks(ServerLevel level, LevelState state) {
        state.cracked.removeIf(packed -> {
            BlockPos pos = BlockPos.of(packed);
            float stress = BuiltBlocks.stress(level, pos);
            if (stress <= 0) return true;
            level.destroyBlockProgress(crackId(pos), pos, crackStage(stress));
            return false;
        });
    }

    static int crackStage(float stress) {
        return stress <= 0 ? -1 : Math.min(9, (int) (stress * 10));
    }

    /** A breaker id no entity has: crack marks are keyed by breaker, one per block here. */
    private static int crackId(BlockPos pos) {
        return -1 - (Long.hashCode(pos.asLong()) & 0x3fffffff);
    }

    // ---------------------------------------------------------------- chunks

    private static void chunkLoaded(ServerLevel level, LevelChunk chunk, boolean generated) {
        long[] stressed = BuiltBlocks.stressed(chunk);
        if (stressed.length == 0) return;
        LevelState state = state(level);
        for (long packed : stressed) {
            state.cracked.add(packed);
            // Whether it is still failing is not saved; judging it again finds out - a built block
            // through its structure, a natural one through the piece it belongs to.
            state.dirty.add(packed);
            state.loose.add(packed);
        }
    }

    private static void chunkUnloaded(ServerLevel level, LevelChunk chunk) {
        LevelState state = states.get(level);
        if (state == null) return;
        ChunkPos at = chunk.getPos();
        java.util.function.LongPredicate inChunk = packed -> {
            BlockPos pos = BlockPos.of(packed);
            return SectionPos.blockToSectionCoord(pos.getX()) == at.x()
                    && SectionPos.blockToSectionCoord(pos.getZ()) == at.z();
        };
        state.failing.keySet().removeIf(inChunk);
        state.cracked.removeIf(inChunk);
    }

    // ---------------------------------------------------------------- the world as Statics sees it

    static Statics.View view(ServerLevel level) {
        Map<BlockPos, Statics.Cell> seen = new HashMap<>();
        return pos -> seen.computeIfAbsent(pos.immutable(), at -> cell(level, at));
    }

    private static Statics.Cell cell(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY()) return Statics.ANCHOR;
        if (pos.getY() > level.getMaxY()) return Statics.EMPTY;
        // Past the loaded world nothing is known; assuming it holds never drops a building at a border.
        if (BuiltBlocks.loadedChunk(level, pos) == null) return Statics.ANCHOR;
        BlockState state = structural(level, pos);
        if (BuiltBlocks.isBuilt(level, pos)) {
            Optional<Statics.Member> member = StructuralMaterials.of(level.getServer(), state);
            if (member.isPresent() && BuiltBlocks.isAnchored(level, pos)) return member.get().withAnchor();
            return member.<Statics.Cell>map(value -> value).orElse(Statics.EMPTY);
        }
        if (Leaves.isLeaves(state) || !state.isCollisionShapeFullBlock(level, pos)) return Statics.EMPTY;
        return Statics.ANCHOR;
    }

    // ---------------------------------------------------------------- diagnostics

    public static void registerDiagnostics(DiagnosticRegistry registry) {
        registry.register("hardwrought:statics", DiagnosticRegistry.Channel.STRUCTURE, BuildingPhysics::inspect);
    }

    private static String inspect(ServerLevel level, BlockPos pos) {
        BlockState state = structural(level, pos);
        String material = StructuralMaterials.materialId(level.getServer(), state).map(Object::toString).orElse("none");
        if (!BuiltBlocks.isBuilt(level, pos)) {
            return cell(level, pos) instanceof Statics.Anchor
                    ? "natural terrain, anchored | material=" + material
                    : "carries nothing | material=" + material;
        }
        Map<BlockPos, Statics.Result> results = Statics.solve(view(level), List.of(pos), STRUCTURE_LIMIT);
        if (results == null) return "built | structure larger than " + STRUCTURE_LIMIT + " blocks, not judged";
        Statics.Result result = results.get(pos);
        if (result == null) return "built | material=" + material + " without structural data";
        float stress = BuiltBlocks.stress(level, pos);
        float rate = stressPerTick(result);
        return String.format(Locale.ROOT,
                "built %s%s | support=%.0f%% held=%s | load=%.1f kN of %s | stress=%.0f%%%s | structure=%d blocks",
                material, BuiltBlocks.isAnchored(level, pos) ? " (anchored)" : "", result.support() * 100, result.held().name().toLowerCase(Locale.ROOT),
                result.loadN() / 1000,
                result.capacityN() == Double.POSITIVE_INFINITY ? "span" : String.format(Locale.ROOT, "%.1f kN", result.capacityN() / 1000),
                stress * 100,
                rate > 0 ? String.format(Locale.ROOT, " | failing, falls in %.1fs", (1 - stress) / rate / 20) : "",
                results.size());
    }
}
