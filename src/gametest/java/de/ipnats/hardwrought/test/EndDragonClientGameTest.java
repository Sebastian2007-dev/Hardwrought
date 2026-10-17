package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.end.dragon.DragonHunt;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.function.Consumer;

/**
 * The dragon's fight, looked at: in a new world's End, with the player hanging in the air beside the
 * arena where the dragon will hunt them, each of its moves made to happen in turn, its stages forced by
 * its health, and its death. The player cannot be hurt and flies, so that the test sees it through.
 */
public final class EndDragonClientGameTest implements FabricClientGameTest {
    private static final Vec3 STAND = new Vec3(30.5, 85, 40.5);
    /** Where the dragon was last seen, to go looking for it. */
    private static Vec3 lastSeen;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                ServerLevel end = end(server);
                ServerPlayer player = player(server);
                player.setGameMode(GameType.SURVIVAL);
                player.getAbilities().invulnerable = true;
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                // A floor under them, set by the test, which may build where players may not.
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(STAND).offset(-1, -1, -1), BlockPos.containing(STAND).offset(1, -1, 1))) {
                    end.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
                }
                player.teleportTo(end, STAND.x, STAND.y, STAND.z, Set.of(), 0, 0, true);
            });
            context.waitTicks(100);
            world.getConnection().waitForChunksRender();
            shotAtDragon(context, world, 40, "hardwrought-dragon-patrol");
            crystal(context, world);
            move(context, world, "bolts", 45, "hardwrought-dragon-bolts");
            context.waitTicks(40);
            // Stepped aside: the clouds its bolts left where the player was.
            onServer(world, server -> look(player(server), end(server), STAND.add(14, 3, 6), STAND.add(0, 1, 0)));
            shot(context, world, 4, "hardwrought-dragon-clouds");
            onServer(world, server -> look(player(server), end(server), STAND, STAND.add(0, 1, -10)));
            context.waitTicks(150);
            move(context, world, "gust", 1, null);
            shotAtDragon(context, world, 60, "hardwrought-dragon-gust");
            onServer(world, server -> look(player(server), end(server), STAND, STAND.add(0, 1, -10)));
            move(context, world, "dive", 1, null);
            context.waitTicks(80);
            onServer(world, server -> look(player(server), end(server), STAND.add(10, 2, 10), STAND.add(0, 1, 0)));
            shot(context, world, 2, "hardwrought-dragon-dive-lock");
            shotAtDragon(context, world, 25, "hardwrought-dragon-dive");
            // Down to the landing: crystals gone, health under the mark.
            onServer(world, server -> {
                for (EndCrystal crystal : end(server).getEntitiesOfClass(EndCrystal.class, new AABB(BlockPos.ZERO).inflate(150))) crystal.discard();
                EnderDragon dragon = dragon(server);
                dragon.setHealth(dragon.getMaxHealth() * 0.45f);
                ((DragonHunt) dragon.getPhaseManager().getCurrentPhase()).landForTesting(end(server));
            });
            Vec3 above = new Vec3(4.5, 82, 26.5);
            onServer(world, server -> look(player(server), end(server), above, new Vec3(0, 64, 0)));
            shot(context, world, 30, "hardwrought-dragon-landed");
            shot(context, world, 22, "hardwrought-dragon-stomp-mark");
            shot(context, world, 26, "hardwrought-dragon-stomp");
            // Raging: the sky torn, the island breaking up.
            onServer(world, server -> {
                EnderDragon dragon = dragon(server);
                dragon.setHealth(dragon.getMaxHealth() * 0.2f);
            });
            context.waitTicks(260);
            onServer(world, server -> look(player(server), end(server), new Vec3(25, 100, 35), new Vec3(0, 170, 0)));
            shot(context, world, 20, "hardwrought-dragon-rage-sky");
            onServer(world, server -> look(player(server), end(server), new Vec3(25, 110, 35), new Vec3(0, 62, 0)));
            shot(context, world, 10, "hardwrought-dragon-rage-island");
            // Off to where it was last seen, until it is near again, so that it is loaded when it dies.
            for (int tries = 0; tries < 6; tries++) {
                boolean[] found = new boolean[1];
                onServer(world, server -> {
                    found[0] = !end(server).getDragons().isEmpty();
                    if (!found[0] && lastSeen != null) look(player(server), end(server), lastSeen.add(20, 8, 20), lastSeen);
                });
                if (found[0]) break;
                context.waitTicks(30);
            }
            onServer(world, server -> dragon(server).setHealth(1));
            onServer(world, server -> dragon(server).hurtServer(end(server), end(server).damageSources().playerAttack(player(server)), 1000));
            shotAtDragon(context, world, 120, "hardwrought-dragon-dying");
            // Its last moments, from where it dies: the void falling into one point, and bursting.
            Vec3[] where = new Vec3[1];
            onServer(world, server -> where[0] = dragon(server).position());
            onServer(world, server -> look(player(server), end(server), where[0].add(18, 6, 18), where[0]));
            shot(context, world, 61, "hardwrought-dragon-dying-implosion");
            shot(context, world, 10, "hardwrought-dragon-dying-burst");
        }
    }

    private static void crystal(ClientGameTestContext context, TestSingleplayerContext world) {
        onServer(world, server -> {
            EndCrystal nearest = null;
            for (EndCrystal crystal : end(server).getEntitiesOfClass(EndCrystal.class, new AABB(BlockPos.ZERO).inflate(150))) {
                if (nearest == null || crystal.distanceToSqr(STAND) < nearest.distanceToSqr(STAND)) nearest = crystal;
            }
            if (nearest == null) return;
            Vec3 at = nearest.position();
            look(player(server), end(server), at.add(8, 2, 8), at.add(0, 1, 0));
            nearest.hurtServer(end(server), end(server).damageSources().playerAttack(player(server)), 12);
        });
        shot(context, world, 3, "hardwrought-dragon-crystal-ward-hit");
        shot(context, world, 20, "hardwrought-dragon-crystal-ward");
        onServer(world, server -> look(player(server), end(server), STAND, dragon(server).position()));
    }

    private static void move(ClientGameTestContext context, TestSingleplayerContext world, String move, int ticks, String name) {
        onServer(world, server -> ((DragonHunt) dragon(server).getPhaseManager().getCurrentPhase()).testMove(move));
        if (name != null) shotAtDragon(context, world, ticks, name);
    }

    private static void onServer(TestSingleplayerContext world, Consumer<MinecraftServer> action) {
        world.getServer().runOnServer(action::accept);
    }

    private static ServerLevel end(MinecraftServer server) {
        return server.getLevel(Level.END);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static EnderDragon dragon(MinecraftServer server) {
        var dragons = end(server).getDragons();
        if (dragons.isEmpty()) throw new AssertionError("The dragon is in the End");
        lastSeen = dragons.getFirst().position();
        return dragons.getFirst();
    }

    /** The player turned on the dragon from where they stand, and a picture taken. */
    private static void shotAtDragon(ClientGameTestContext context, TestSingleplayerContext world, int ticks, String name) {
        context.waitTicks(ticks);
        onServer(world, server -> {
            ServerPlayer player = player(server);
            look(player, end(server), player.position(), dragon(server).position().add(0, 2, 0));
        });
        shot(context, world, 2, name);
    }

    private static void look(ServerPlayer player, ServerLevel level, Vec3 at, Vec3 target) {
        Vec3 eye = at.add(0, player.getEyeHeight(), 0);
        Vec3 d = target.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), yaw, pitch, true);
    }

    private static void shot(ClientGameTestContext context, TestSingleplayerContext world, int ticks, String name) {
        context.waitTicks(ticks);
        context.takeScreenshot(name);
    }
}
