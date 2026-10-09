package de.ipnats.hardwrought.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.networking.AtlasPayloads;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;

/**
 * The atlas: a book that draws the land its bearer walks through, and keeps it.
 *
 * <p>While the atlas is anywhere in a player's inventory, the surface round them is recorded, chunk
 * by chunk, as they come near it: what lies on top and how the ground rises and falls, in the colours
 * of vanilla's own maps, sixteen squares to a chunk. What was seen once stays in the atlas for good,
 * whether the land changes afterwards or not; nothing is drawn of where the bearer has not been.
 * Each player has their own atlas for each dimension, kept with the world and not in the item: an
 * atlas that is lost is a book to be made again, not a map to be walked again.
 *
 * <p>The bearer can also mark places in it, with a name, and where they die it marks by itself.
 */
public final class Atlas {
    /** Squares along a chunk's side in the atlas, and blocks along a square's. */
    public static final int SQUARES = 4, BLOCKS = 16 / SQUARES;
    /** Bytes that record one chunk: a map colour for each of its squares. */
    public static final int TILE = SQUARES * SQUARES;
    /** Chunks round the bearer that are drawn in as they walk. */
    static final int REACH = 3;
    static final int RECORD_TICKS = 20;
    /** The most markers one atlas holds for a dimension, and the longest a marker's name can be. */
    public static final int MAX_MARKERS = 64, MAX_NAME = 24;
    /** Chunks sent to a client in one message. */
    static final int BATCH = 256;

    /** The book itself. Opening it is the client's affair. */
    public static final class Book extends Item {
        /** Client hook, assigned without loading client classes on a server. */
        public static Runnable opener = () -> { };

        public Book(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide()) opener.run();
            return InteractionResult.SUCCESS;
        }
    }

    public static final Item ITEM = register("atlas", new Item.Properties().stacksTo(1));

    /**
     * A marked place.
     *
     * @param kind 0 for a place the bearer marked, 1 for where they died
     */
    public record Marker(int x, int z, String name, int kind) {
        public static final Codec<Marker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("x").forGetter(Marker::x),
                Codec.INT.fieldOf("z").forGetter(Marker::z),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Marker::name),
                Codec.INT.optionalFieldOf("kind", 0).forGetter(Marker::kind)
        ).apply(instance, Marker::new));

        public Marker {
            name = name == null ? "" : name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        }
    }

    /** One player's atlas of one dimension: the chunks drawn in it, and the places marked. */
    public static final class Page {
        static final Codec<Page> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG_STREAM.fieldOf("chunks").forGetter(page -> page.tiles.keySet().stream().mapToLong(Long::longValue)),
                Codec.BYTE_BUFFER.fieldOf("tiles").forGetter(Page::packed),
                Marker.CODEC.listOf().optionalFieldOf("markers", List.of()).forGetter(page -> page.markers)
        ).apply(instance, Page::new));

        /** Chunk (see {@link #key}) to its sixteen squares, in the order they were drawn. */
        final LinkedHashMap<Long, byte[]> tiles = new LinkedHashMap<>();
        final List<Marker> markers = new ArrayList<>();

        Page() { }

        private Page(LongStream chunks, ByteBuffer packed, List<Marker> markers) {
            long[] keys = chunks.toArray();
            ByteBuffer bytes = packed.duplicate();
            for (long key : keys) {
                if (bytes.remaining() < TILE) break;
                byte[] tile = new byte[TILE];
                bytes.get(tile);
                tiles.put(key, tile);
            }
            this.markers.addAll(markers);
        }

        private ByteBuffer packed() {
            ByteBuffer bytes = ByteBuffer.allocate(tiles.size() * TILE);
            tiles.values().forEach(bytes::put);
            return bytes.flip();
        }

        public int chunks() {
            return tiles.size();
        }

        public byte[] tile(int chunkX, int chunkZ) {
            return tiles.get(key(chunkX, chunkZ));
        }

        public List<Marker> markers() {
            return List.copyOf(markers);
        }
    }

    /** Every player's atlas of one dimension, saved with it. */
    public static final class Data extends SavedData {
        private static final Codec<Data> CODEC = Codec.unboundedMap(Codec.STRING, Page.CODEC).xmap(Data::new, data -> {
            Map<String, Page> pages = new HashMap<>();
            data.pages.forEach((player, page) -> pages.put(player.toString(), page));
            return pages;
        });
        public static final SavedDataType<Data> TYPE = new SavedDataType<>(Hardwrought.id("atlas"), Data::new, CODEC, null);

        private final Map<UUID, Page> pages = new HashMap<>();

        Data() { }

        private Data(Map<String, Page> saved) {
            saved.forEach((player, page) -> {
                try {
                    pages.put(UUID.fromString(player), page);
                } catch (IllegalArgumentException notAPlayer) {
                    // A hand-edited file with a bad name in it loses that page and keeps the rest.
                }
            });
        }

        Page page(UUID player) {
            return pages.computeIfAbsent(player, id -> new Page());
        }
    }

    private Atlas() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(Atlas::tick);
        ServerPlayNetworking.registerGlobalReceiver(AtlasPayloads.Request.TYPE, (payload, context) -> sendAll(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(AtlasPayloads.Mark.TYPE,
                (payload, context) -> mark(context.player(), payload.x(), payload.z(), payload.name(), 0));
        ServerPlayNetworking.registerGlobalReceiver(AtlasPayloads.Unmark.TYPE,
                (payload, context) -> unmark(context.player(), payload.index()));
        // Where the bearer of an atlas dies is marked in it: the one thing they will want to find again.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player && carries(player)) {
                mark(player, player.getBlockX(), player.getBlockZ(), "", 1);
            }
        });
    }

    /** A chunk's place as one number. */
    public static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    public static boolean carries(Player player) {
        return player.getInventory().contains(stack -> stack.is(ITEM));
    }

    public static Page page(ServerPlayer player) {
        return ((ServerLevel) player.level()).getDataStorage().computeIfAbsent(Data.TYPE).page(player.getUUID());
    }

    private static Identifier dimension(ServerPlayer player) {
        return player.level().dimension().identifier();
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % RECORD_TICKS != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator() && carries(player)) record(player);
        }
    }

    /** Draws in every chunk near the bearer that is not in their atlas yet, and tells their client. */
    public static int record(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Data data = level.getDataStorage().computeIfAbsent(Data.TYPE);
        Page page = data.page(player.getUUID());
        int chunkX = player.getBlockX() >> 4, chunkZ = player.getBlockZ() >> 4;
        List<Long> drawn = new ArrayList<>();
        for (int dz = -REACH; dz <= REACH; dz++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                long key = key(chunkX + dx, chunkZ + dz);
                // Only what is loaded anyway is drawn: the atlas never loads a chunk to look at it.
                if (page.tiles.containsKey(key) || level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz) == null) continue;
                page.tiles.put(key, survey(level, chunkX + dx, chunkZ + dz));
                drawn.add(key);
            }
        }
        if (!drawn.isEmpty()) {
            data.setDirty();
            send(player, page, drawn, false);
        }
        return drawn.size();
    }

    /**
     * One chunk as the atlas draws it: for each of its squares the map colour of what lies on top in
     * the middle of the square, lighter where the ground rises towards the north and darker where it
     * falls, as vanilla's maps shade it.
     */
    public static byte[] survey(ServerLevel level, int chunkX, int chunkZ) {
        byte[] tile = new byte[TILE];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int sz = 0; sz < SQUARES; sz++) {
            for (int sx = 0; sx < SQUARES; sx++) {
                int x = (chunkX << 4) + sx * BLOCKS + BLOCKS / 2, z = (chunkZ << 4) + sz * BLOCKS + BLOCKS / 2;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                boolean northLoaded = (z - BLOCKS) >> 4 == chunkZ || level.getChunkSource().getChunkNow(chunkX, chunkZ - 1) != null;
                int north = northLoaded ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - BLOCKS) : top;
                MapColor colour = MapColor.NONE;
                for (int down = 1; down <= 8 && colour == MapColor.NONE && top - down >= level.getMinY(); down++) {
                    pos.set(x, top - down, z);
                    BlockState state = level.getBlockState(pos);
                    colour = state.getMapColor(level, pos);
                }
                MapColor.Brightness light = top > north ? MapColor.Brightness.HIGH
                        : top < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                tile[sz * SQUARES + sx] = colour.getPackedId(light);
            }
        }
        return tile;
    }

    private static void send(ServerPlayer player, Page page, List<Long> chunks, boolean fresh) {
        if (!ServerPlayNetworking.canSend(player, AtlasPayloads.Tiles.TYPE)) return;
        Identifier dimension = dimension(player);
        if (chunks.isEmpty() && fresh) {
            ServerPlayNetworking.send(player, new AtlasPayloads.Tiles(dimension, new long[0], new byte[0], true));
        }
        for (int from = 0; from < chunks.size(); from += BATCH) {
            int count = Math.min(BATCH, chunks.size() - from);
            long[] keys = new long[count];
            byte[] tiles = new byte[count * TILE];
            for (int i = 0; i < count; i++) {
                keys[i] = chunks.get(from + i);
                System.arraycopy(page.tiles.get(keys[i]), 0, tiles, i * TILE, TILE);
            }
            ServerPlayNetworking.send(player, new AtlasPayloads.Tiles(dimension, keys, tiles, fresh && from == 0));
        }
    }

    /** Sends a player the whole of their atlas of the dimension they are in: on opening it. */
    private static void sendAll(ServerPlayer player) {
        Page page = page(player);
        send(player, page, new ArrayList<>(page.tiles.keySet()), true);
        sendMarkers(player, page);
    }

    private static void sendMarkers(ServerPlayer player, Page page) {
        if (ServerPlayNetworking.canSend(player, AtlasPayloads.Markers.TYPE)) {
            ServerPlayNetworking.send(player, new AtlasPayloads.Markers(dimension(player), page.markers()));
        }
    }

    /** Marks a place in a player's atlas. The oldest death gives way to a newer one when the atlas is full of marks. */
    public static boolean mark(ServerPlayer player, int x, int z, String name, int kind) {
        ServerLevel level = (ServerLevel) player.level();
        Data data = level.getDataStorage().computeIfAbsent(Data.TYPE);
        Page page = data.page(player.getUUID());
        if (page.markers.size() >= MAX_MARKERS) {
            int oldestDeath = -1;
            for (int i = 0; i < page.markers.size() && oldestDeath < 0; i++) if (page.markers.get(i).kind() == 1) oldestDeath = i;
            if (kind != 1 || oldestDeath < 0) return false;
            page.markers.remove(oldestDeath);
        }
        page.markers.add(new Marker(x, z, name.strip(), kind));
        data.setDirty();
        sendMarkers(player, page);
        return true;
    }

    public static boolean unmark(ServerPlayer player, int index) {
        ServerLevel level = (ServerLevel) player.level();
        Data data = level.getDataStorage().computeIfAbsent(Data.TYPE);
        Page page = data.page(player.getUUID());
        if (index < 0 || index >= page.markers.size()) return false;
        page.markers.remove(index);
        data.setDirty();
        sendMarkers(player, page);
        return true;
    }

    private static Item register(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Book(properties.setId(key)));
    }
}
