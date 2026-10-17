package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.end.EndRules;
import de.ipnats.hardwrought.end.VoidLight;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** The floor of light in the End, looked at: from on it, from beside it, from beneath it, and as it fails. */
public final class EndClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            world.getConnection().waitForChunksRender();
            Vec3 top = EndRules.ARRIVAL;
            world.getServer().runOnServer(server -> {
                ServerPlayer player = player(server);
                player.setGameMode(GameType.CREATIVE);
                look(player, end(server), top, top.add(-6, -1, 0));
                EndRules.lightPlatform(end(server));
            });
            shot(context, world, 80, "hardwrought-end-platform-standing");
            world.getServer().runOnServer(server -> look(player(server), end(server), top.add(5, 2.5, 6), top));
            shot(context, world, 30, "hardwrought-end-platform-beside");
            world.getServer().runOnServer(server -> look(player(server), end(server), top.add(3, -6, 3), top));
            shot(context, world, 30, "hardwrought-end-platform-beneath");
            world.getServer().runOnServer(server -> {
                ServerLevel end = end(server);
                BlockPos middle = BlockPos.containing(top).below();
                for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-2, 0, -2), middle.offset(2, 0, 2))) {
                    if (end.getBlockEntity(pos) instanceof VoidLight.Entity light) light.testFadeIn(end, 100);
                }
                look(player(server), end, top.add(5, 2.5, 6), top);
            });
            shot(context, world, 40, "hardwrought-end-platform-fading");
            // The broken island: from where travellers arrive, from among the fragments, and from beneath.
            Vec3 middle = new Vec3(0, 62, 0);
            world.getServer().runOnServer(server -> look(player(server), end(server), top.add(0, 8, 0), middle));
            shot(context, world, 120, "hardwrought-end-arena-arrival");
            world.getServer().runOnServer(server -> look(player(server), end(server), new Vec3(70, 95, 55), middle));
            shot(context, world, 100, "hardwrought-end-arena-among");
            world.getServer().runOnServer(server -> look(player(server), end(server), new Vec3(30, 20, 70), middle.add(0, 20, 0)));
            shot(context, world, 100, "hardwrought-end-arena-beneath");
        }
    }

    private static ServerLevel end(MinecraftServer server) {
        return server.getLevel(Level.END);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    /** Puts the player, flying, at a place, looking at a point. */
    private static void look(ServerPlayer player, ServerLevel level, Vec3 at, Vec3 target) {
        Vec3 eye = at.add(0, player.getEyeHeight(), 0);
        Vec3 d = target.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, at.x, at.y, at.z, Set.of(), yaw, pitch, true);
    }

    private static void shot(ClientGameTestContext context, TestSingleplayerContext world, int ticks, String name) {
        context.waitTicks(ticks);
        world.getConnection().waitForChunksRender();
        context.takeScreenshot(name);
    }
}
