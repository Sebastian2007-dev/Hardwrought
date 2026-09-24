package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.Hardwrought;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * Getting down on hands and knees at will, to go through a gap one block high.
 *
 * <p>Vanilla already has the pose — it is what a player falls into under a trapdoor — but only ever
 * as something that happens to them. Here the player chooses it: a key toggles it, and while it is
 * on the player crawls wherever they are. Turned off under a low ceiling, they go on crawling until
 * there is room to stand, which is vanilla's own rule for a pose that does not fit.
 */
public final class Crawling {
    private static boolean initialized;

    /** The crawl switch, kept on the player by a mixin. */
    public interface Crawler {
        boolean hardwrought$crawling();

        void hardwrought$setCrawling(boolean crawling);
    }

    /** Client to server: the player has turned crawling on or off. */
    public record Toggle(boolean crawling) implements CustomPacketPayload {
        public static final Type<Toggle> TYPE = new Type<>(Hardwrought.id("crawl_toggle"));
        public static final StreamCodec<ByteBuf, Toggle> CODEC =
                ByteBufCodecs.BOOL.map(Toggle::new, Toggle::crawling);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Crawling() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.serverboundPlay().register(Toggle.TYPE, Toggle.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Toggle.TYPE, (payload, context) -> {
            if (context.player() instanceof Crawler crawler) crawler.hardwrought$setCrawling(payload.crawling());
        });
    }

    public static boolean isCrawling(Player player) {
        return player instanceof Crawler crawler && crawler.hardwrought$crawling();
    }

    /**
     * The pose a player wants while crawling is switched on: down on the ground, unless something
     * else — sleeping, swimming, gliding, a vehicle — already decides it.
     */
    public static Pose desired(Player player, Pose vanilla) {
        if (!isCrawling(player)) return vanilla;
        if (vanilla != Pose.STANDING && vanilla != Pose.CROUCHING) return vanilla;
        if (player.isPassenger() || player.getAbilities().flying) return vanilla;
        return Pose.SWIMMING;
    }
}
