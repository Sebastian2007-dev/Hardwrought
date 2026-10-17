package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.ritual.Ritual;
import de.ipnats.hardwrought.ritual.RitualAltar;
import de.ipnats.hardwrought.ritual.RitualContent;
import de.ipnats.hardwrought.ritual.RitualPedestal;
import de.ipnats.hardwrought.ritual.Temple;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/** The three temples of the end of the game, looked at, and the ritual in one of them, from beginning to end. */
public final class RitualClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            world.getConnection().waitForChunksRender();
            BlockPos[] altars = new BlockPos[3];
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = player(server);
                player.setGameMode(GameType.SPECTATOR);
                for (int i = 0; i < 3; i++) {
                    // At a fixed height: a chunk not yet made has no ground to measure.
                    altars[i] = new BlockPos(400 + i * 90, 110, 400);
                    Temple.buildNow(level, altars[i], Temple.Theme.values()[i]);
                }
            });
            String[] names = {"plains", "desert", "stony"};
            for (int i = 0; i < 3; i++) {
                BlockPos altar = altars[i];
                look(world, Vec3.atCenterOf(altar).add(48, 28, 40), Vec3.atCenterOf(altar).add(0, 6, 0));
                shot(context, world, 80, "hardwrought-temple-" + names[i]);
                look(world, Vec3.atCenterOf(altar).add(2, 0, 9), Vec3.atCenterOf(altar).add(0, 1, -2));
                shot(context, world, 30, "hardwrought-temple-" + names[i] + "-chamber");
            }
            // The ritual, in the desert temple. First it does not stand: a piece of wall gone, a pillar wrong; asked, the
            // altar marks every block still wrong red.
            BlockPos altar = altars[1];
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                for (int y = 0; y <= 11; y++) for (int z = 3; z <= 12; z++) for (int x = 11; x <= 16; x++) {
                    level.setBlock(altar.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
                for (int y = 0; y <= 6; y++) {
                    level.setBlock(altar.offset(-8, y, -7), net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 2);
                }
                for (int i = 0; i < 1200; i++) {
                    BlockPos at = altar.offset(-20 + i % 40, -2, 14 + i / 40 % 6);
                    if (level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.SMOOTH_SANDSTONE)) {
                        level.setBlock(at, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
                ServerPlayer player = player(server);
                player.setGameMode(GameType.SURVIVAL);
                var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(altar), net.minecraft.core.Direction.UP, altar, false);
                level.getBlockState(altar).useWithoutItem(level, player, hit);
            });
            look(world, Vec3.atCenterOf(altar).add(-4, -1, 3), Vec3.atCenterOf(altar).add(6, 3, 6));
            shot(context, world, 20, "hardwrought-temple-faults");
            context.runOnClient(client -> {
                if (de.ipnats.hardwrought.client.ritual.TempleFaultsClient.markedForTesting().size() < 20) {
                    throw new AssertionError("The wrong blocks are marked: " + de.ipnats.hardwrought.client.ritual.TempleFaultsClient.markedForTesting().size());
                }
            });
            look(world, Vec3.atCenterOf(altar).add(Temple.HALL + 9, 2, 12), Vec3.atCenterOf(altar).add(Temple.HALL, 3, 5));
            shot(context, world, 10, "hardwrought-temple-faults-wall");
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                // Put right, one by one: a mark goes as soon as its block is right.
                Temple.buildNow(level, altar, Temple.Theme.DESERT);
                player(server).setGameMode(GameType.SPECTATOR);
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                if (!de.ipnats.hardwrought.client.ritual.TempleFaultsClient.markedForTesting().isEmpty()) {
                    throw new AssertionError("Put right, the marks go: " + de.ipnats.hardwrought.client.ritual.TempleFaultsClient.markedForTesting().size());
                }
            });
            // Half its offerings laid, the others still ghosts over their pedestals.
            List<BlockPos> pedestals = Temple.pedestals(altar);
            offer(world, altar, 0, 4);
            look(world, Vec3.atCenterOf(altar).add(3, -1, 4), Vec3.atCenterOf(altar).add(-1, 0, -1));
            shot(context, world, 40, "hardwrought-ritual-ghosts");
            look(world, Vec3.atCenterOf(pedestals.get(6)).add(1.6, 0, 1.6), Vec3.atCenterOf(pedestals.get(6)).add(0, 1.4, 0));
            shot(context, world, 10, "hardwrought-ritual-ghost-near");
            offer(world, altar, 4, pedestals.size());
            look(world, Vec3.atCenterOf(altar).add(6, -1, 7), Vec3.atCenterOf(altar).add(0, 1, 0));
            shot(context, world, 30, "hardwrought-ritual-offerings");
            // A player of their own in the chamber when it begins: held there, and rising at its end.
            world.getServer().runOnServer(server -> {
                player(server).setGameMode(GameType.SURVIVAL);
                if (!((RitualAltar.Entity) server.overworld().getBlockEntity(altar)).beginForTesting(server.overworld())) {
                    throw new AssertionError("The ritual begins: " + Temple.survey(server.overworld(), altar));
                }
            });
            context.waitTicks(100);
            // Out through the door: the ward sets them back.
            look(world, Vec3.atCenterOf(altar).add(Temple.HALL + 3, -1, 0), Vec3.atCenterOf(altar).add(Temple.HALL + 20, 1, 0));
            shot(context, world, 3, "hardwrought-ritual-ward");
            look(world, Vec3.atCenterOf(altar).add(5, -1, 6), Vec3.atCenterOf(altar).add(0, 1, 0));
            shot(context, world, 137, "hardwrought-ritual-kindled");
            shot(context, world, 300, "hardwrought-ritual-rings-sky");
            shot(context, world, 300, "hardwrought-ritual-converge");
            shot(context, world, 264, "hardwrought-ritual-finale");
            // The rise (Ritual.ASCEND on): up from the chamber, through the roof, outlined in gold, and the light breaking out.
            int[] at = {30, 75, 95, 120, 150, 176, 188, 215, 245};
            String[] names2 = {"rise", "rise-roof", "rise-outside", "outline", "outline-near", "apotheosis", "apotheosis-burst", "after", "back"};
            int now = Ritual.FINALE + 4 - Ritual.ASCEND;
            for (int i = 0; i < at.length; i++) {
                shot(context, world, at[i] - now, "hardwrought-ritual-" + names2[i]);
                now = at[i];
            }
            world.getServer().runOnServer(server -> {
                if (!player(server).isCreative()) throw new AssertionError("Risen, the player is set free: creative mode");
            });
            shot(context, world, 40, "hardwrought-ritual-free");
        }
    }

    private static void offer(TestSingleplayerContext world, BlockPos altar, int from, int to) {
        world.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            List<BlockPos> pedestals = Temple.pedestals(altar);
            for (int i = from; i < to; i++) {
                ((RitualPedestal.Entity) level.getBlockEntity(pedestals.get(i))).offerForTesting(level, new ItemStack(RitualContent.offering(i)));
            }
        });
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void look(TestSingleplayerContext world, Vec3 at, Vec3 target) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = player(server);
            Vec3 eye = at.add(0, player.getEyeHeight(), 0);
            Vec3 d = target.subtract(eye);
            float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
            float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
            player.teleportTo(server.overworld(), at.x, at.y, at.z, Set.of(), yaw, pitch, true);
        });
    }

    private static void shot(ClientGameTestContext context, TestSingleplayerContext world, int ticks, String name) {
        context.waitTicks(ticks);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot(name);
    }
}
