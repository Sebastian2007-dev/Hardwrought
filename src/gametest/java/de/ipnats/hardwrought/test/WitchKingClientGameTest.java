package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.underworld.Underworld;
import de.ipnats.hardwrought.witchking.Citadel;
import de.ipnats.hardwrought.witchking.WitchKing;
import de.ipnats.hardwrought.witchking.WitchKingThrone;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * The Witch King's fight in a real world, looked at: his citadel, his entrance, each of his phases, his
 * death. The player is in survival so that he fights, and cannot be hurt so that the test sees it through.
 */
public final class WitchKingClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            world.getConnection().waitForChunksRender();
            BlockPos home = new BlockPos(0, 120, 0);
            world.getServer().runOnServer(server -> {
                ServerLevel underworld = server.getLevel(Underworld.LEVEL);
                for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) underworld.getChunk(cx, cz);
                Citadel.build(underworld, home, RandomSource.create(1));
                ServerPlayer player = player(server);
                player.setGameMode(GameType.SURVIVAL);
                player.getAbilities().invulnerable = true;
                player.onUpdateAbilities();
                look(player, underworld, home.offset(0, 1, 12), home.offset(0, 2, -Citadel.THRONE_DISTANCE));
            });
            shot(context, world, 60, "hardwrought-witch-king-citadel");
            world.getServer().runOnServer(server -> {
                ServerLevel underworld = server.getLevel(Underworld.LEVEL);
                BlockPos throne = home.offset(0, 1, -Citadel.THRONE_DISTANCE);
                ((WitchKingThrone.Entity) underworld.getBlockEntity(throne)).wake(underworld);
            });
            shot(context, world, 125, "hardwrought-witch-king-entrance");
            follow(world, home);
            shot(context, world, 70, "hardwrought-witch-king-phase-one");
            follow(world, home);
            shot(context, world, 45, "hardwrought-witch-king-phase-one-blades");
            // Down to two thirds: his ward and his crystals.
            world.getServer().runOnServer(server -> {
                WitchKing king = king(server, home);
                king.testHealth(0.67f);
                king.hurtServer(server.getLevel(Underworld.LEVEL), server.getLevel(Underworld.LEVEL).damageSources().magic(), 10);
                if (king.phase() != 2) throw new AssertionError("He is warded at two thirds");
                look(player(server), server.getLevel(Underworld.LEVEL), home.offset(-6, 1, 14), home.offset(0, 6, 0));
            });
            shot(context, world, 50, "hardwrought-witch-king-warded");
            shot(context, world, 90, "hardwrought-witch-king-warded-rain");
            shot(context, world, 140, "hardwrought-witch-king-curse");
            // The crystals broken: he grows.
            world.getServer().runOnServer(server -> {
                ServerLevel underworld = server.getLevel(Underworld.LEVEL);
                for (BlockPos spot : WitchKing.CRYSTAL_SPOTS) underworld.destroyBlock(home.offset(spot), false);
            });
            shot(context, world, 30, "hardwrought-witch-king-growing");
            world.getServer().runOnServer(server -> {
                WitchKing king = king(server, home);
                if (king.phase() != 3) throw new AssertionError("The ward broken, he is unbound");
                look(player(server), server.getLevel(Underworld.LEVEL), home.offset(0, 1, 15), king.position().add(0, king.getBbHeight() * 0.6, 0));
            });
            shot(context, world, 45, "hardwrought-witch-king-giant");
            follow(world, home);
            shot(context, world, 120, "hardwrought-witch-king-giant-fight");
            // And his death.
            world.getServer().runOnServer(server -> {
                WitchKing king = king(server, home);
                look(player(server), server.getLevel(Underworld.LEVEL), home.offset(0, 1, 15), king.position().add(0, king.getBbHeight() * 0.5, 0));
                king.hurtServer(server.getLevel(Underworld.LEVEL), server.getLevel(Underworld.LEVEL).damageSources().genericKill(), 10000);
            });
            shot(context, world, 50, "hardwrought-witch-king-dying");
            context.waitTicks(60);
            world.getServer().runOnServer(server -> {
                ServerLevel underworld = server.getLevel(Underworld.LEVEL);
                if (!underworld.getEntitiesOfClass(WitchKing.class, new AABB(home).inflate(30)).isEmpty()) throw new AssertionError("He is gone");
                var seat = (WitchKingThrone.Entity) underworld.getBlockEntity(home.offset(0, 1, -Citadel.THRONE_DISTANCE));
                if (seat.state() != WitchKingThrone.Entity.DEFEATED) throw new AssertionError("and his throne cold");
                var drops = underworld.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(home).inflate(30),
                        item -> item.getItem().is(de.ipnats.hardwrought.witchking.WitchKingContent.MANTLE));
                if (drops.isEmpty()) throw new AssertionError("leaving his mantle");
            });
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static WitchKing king(MinecraftServer server, BlockPos home) {
        var kings = server.getLevel(Underworld.LEVEL).getEntitiesOfClass(WitchKing.class, new AABB(home).inflate(40));
        if (kings.isEmpty()) throw new AssertionError("The Witch King is in his hall");
        return kings.getFirst();
    }

    /** Puts the player at a place, looking at a point. */
    private static void look(ServerPlayer player, ServerLevel level, BlockPos at, Vec3 target) {
        Vec3 eye = Vec3.atBottomCenterOf(at).add(0, player.getEyeHeight(), 0);
        Vec3 d = target.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
        player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), yaw, pitch, true);
    }

    private static void look(ServerPlayer player, ServerLevel level, BlockPos at, BlockPos target) {
        look(player, level, at, Vec3.atCenterOf(target));
    }

    /** Turns the player to look at the king, from where they stand. */
    private static void follow(TestSingleplayerContext world, BlockPos home) {
        world.getServer().runOnServer(server -> {
            WitchKing king = king(server, home);
            ServerPlayer player = player(server);
            look(player, server.getLevel(Underworld.LEVEL), player.blockPosition(), king.position().add(0, king.getBbHeight() * 0.6, 0));
        });
    }

    private static void shot(ClientGameTestContext context, TestSingleplayerContext world, int ticks, String name) {
        context.waitTicks(ticks);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot(name);
    }
}
