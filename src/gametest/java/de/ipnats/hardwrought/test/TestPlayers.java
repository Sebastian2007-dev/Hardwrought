package de.ipnats.hardwrought.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Mock players for what the vanilla helpers cannot give: one in survival that is connected, in the
 * player list, and can be hurt. {@code makeMockServerPlayerInLevel} is always creative, and
 * {@code makeMockServerPlayer(SURVIVAL)} has no connection to send anything over.
 */
final class TestPlayers {
    private TestPlayers() { }

    /** A connected survival player standing at this place of the test, relative to it. */
    static ServerPlayer survivor(GameTestHelper helper, double x, double z) {
        var level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-survivor");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        // Until its client says it has loaded the world, a player cannot be hurt.
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        Vec3 at = helper.absoluteVec(new Vec3(x, 1, z));
        player.snapTo(at.x, at.y, at.z, 0, 0);
        return player;
    }
}
