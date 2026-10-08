package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Light that people made — torches, lanterns, campfires, lamps — shines in the shaders as well: in
 * its own colour, flickering where it is a flame, and smooth from pixel to pixel. Vanilla's block
 * light stays underneath, for spawning and for what the shaders cannot see: it also tells the
 * shader where a light is hidden behind a wall (see {@code hardwrought:dynamic_light.glsl}).
 *
 * <p>Which blocks count is the block tag {@code hardwrought:shader_lights} — the enchanting table is
 * among them, glowing violet; natural light — lava,
 * fire, glow lichen — is left to vanilla. How bright a block shines is its own light emission, so
 * an unlit furnace or candle stays dark.
 */
public final class PlacedLights {
    public static final TagKey<Block> SHADER_LIGHTS = TagKey.create(Registries.BLOCK, Hardwrought.id("shader_lights"));

    /** Lights farther from the player than this are not looked for. */
    public static final int RANGE = 28;
    /** The shader takes only so many; the nearest win. */
    private static final int MAX_LIGHTS = 40;
    /** Placing a torch shows in the shaders within this many ticks; vanilla's light is there at once. */
    private static final int SCAN_INTERVAL = 8;

    private static final int EMBER = 0xFF8A3C;
    private static final int REDSTONE = 0xFF3A28;
    private static final int SEA = 0xA8E8FF;
    private static final int WHITE = 0xF4ECFF;
    /** Copper burns green: copper torches and copper lanterns. */
    private static final int COPPER = 0x8CFF9C;
    /** Magic is violet: the enchanting table. */
    private static final int ARCANE = 0xB060FF;

    /** One placed light: where it sits, how bright it is and its colour. */
    public record Placed(Vec3 position, int level, int color, boolean flame, int seed) { }

    private static final List<Placed> placed = new ArrayList<>();
    private static int cooldown;

    private PlacedLights() { }

    /** Every placed light near the player, nearest first. */
    public static List<Placed> placed() {
        return placed;
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(PlacedLights::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            placed.clear();
            cooldown = 0;
        });
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null || !Hardwrought.config().dynamicLight()) {
            placed.clear();
            return;
        }
        if (--cooldown > 0) return;
        cooldown = SCAN_INTERVAL;
        scan(level, client.player.blockPosition());
    }

    private static void scan(ClientLevel level, BlockPos center) {
        List<Placed> found = new ArrayList<>();
        int minSection = Math.max(level.getMinSectionY(), SectionPos.blockToSectionCoord(center.getY() - RANGE));
        int maxSection = Math.min(level.getMaxSectionY(), SectionPos.blockToSectionCoord(center.getY() + RANGE));
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - RANGE);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + RANGE);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - RANGE);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + RANGE);
        long rangeSqr = (long) RANGE * RANGE;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int sectionY = minSection; sectionY <= maxSection; sectionY++) {
                    LevelChunkSection section = sections[level.getSectionIndexFromSectionY(sectionY)];
                    if (section.hasOnlyAir() || !section.maybeHas(PlacedLights::shines)) continue;
                    int baseX = SectionPos.sectionToBlockCoord(chunkX);
                    int baseY = SectionPos.sectionToBlockCoord(sectionY);
                    int baseZ = SectionPos.sectionToBlockCoord(chunkZ);
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!shines(state)) continue;
                                long dx = baseX + x - center.getX(), dy = baseY + y - center.getY(), dz = baseZ + z - center.getZ();
                                if (dx * dx + dy * dy + dz * dz > rangeSqr) continue;
                                found.add(light(state, baseX + x, baseY + y, baseZ + z));
                            }
                        }
                    }
                }
            }
        }
        Vec3 from = Vec3.atCenterOf(center);
        found.sort(Comparator.comparingDouble(light -> light.position().distanceToSqr(from)));
        placed.clear();
        placed.addAll(found.subList(0, Math.min(MAX_LIGHTS, found.size())));
    }

    private static boolean shines(BlockState state) {
        return state.is(SHADER_LIGHTS) && state.getLightEmission() > 0;
    }

    private static Placed light(BlockState state, int x, int y, int z) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        int color = colorOf(id);
        boolean flame = flickers(id);
        return new Placed(new Vec3(x + 0.5, y + 0.6, z + 0.5), state.getLightEmission(), color, flame,
                (int) BlockPos.asLong(x, y, z));
    }

    /** The colour of a light by its name: soul fire burns blue, copper green, embers deep orange, lamps a steadier yellow. */
    public static int colorOf(String id) {
        if (id.contains("soul")) return DynamicLight.SOUL_FLAME;
        if (id.contains("copper") && (id.contains("torch") || id.contains("lantern"))) return COPPER;
        if (id.contains("redstone_torch") || id.contains("redstone_wall_torch")) return REDSTONE;
        if (id.contains("sea_lantern") || id.contains("beacon")) return SEA;
        if (id.contains("end_rod")) return WHITE;
        if (id.contains("enchanting_table")) return ARCANE;
        if (id.contains("furnace") || id.contains("smoker") || id.contains("forge") || id.contains("smeltery")) return EMBER;
        if (id.contains("lantern") || id.contains("lamp") || id.contains("glowstone") || id.contains("froglight")) return DynamicLight.LAMP;
        return DynamicLight.FLAME;
    }

    /** Whether a light is a living flame, which breathes, rather than a lamp, which holds still. */
    public static boolean flickers(String id) {
        return !(id.contains("redstone") || id.contains("sea_lantern") || id.contains("glowstone")
                || id.contains("froglight") || id.contains("end_rod") || id.contains("beacon")
                || id.contains("enchanting_table"));
    }
}
