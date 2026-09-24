package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Where the exact amount of water in a block lives.
 *
 * <p>Only blocks that are not completely full need storing: a full block is a thousand millibuckets
 * by definition, and so are the oceans and lakes a world was generated with. That keeps the stored
 * table to the moving edge of the water rather than to every block of it.
 *
 * <p>The table hangs off the chunk, so it is saved and loaded with the chunk it describes and never
 * grows into a world-sized structure.
 */
public final class WaterStorage {
    private static final Codec<WaterChunkData> TABLE_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
                    WaterChunkData::fromSerialized, WaterChunkData::serialized);
    public static final AttachmentType<WaterChunkData> PARTIAL_WATER =
            AttachmentRegistry.<WaterChunkData>create(Hardwrought.id("partial_water"), builder ->
                    builder.persistent(TABLE_CODEC).initializer(WaterChunkData::new));

    /**
     * What the client needs to draw water it cannot see the amount of: the visible step of every cell
     * that holds its water inside another block (a waterlogged stair, a kelp, a bubble column) and
     * is not full. Vanilla draws those as full blocks whatever they hold; with this they stand at the
     * same eighths as free water beside them.
     *
     * <p>Kept apart from {@link #PARTIAL_WATER} so that only the rare change of a visible step travels
     * to the client, not every millibucket moving through a lake. The value is replaced rather than
     * edited, which is what makes Fabric send it.
     */
    public static final AttachmentType<CarrierLevels> CARRIER_LEVELS =
            AttachmentRegistry.<CarrierLevels>create(Hardwrought.id("carrier_levels"), builder ->
                    builder.persistent(CarrierLevels.CODEC)
                            .syncWith(CarrierLevels.STREAM_CODEC, AttachmentSyncPredicate.all()));

    private WaterStorage() { }

    /**
     * Forces the attachment above to be registered during mod initialisation. An attachment type that
     * is only registered when the class happens to be touched is unknown while chunks are being read,
     * and every stored amount in them is discarded — the table would then only ever hold what the
     * current session put there.
     */
    public static void initialize() {
        // Loading the class registers the attachments.
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> refreshCarriers(chunk));
    }

    /**
     * Works out the visible steps of a chunk's water carriers from the amounts stored in it. Carriers
     * that were already partly filled before the steps were kept, or whose block changed while
     * nothing was watching, would otherwise be drawn full until their water next moved.
     */
    static void refreshCarriers(LevelChunk chunk) {
        WaterChunkData table = chunk.getAttached(PARTIAL_WATER);
        CarrierLevels current = chunk.getAttached(CARRIER_LEVELS);
        if (table == null && current == null) return;
        Long2ByteOpenHashMap levels = new Long2ByteOpenHashMap();
        if (table != null) {
            for (Long2IntMap.Entry entry : table.amounts.long2IntEntrySet()) {
                int amount = entry.getIntValue();
                if (amount <= 0 || amount >= WaterAmounts.BLOCK) continue;
                BlockState state = chunk.getBlockState(BlockPos.of(entry.getLongKey()));
                if (containsWater(state) && !isFreeWater(state)) {
                    levels.put(entry.getLongKey(), (byte) WaterAmounts.displayLevel(amount));
                }
            }
        }
        CarrierLevels fresh = new CarrierLevels(levels);
        if (current != null && current.levels.equals(levels)) return;
        chunk.setAttached(CARRIER_LEVELS, levels.isEmpty() ? null : fresh);
    }

    /** How much water stands in this block, in millibuckets. Zero when there is none. */
    public static int amount(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!containsWater(state)) return 0;
        // The table is asked first even for a block that looks full: a cell under pressure holds
        // more than a block and is drawn exactly the same way.
        // Reading a full lake must stay read-only. getAttachedOrCreate used to attach an empty map
        // to every inspected chunk, marking it dirty even though no precise amount existed there.
        WaterChunkData table = level.getChunkAt(pos).getAttached(PARTIAL_WATER);
        int stored = table == null ? WaterChunkData.MISSING : table.get(pos.asLong());
        if (stored != WaterChunkData.MISSING) return WaterAmounts.clamp(stored);
        if (isFreeWater(state)) {
            return state.getValue(LiquidBlock.LEVEL) == 0
                    ? WaterAmounts.BLOCK : WaterAmounts.nominalAmount(state);
        }
        // A newly placed waterlogged block, bubble column or water plant starts with one finite
        // block. Partial amounts are kept in the table and therefore returned above.
        return WaterAmounts.BLOCK;
    }

    /**
     * Sets the water in this block and updates what is drawn there. Zero removes it; a full block is
     * stored implicitly, so the table only ever holds the partial cells.
     */
    public static void setAmount(ServerLevel level, BlockPos pos, int millibuckets) {
        int amount = WaterAmounts.clamp(millibuckets);
        BlockState state = level.getBlockState(pos);
        if (amount <= 0) {
            removeWater(level, pos, state);
            store(level.getChunkAt(pos), pos, amount);
            showCarrier(level, pos, amount);
            // Water that is no longer there cannot still be salt water.
            WaterQualityStorage.clear(level, pos);
            return;
        }
        if (isWaterloggable(state)) {
            if (!isWaterlogged(state)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
            }
            store(level.getChunkAt(pos), pos, amount);
            showCarrier(level, pos, amount);
            return;
        }
        if (containsWater(state) && !isFreeWater(state)) {
            // Bubble columns and water plants keep their block while some water remains. Their exact
            // partial amount lives in the attachment; its visible step goes to the client separately.
            store(level.getChunkAt(pos), pos, amount);
            showCarrier(level, pos, amount);
            return;
        }
        if (!isFreeWater(state) && !state.isAir()) {
            // Water does not stop at a tuft of grass or a torch: it washes them out of its way.
            if (!state.is(BlockTags.WASHED_AWAY_BY_FLUIDS)) return;
            Block.dropResources(state, level, pos);
        }
        int display = WaterAmounts.displayLevel(amount);
        BlockState target = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, display);
        setWaterDisplay(level, pos, state, target);
        store(level.getChunkAt(pos), pos, amount);
        showCarrier(level, pos, amount);
    }

    /**
     * Updates both ends of one transfer together. Neighbouring cells are normally in the same
     * chunk, so this copies and replaces their persistent amount table once instead of twice.
     */
    public static void setAmounts(ServerLevel level, BlockPos firstPos, int firstMillibuckets,
                                  BlockPos secondPos, int secondMillibuckets) {
        int first = WaterAmounts.clamp(firstMillibuckets);
        int second = WaterAmounts.clamp(secondMillibuckets);
        updateDisplay(level, firstPos, first);
        updateDisplay(level, secondPos, second);
        showCarrier(level, firstPos, first);
        showCarrier(level, secondPos, second);

        LevelChunk firstChunk = level.getChunkAt(firstPos);
        boolean sameChunkPosition = firstPos.getX() >> 4 == secondPos.getX() >> 4
                && firstPos.getZ() >> 4 == secondPos.getZ() >> 4;
        LevelChunk secondChunk = sameChunkPosition ? firstChunk : level.getChunkAt(secondPos);
        if (firstChunk != secondChunk) {
            store(firstChunk, firstPos, first);
            store(secondChunk, secondPos, second);
            return;
        }

        WaterChunkData table = writableTable(firstChunk, first, second);
        if (table == null) return;
        boolean changed = updateEntry(table, firstPos, first);
        changed |= updateEntry(table, secondPos, second);
        if (changed) markChanged(firstChunk, table);
    }

    /** Free water is water that can move: a plain water block, not a waterlogged stair or a kelp. */
    public static boolean isFreeWater(BlockState state) {
        return state.is(Blocks.WATER) && state.hasProperty(LiquidBlock.LEVEL);
    }

    /** Any finite water carrier: plain water, waterlogged blocks, bubble columns or water plants. */
    public static boolean containsWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    private static boolean isWaterloggable(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED);
    }

    private static boolean isWaterlogged(BlockState state) {
        return state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false);
    }

    /**
     * True where water could stand: empty space, water that is not already full, or anything a fluid
     * washes away.
     *
     * <p>That last part is not a detail. A hillside is covered in grass tufts and flowers, and
     * treating them as walls left a bucket of water sitting in place on open ground because every
     * direction it could have gone was occupied by a plant. Vanilla keeps the list of what a fluid
     * removes in a tag, and that same tag is used here.
     *
     * <p>Waterloggable blocks can receive a finite amount without being replaced. Lava and other
     * solid blocks remain barriers.
     */
    public static boolean canHold(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (containsWater(state) || isWaterloggable(state)) return true;
        return state.is(BlockTags.WASHED_AWAY_BY_FLUIDS);
    }

    private static void updateDisplay(ServerLevel level, BlockPos pos, int amount) {
        BlockState state = level.getBlockState(pos);
        if (amount <= 0) {
            removeWater(level, pos, state);
            WaterQualityStorage.clear(level, pos);
            return;
        }
        if (isWaterloggable(state)) {
            if (!isWaterlogged(state)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
            }
            return;
        }
        if (containsWater(state) && !isFreeWater(state)) return;
        if (!isFreeWater(state) && !state.isAir()) {
            if (!state.is(BlockTags.WASHED_AWAY_BY_FLUIDS)) return;
            Block.dropResources(state, level, pos);
        }
        BlockState target = Blocks.WATER.defaultBlockState().setValue(
                LiquidBlock.LEVEL, WaterAmounts.displayLevel(amount));
        setWaterDisplay(level, pos, state, target);
    }

    /**
     * A change between two visible levels of the same water block is rendering data, not a new
     * physical block. Sending it to clients is sufficient; notifying every neighbouring block would
     * schedule another ring of fluid ticks and block-shape work. Entering or leaving a cell still
     * uses full updates so plants, falling blocks and waterlogging retain normal behaviour.
     */
    private static void setWaterDisplay(ServerLevel level, BlockPos pos, BlockState current,
                                        BlockState target) {
        if (target.equals(current)) return;
        int flags = isFreeWater(current) ? Block.UPDATE_CLIENTS : Block.UPDATE_ALL;
        level.setBlock(pos, target, flags);
    }

    private static void removeWater(ServerLevel level, BlockPos pos, BlockState state) {
        if (isWaterlogged(state)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
            return;
        }
        if (!containsWater(state)) return;
        // A bubble column has no item to drop. Kelp, seagrass and similar water carriers break when
        // the water supporting them is gone and retain their normal drops.
        if (!isFreeWater(state) && !state.is(Blocks.BUBBLE_COLUMN)) {
            Block.dropResources(state, level, pos);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * Records the visible step of a water carrier, or forgets it where the cell is full, empty or
     * plain water again. Only a change of step replaces the attachment and so reaches the client.
     */
    private static void showCarrier(ServerLevel level, BlockPos pos, int amount) {
        BlockState state = level.getBlockState(pos);
        boolean carrier = containsWater(state) && !isFreeWater(state);
        byte step = carrier && amount > 0 && amount < WaterAmounts.BLOCK
                ? (byte) WaterAmounts.displayLevel(amount) : 0;
        LevelChunk chunk = level.getChunkAt(pos);
        CarrierLevels current = chunk.getAttached(CARRIER_LEVELS);
        if (current == null && step == 0) return;
        long key = pos.asLong();
        if (current != null && current.level(key) == step) return;
        CarrierLevels next = (current == null ? CarrierLevels.EMPTY : current).with(key, step);
        chunk.setAttached(CARRIER_LEVELS, next.isEmpty() ? null : next);
    }

    /** The visible step of a partly filled water carrier, 0 where it is full, dry or free water. */
    public static byte carrierLevel(ServerLevel level, BlockPos pos) {
        CarrierLevels levels = level.getChunkAt(pos).getAttached(CARRIER_LEVELS);
        return levels == null ? 0 : levels.level(pos.asLong());
    }

    private static void store(LevelChunk chunk, BlockPos pos, int amount) {
        WaterChunkData table = chunk.getAttached(PARTIAL_WATER);
        if (table == null) {
            if (!isStored(amount)) return;
            table = new WaterChunkData();
            table.put(pos.asLong(), amount);
            chunk.setAttached(PARTIAL_WATER, table);
            return;
        }
        if (updateEntry(table, pos, amount)) markChanged(chunk, table);
    }

    private static WaterChunkData writableTable(LevelChunk chunk, int first, int second) {
        WaterChunkData table = chunk.getAttached(PARTIAL_WATER);
        if (table != null) return table;
        if (!isStored(first) && !isStored(second)) return null;
        table = new WaterChunkData();
        chunk.setAttached(PARTIAL_WATER, table);
        return table;
    }

    private static boolean updateEntry(WaterChunkData table, BlockPos pos, int amount) {
        // Empty cells and exactly full cells are represented completely by their block state.
        long key = pos.asLong();
        if (!isStored(amount)) return table.remove(key) != WaterChunkData.MISSING;
        return table.put(key, amount) != amount;
    }

    private static boolean isStored(int amount) {
        return amount > 0 && amount != WaterAmounts.BLOCK;
    }

    private static void markChanged(LevelChunk chunk, WaterChunkData table) {
        // Once every precise amount has settled back to an implicit full/empty block, discard the
        // attachment entirely instead of saving empty bookkeeping forever.
        if (table.isEmpty()) chunk.removeAttached(PARTIAL_WATER);
        else chunk.markUnsaved();
    }

    /**
     * The visible water step, 1 (almost full) to 7 (a film), of each partly filled carrier in a chunk.
     * Immutable: a change is a new value.
     */
    public static final class CarrierLevels {
        static final CarrierLevels EMPTY = new CarrierLevels(new Long2ByteOpenHashMap());
        static final Codec<CarrierLevels> CODEC = Codec.unboundedMap(Codec.STRING, Codec.BYTE).xmap(
                map -> {
                    Long2ByteOpenHashMap levels = new Long2ByteOpenHashMap(map.size());
                    map.forEach((key, value) -> levels.put(Long.parseLong(key), value.byteValue()));
                    return new CarrierLevels(levels);
                },
                carriers -> {
                    Map<String, Byte> map = new HashMap<>(carriers.levels.size());
                    for (Long2ByteMap.Entry entry : carriers.levels.long2ByteEntrySet()) {
                        map.put(Long.toString(entry.getLongKey()), entry.getByteValue());
                    }
                    return map;
                });
        static final StreamCodec<ByteBuf, CarrierLevels> STREAM_CODEC = StreamCodec.of(
                (buffer, carriers) -> {
                    VarInt.write(buffer, carriers.levels.size());
                    for (Long2ByteMap.Entry entry : carriers.levels.long2ByteEntrySet()) {
                        buffer.writeLong(entry.getLongKey());
                        buffer.writeByte(entry.getByteValue());
                    }
                },
                buffer -> {
                    int size = VarInt.read(buffer);
                    Long2ByteOpenHashMap levels = new Long2ByteOpenHashMap(size);
                    for (int i = 0; i < size; i++) levels.put(buffer.readLong(), buffer.readByte());
                    return new CarrierLevels(levels);
                });

        private final Long2ByteOpenHashMap levels;

        private CarrierLevels(Long2ByteOpenHashMap levels) {
            this.levels = levels;
            levels.defaultReturnValue((byte) 0);
        }

        /** The visible step at this position, or 0 where the carrier is drawn as vanilla draws it. */
        public byte level(long pos) {
            return levels.get(pos);
        }

        /** Every position this value knows about, for comparing an old value against a new one. */
        public LongSet positions() {
            return levels.keySet();
        }

        boolean isEmpty() {
            return levels.isEmpty();
        }

        CarrierLevels with(long pos, byte step) {
            Long2ByteOpenHashMap copy = new Long2ByteOpenHashMap(levels);
            if (step == 0) copy.remove(pos);
            else copy.put(pos, step);
            return new CarrierLevels(copy);
        }
    }

    /** Primitive runtime representation; its codec keeps the existing string-keyed save format. */
    public static final class WaterChunkData {
        private static final int MISSING = -1;
        private final Long2IntOpenHashMap amounts = new Long2IntOpenHashMap();

        private WaterChunkData() {
            amounts.defaultReturnValue(MISSING);
        }

        public static WaterChunkData fromSerialized(Map<String, Integer> serialized) {
            WaterChunkData data = new WaterChunkData();
            serialized.forEach((key, value) -> data.amounts.put(Long.parseLong(key), value.intValue()));
            return data;
        }

        private Map<String, Integer> serialized() {
            Map<String, Integer> result = new HashMap<>(amounts.size());
            for (Long2IntMap.Entry entry : amounts.long2IntEntrySet()) {
                result.put(Long.toString(entry.getLongKey()), entry.getIntValue());
            }
            return result;
        }

        private int get(long key) {
            return amounts.get(key);
        }

        private int put(long key, int value) {
            return amounts.put(key, value);
        }

        private int remove(long key) {
            return amounts.remove(key);
        }

        private boolean isEmpty() {
            return amounts.isEmpty();
        }
    }
}
