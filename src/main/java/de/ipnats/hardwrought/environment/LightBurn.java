package de.ipnats.hardwrought.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Lights that burn down (specification section 20). A torch lasts a day, a lantern three; then it
 * goes out and has to be lit again. Every fire-lit light in the world burns — the ones a player sets
 * and the ones a village was built with — so a settlement nobody tends goes dark.
 *
 * <p>When a light was lit is kept per position with its chunk, so it burns on while nobody is near and
 * goes out the next time anyone comes by. A light the world generated starts burning when it is first
 * found. The check rides the random ticks every loaded block may get (about one a minute), so a light
 * goes out within a minute or so of its time.
 *
 * <p>Each kind of light is one line in {@link #BURNERS}: what it is, how long it burns, what it becomes.
 */
public final class LightBurn {
    /** A day, in ticks. */
    private static final long DAY = 24000;

    /**
     * One kind of light.
     *
     * @param burning which states are this light, lit
     * @param ticks how long it burns
     * @param out what it is once it has burnt out
     */
    record Burner(String name, Predicate<BlockState> burning, long ticks, UnaryOperator<BlockState> out) { }

    static final List<Burner> BURNERS = List.of(
            new Burner("wall_torch", s -> s.getBlock() instanceof WallTorchBlock && !(s.getBlock() instanceof BurntLight), DAY,
                    s -> ModBlocks.BURNT_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, s.getValue(WallTorchBlock.FACING))),
            new Burner("torch", s -> s.getBlock() instanceof TorchBlock && !(s.getBlock() instanceof BurntLight), DAY,
                    s -> ModBlocks.BURNT_TORCH.defaultBlockState()),
            new Burner("lantern", s -> s.getBlock() instanceof LanternBlock && !(s.getBlock() instanceof BurntLight), 3 * DAY,
                    s -> ModBlocks.BURNT_LANTERN.defaultBlockState()
                            .setValue(BurntLanternBlock.VARIANT, BurntLanternBlock.Variant.of(s.getBlock()))
                            .setValue(LanternBlock.HANGING, s.getValue(LanternBlock.HANGING))
                            .setValue(LanternBlock.WATERLOGGED, s.getValue(LanternBlock.WATERLOGGED))),
            new Burner("campfire", s -> s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT), 2 * DAY,
                    s -> s.setValue(CampfireBlock.LIT, false)),
            new Burner("candle", s -> s.getBlock() instanceof AbstractCandleBlock && s.hasProperty(AbstractCandleBlock.LIT)
                    && s.getValue(AbstractCandleBlock.LIT), 3 * DAY / 2,
                    s -> s.setValue(AbstractCandleBlock.LIT, false)),
            new Burner("jack_o_lantern", s -> s.getBlock() instanceof CarvedPumpkinBlock && s.getLightEmission() > 0, 2 * DAY,
                    s -> Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, s.getValue(CarvedPumpkinBlock.FACING))));

    /** Which kind of light each state is, looked up once; asked for every block a chunk is counted for. */
    private static final Map<BlockState, Optional<Burner>> KIND = new ConcurrentHashMap<>();

    /** When the light at a position was lit; or, for a light gone out, what it was (and no time). */
    record Entry(long since, Optional<BlockState> was) {
        boolean burning() {
            return was.isEmpty();
        }

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("since").forGetter(Entry::since),
                BlockState.CODEC.optionalFieldOf("was").forGetter(Entry::was)).apply(i, Entry::new));
    }

    private static final Codec<Long2ObjectOpenHashMap<Entry>> TABLE_CODEC = Codec.unboundedMap(Codec.STRING, Entry.CODEC).xmap(
            map -> {
                Long2ObjectOpenHashMap<Entry> table = new Long2ObjectOpenHashMap<>();
                map.forEach((key, entry) -> table.put(Long.parseLong(key), entry));
                return table;
            },
            table -> {
                Map<String, Entry> map = new HashMap<>();
                table.long2ObjectEntrySet().forEach(e -> map.put(Long.toString(e.getLongKey()), e.getValue()));
                return map;
            });

    public static final AttachmentType<Long2ObjectOpenHashMap<Entry>> BURNING = AttachmentRegistry.create(
            Hardwrought.id("burning_lights"), builder -> builder.persistent(TABLE_CODEC));

    private LightBurn() { }

    /** Loading the class registers the attachment, before any chunk is read. */
    public static void initialize() { }

    static Burner burner(BlockState state) {
        return KIND.computeIfAbsent(state, s -> BURNERS.stream().filter(b -> b.burning().test(s)).findFirst()).orElse(null);
    }

    /** Whether a state is a light that burns down, and so needs random ticks to be watched. */
    public static boolean burns(BlockState state) {
        return burner(state) != null;
    }

    /** How long a light of this state burns, in ticks; zero if it does not. */
    public static long life(BlockState state) {
        Burner burner = burner(state);
        return burner == null ? 0 : burner.ticks();
    }

    /** A block was set: if a light has just been lit, it starts burning now. */
    public static void placed(Level level, BlockPos pos, BlockState old, BlockState now) {
        if (!(level instanceof ServerLevel server) || burner(now) == null || burner(old) != null) return;
        put(server, pos, new Entry(server.getGameTime(), Optional.empty()));
    }

    /**
     * A random tick on a light: it starts burning if it was never seen to, and goes out if its time
     * has come.
     *
     * @return whether it went out, so nothing else acts on the light that is no longer there
     */
    public static boolean tick(BlockState state, ServerLevel level, BlockPos pos) {
        Burner burner = burner(state);
        if (burner == null) return false;
        long now = level.getGameTime();
        Entry entry = get(level, pos);
        if (entry == null || !entry.burning()) {
            put(level, pos, new Entry(now, Optional.empty()));
            return false;
        }
        if (now - entry.since() < burner.ticks()) return false;
        burnOut(level, pos, state);
        return true;
    }

    /** Puts a light out now: it becomes its burnt-out self, which remembers what it was. */
    public static void burnOut(ServerLevel level, BlockPos pos, BlockState state) {
        Burner burner = burner(state);
        if (burner == null) return;
        BlockState out = burner.out().apply(state);
        if (out.getBlock() instanceof BurntLight) put(level, pos, new Entry(0, Optional.of(state)));
        else remove(level, pos);
        level.setBlock(pos, out, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f, 1.4f);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
    }

    /** Lights a burnt-out light again: as exactly what it was, if that is remembered, else as the fallback. */
    static void relight(ServerLevel level, BlockPos pos, BlockState burnt, BlockState fallback) {
        Entry entry = get(level, pos);
        BlockState lit = entry != null && entry.was().isPresent() ? entry.was().get() : fallback;
        // Hanging, waterlogged, facing: whatever the burnt light has now, the lit one takes.
        for (Property<?> property : burnt.getProperties()) {
            if (lit.hasProperty(property) && property != BlockStateProperties.LIT) lit = copy(burnt, lit, property);
        }
        remove(level, pos);
        level.setBlock(pos, lit, Block.UPDATE_ALL);
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    /** Says the light at a position was lit at this game tick, as if it had burnt that long already. For tests. */
    public static void litSince(ServerLevel level, BlockPos pos, long since) {
        put(level, pos, new Entry(since, Optional.empty()));
    }

    /** When the light at a position was lit, in game ticks; -1 if no lit light is known there. For tests. */
    public static long litAt(ServerLevel level, BlockPos pos) {
        Entry entry = get(level, pos);
        return entry == null || !entry.burning() ? -1 : entry.since();
    }

    // --- The table, kept with each chunk ---------------------------------------------------------

    private static Entry get(ServerLevel level, BlockPos pos) {
        Long2ObjectOpenHashMap<Entry> table = level.getChunkAt(pos).getAttached(BURNING);
        return table == null ? null : table.get(pos.asLong());
    }

    private static void put(ServerLevel level, BlockPos pos, Entry entry) {
        LevelChunk chunk = level.getChunkAt(pos);
        Long2ObjectOpenHashMap<Entry> table = copyOf(chunk);
        table.put(pos.asLong(), entry);
        chunk.setAttached(BURNING, table);
    }

    private static void remove(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        Long2ObjectOpenHashMap<Entry> existing = chunk.getAttached(BURNING);
        if (existing == null || !existing.containsKey(pos.asLong())) return;
        Long2ObjectOpenHashMap<Entry> table = copyOf(chunk);
        table.remove(pos.asLong());
        if (table.isEmpty()) chunk.removeAttached(BURNING);
        else chunk.setAttached(BURNING, table);
    }

    /** A fresh copy, so that setting it marks the chunk to be saved. */
    private static Long2ObjectOpenHashMap<Entry> copyOf(LevelChunk chunk) {
        Long2ObjectOpenHashMap<Entry> existing = chunk.getAttached(BURNING);
        return existing == null ? new Long2ObjectOpenHashMap<>() : new Long2ObjectOpenHashMap<>(existing);
    }
}
