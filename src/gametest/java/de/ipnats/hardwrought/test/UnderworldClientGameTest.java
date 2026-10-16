package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.underworld.Underworld;
import de.ipnats.hardwrought.underworld.UnderworldGate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Underworld in a real world: it is there, a cave world under a rock roof with all nine biomes, and
 * what it looks like inside a few of them.
 */
public final class UnderworldClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            world.getConnection().waitForChunksRender();
            List<String> report = new ArrayList<>();
            List<BlockPos> views = world.getServer().computeOnServer(server -> {
                var underworld = server.getLevel(Underworld.LEVEL);
                if (underworld == null) throw new AssertionError("The Underworld exists in a real world");
                underworld.getChunk(0, 0);
                if (!underworld.getBlockState(new BlockPos(0, underworld.getMinY(), 0)).is(Blocks.BEDROCK)
                        || !underworld.getBlockState(new BlockPos(0, underworld.getMaxY(), 0)).is(Blocks.BEDROCK)) {
                    throw new AssertionError("Bedrock under it and over it");
                }
                int open = 0, total = 0;
                // Spread over a few chunks: one chunk can happen to be nearly all rock.
                for (int x = -48; x < 48; x += 6) {
                    for (int z = -48; z < 48; z += 6) {
                        underworld.getChunk(x >> 4, z >> 4);
                        for (int y = underworld.getMinY() + 8; y < underworld.getMaxY() - 8; y += 4) {
                            total++;
                            if (underworld.getBlockState(new BlockPos(x, y, z)).isAir()) open++;
                        }
                    }
                }
                double share = open / (double) total;
                if (share < 0.05 || share > 0.8) throw new AssertionError("Caves through rock: " + share + " open");
                report.add("open " + Math.round(share * 100) + "%");
                List<BlockPos> found = new ArrayList<>();
                for (String name : List.of("stone_wastes", "overgrown_halls", "dripstone_depths", "mossy_grove", "graveyard",
                        "ossuary", "flooded_halls", "echo_chasm")) {
                    ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Hardwrought.id(name));
                    var hit = underworld.findClosestBiome3d(biome -> biome.is(key), BlockPos.ZERO, 6400, 64, 32);
                    if (hit == null) throw new AssertionError("The " + name + " is found in the Underworld");
                    report.add(name + " at " + hit.getFirst().toShortString());
                    found.add(hit.getFirst());
                }
                // The Flooded Halls' lakes stay inside them: no water out in the halls beside.
                BlockPos lake = found.get(6);
                int spilled = 0;
                java.util.Map<String, Integer> where = new java.util.TreeMap<>();
                java.util.Map<String, String> samples = new java.util.TreeMap<>();
                for (int x = lake.getX() - 96; x < lake.getX() + 96; x++) {
                    for (int z = lake.getZ() - 96; z < lake.getZ() + 96; z++) {
                        underworld.getChunk(x >> 4, z >> 4);
                        for (int y = underworld.getMinY() + 1; y <= 8; y++) {
                            BlockPos at = new BlockPos(x, y, z);
                            if (underworld.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)
                                    && !underworld.getBiome(at.atY(8)).is(ResourceKey.create(Registries.BIOME, Hardwrought.id("flooded_halls")))) {
                                spilled++;
                                String kind = underworld.getBiome(at.atY(8)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?")
                                        + (underworld.getFluidState(at).isSource() ? " source" : " flowing");
                                where.merge(kind, 1, Integer::sum);
                                if (where.size() < 4 && !samples.containsKey(kind)) samples.put(kind, at.toShortString());
                            }
                        }
                    }
                }
                report.add("water outside the flooded halls " + spilled + " " + where + " " + samples);
                if (spilled > 0) throw new AssertionError("The Flooded Halls are sealed: " + report);
                if (underworld.findClosestBiome3d(biome -> biome.is(net.minecraft.world.level.biome.Biomes.DEEP_DARK),
                        BlockPos.ZERO, 6400, 64, 32) == null) {
                    throw new AssertionError("The deep dark is found in the Underworld");
                }
                return found;
            });
            System.out.println("[underworld] " + report);
            // The ruins of those who came before: counted over the chunks round the middle, and one looked at.
            BlockPos ruin = world.getServer().computeOnServer(server -> {
                var underworld = server.getLevel(Underworld.LEVEL);
                java.util.Map<String, Integer> found = new java.util.TreeMap<>();
                BlockPos first = null;
                for (int cx = -10; cx <= 10; cx++) {
                    for (int cz = -10; cz <= 10; cz++) {
                        var chunk = underworld.getChunk(cx, cz);
                        for (var entity : chunk.getBlockEntities().values()) {
                            if (!(entity instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container)
                                    || container.getLootTable() == null) continue;
                            String table = container.getLootTable().identifier().getPath();
                            if (!table.startsWith("chests/underworld_")) continue;
                            found.merge(table, 1, Integer::sum);
                            if (first == null && table.endsWith("ruin")) first = entity.getBlockPos();
                        }
                    }
                }
                System.out.println("[underworld] ruins in 21x21 chunks: " + found);
                if (!found.containsKey("chests/underworld_ruin") || !found.containsKey("chests/underworld_camp")) {
                    throw new AssertionError("Ruins and camps stand in the Underworld: " + found);
                }
                return first;
            });
            world.getServer().runOnServer(server -> {
                var underworld = server.getLevel(Underworld.LEVEL);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                // A light, so the ruin can be seen at all.
                underworld.setBlockAndUpdate(ruin.above(3), Blocks.LIGHT.defaultBlockState());
                player.teleportTo(underworld, ruin.getX() + 6.5, ruin.getY() + 3, ruin.getZ() + 6.5, Set.of(), 135.0f, 25.0f, true);
            });
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-underworld-ruin");
            // The Graveyard: its graves, and one of its dead coming up out of the ground.
            BlockPos graveyard = views.get(4);
            BlockPos grave = world.getServer().computeOnServer(server -> {
                var underworld = server.getLevel(Underworld.LEVEL);
                int headstones = 0;
                BlockPos found = null;
                for (int cx = -3; cx <= 3; cx++) {
                    for (int cz = -3; cz <= 3; cz++) {
                        var chunk = underworld.getChunk((graveyard.getX() >> 4) + cx, (graveyard.getZ() >> 4) + cz);
                        for (BlockPos pos : BlockPos.betweenClosed(chunk.getPos().getMinBlockX(), underworld.getMinY() + 8, chunk.getPos().getMinBlockZ(),
                                chunk.getPos().getMaxBlockX(), underworld.getMaxY() - 8, chunk.getPos().getMaxBlockZ())) {
                            var state = chunk.getBlockState(pos);
                            if ((state.is(Blocks.MOSSY_COBBLESTONE_WALL) || state.is(Blocks.STONE_BRICK_WALL) || state.is(Blocks.MOSSY_STONE_BRICK_WALL))
                                    && chunk.getBlockState(pos.below()).isSolidRender()) {
                                headstones++;
                                if (found == null) found = pos.immutable();
                            }
                        }
                    }
                }
                System.out.println("[underworld] headstones in 7x7 chunks of the Graveyard: " + headstones);
                if (headstones < 5 || found == null) throw new AssertionError("The Graveyard has its graves: " + headstones);
                var player = server.getPlayerList().getPlayers().getFirst();
                underworld.setBlockAndUpdate(found.above(4), Blocks.LIGHT.defaultBlockState());
                var walker = de.ipnats.hardwrought.underworld.GraveUndead.GRAVE_WALKER.create(underworld, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                BlockPos at = found.east(2);
                for (int up = 0; up < 3 && !underworld.getBlockState(at).isAir(); up++) at = at.above();
                walker.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
                underworld.addFreshEntity(walker);
                walker.rise();
                player.teleportTo(underworld, found.getX() + 4.5, found.getY() + 2.5, found.getZ() + 4.5, Set.of(), 135.0f, 25.0f, true);
                return found;
            });
            context.waitTicks(de.ipnats.hardwrought.underworld.GraveUndead.SHIFTING + 22);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-underworld-graveyard-rising");
            context.waitTicks(40);
            context.takeScreenshot("hardwrought-underworld-graveyard-risen");
            // A look inside three of them: a cave of the Stone Wastes, the Mossy Grove, the Echo Chasm.
            String[] names = {"stone-wastes", "mossy-grove", "echo-chasm"};
            int[] which = {0, 3, 7};
            for (int i = 0; i < names.length; i++) {
                BlockPos at = views.get(which[i]);
                world.getServer().runOnServer(server -> {
                    var underworld = server.getLevel(Underworld.LEVEL);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    underworld.getChunk(at.getX() >> 4, at.getZ() >> 4);
                    BlockPos stand = open(underworld, at);
                    player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    player.teleportTo(underworld, stand.getX() + 0.5, stand.getY() + 0.2, stand.getZ() + 0.5, Set.of(), 30.0f, 10.0f, true);
                });
                context.waitTicks(40);
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("hardwrought-underworld-" + names[i]);
            }

            // The way down: a whole seal in the abyss, opened; into the shaft, and the Underworld, black without a light.
            BlockPos stone = new BlockPos(8, UnderworldGate.ABYSS_BELOW - 20, 8);
            world.getServer().runOnServer(server -> {
                var overworld = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                overworld.getChunk(0, 0);
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        overworld.setBlockAndUpdate(stone.offset(dx, -1, dz), Blocks.DEEPSLATE.defaultBlockState());
                        for (int dy = 0; dy <= 3; dy++) overworld.setBlockAndUpdate(stone.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                    }
                }
                for (int dx : new int[] {-2, 2}) {
                    for (int dz : new int[] {-2, 2}) {
                        overworld.setBlockAndUpdate(stone.offset(dx, 0, dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                        overworld.setBlockAndUpdate(stone.offset(dx, 1, dz), Blocks.SOUL_LANTERN.defaultBlockState());
                    }
                }
                overworld.setBlockAndUpdate(stone, UnderworldGate.SEAL_STONE.defaultBlockState());
                if (UnderworldGate.wanting(overworld, stone) != null) throw new AssertionError("The whole seal takes");
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.teleportTo(overworld, stone.getX() + 3.5, stone.getY(), stone.getZ() + 0.5, Set.of(), 90.0f, 50.0f, true);
                UnderworldGate.open(overworld, stone);
            });
            context.waitTicks(60);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-underworld-seal-opening");
            context.waitTicks(80);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-underworld-shaft");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                if (!server.overworld().getBlockState(stone.below(UnderworldGate.PIT_DEPTH)).is(UnderworldGate.SHAFT)) throw new AssertionError("The seal opened into the shaft");
                player.teleportTo(server.overworld(), stone.getX() + 0.5, stone.getY(), stone.getZ() + 0.5, Set.of(), 0.0f, 0.0f, true);
            });
            context.waitTicks(20);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                if (player.level().dimension() != Underworld.LEVEL) throw new AssertionError("Into the shaft is down into the Underworld");
            });
            context.waitTicks(200);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-underworld-no-light");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                BlockPos eyes = BlockPos.containing(player.getEyePosition());
                List<String> lights = new ArrayList<>();
                for (BlockPos pos : BlockPos.betweenClosed(eyes.offset(-10, -10, -10), eyes.offset(10, 10, 10))) {
                    var state = level.getBlockState(pos);
                    if (state.getLightEmission() > 0 && lights.size() < 12) lights.add(state.getBlock() + "@" + pos.toShortString());
                }
                System.out.println("[underworld] block light at the eyes " + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, eyes)
                        + ", sky " + level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eyes) + ", biome "
                        + level.getBiome(eyes).unwrapKey().orElseThrow().identifier() + ", lights " + lights
                        + ", effects " + player.getActiveEffects());
            });
            context.runOnClient(client -> System.out.println("[underworld] gamma " + client.options.gamma().get()
                    + ", adaptation " + de.ipnats.hardwrought.client.environment.Darkness.adaptation()));
            // What it sounds like and the unease: the reverb is on down here, and a pair of eyes opens in the dark.
            context.runOnClient(client -> {
                if (!de.ipnats.hardwrought.client.underworld.UnderworldReverb.active()) throw new AssertionError("The reverb is on in the Underworld");
                System.out.println("[underworld] reverb supported: " + de.ipnats.hardwrought.client.underworld.UnderworldReverb.supported()
                        + ", farthest rock " + de.ipnats.hardwrought.client.underworld.UnderworldReverb.farthest());
            });
            boolean eyes = context.computeOnClient(client -> {
                for (int i = 0; i < 40; i++) {
                    if (de.ipnats.hardwrought.client.underworld.WatchingEyes.open(client.level, client.player)) return true;
                }
                return false;
            });
            System.out.println("[underworld] eyes opened: " + eyes);
            context.waitTicks(3);
            context.takeScreenshot("hardwrought-underworld-eyes");
            // And up again: a rekindled ascent shaft under them.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var underworld = server.getLevel(Underworld.LEVEL);
                underworld.setBlockAndUpdate(player.blockPosition().below(),
                        UnderworldGate.ASCENT_SHAFT.defaultBlockState().setValue(UnderworldGate.LIT, true));
            });
            context.waitTicks(80);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                if (player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                    throw new AssertionError("A lit ascent shaft carries them back up");
                }
                if (player.blockPosition().distManhattan(stone) > 20) throw new AssertionError("beside the shaft they came down: " + player.blockPosition());
            });
            // Down again: back to where they went up, not under the seal.
            BlockPos wentUp = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos from = player.getAttached(UnderworldGate.RETURN);
                if (from == null) throw new AssertionError("Where they went up from is kept");
                player.teleportTo(server.overworld(), stone.getX() + 0.5, stone.getY() - UnderworldGate.PIT_DEPTH + 0.2, stone.getZ() + 0.5,
                        Set.of(), 0.0f, 0.0f, true);
                return from;
            });
            context.waitTicks(20);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                if (player.level().dimension() != Underworld.LEVEL) throw new AssertionError("Down the shaft again");
                if (player.blockPosition().distManhattan(wentUp) > 12) {
                    throw new AssertionError("back where they went up, " + wentUp + ", not " + player.blockPosition());
                }
            });
            context.waitTicks(60);
            world.getServer().runOnServer(server -> {
                if (server.getPlayerList().getPlayers().getFirst().level().dimension() != Underworld.LEVEL) {
                    throw new AssertionError("and not carried straight back up by the shaft's light");
                }
            });
        }
    }

    /** An open block near a position, to stand a camera in. */
    private static BlockPos open(net.minecraft.server.level.ServerLevel level, BlockPos around) {
        for (int dy = 0; dy < 120; dy++) {
            for (int sign : new int[] {1, -1}) {
                BlockPos at = around.atY(Math.clamp(around.getY() + dy * sign, level.getMinY() + 6, level.getMaxY() - 6));
                if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) return at;
            }
        }
        return around;
    }
}
