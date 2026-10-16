package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * What the compendium's multiblock section needs to know of a player: which of the blocks that
 * structures are built of they have studied. Only those, so the section can show a structure whose
 * every part is known and keep the rest as unread as the compendium keeps everything else.
 */
public final class MultiblockKnowledge {
    /** The client asks. */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Hardwrought.id("multiblock_knowledge_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The server answers: the structure blocks this player has studied. */
    public record Known(List<Identifier> studied) implements CustomPacketPayload {
        public static final Type<Known> TYPE = new Type<>(Hardwrought.id("multiblock_knowledge"));
        public static final StreamCodec<ByteBuf, Known> CODEC =
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Known::new, Known::studied);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private MultiblockKnowledge() { }

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Known.TYPE, Known.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) ->
                ServerPlayNetworking.send(context.player(), new Known(studied(context.player()))));
    }

    /** The blocks of all the structures that this player has studied. */
    public static List<Identifier> studied(ServerPlayer player) {
        var runtime = CoreLifecycle.find(player.level().getServer());
        List<Identifier> studied = new ArrayList<>();
        if (runtime == null) return studied;
        PlayerKnowledge knowledge = runtime.knowledge().knowledge(player);
        for (Multiblocks.Multiblock multiblock : Multiblocks.ALL) {
            for (Identifier block : multiblock.blocks()) {
                if (!studied.contains(block) && knowledge.level(block).ordinal() >= KnowledgeLevel.STUDIED.ordinal()) {
                    studied.add(block);
                }
            }
        }
        return studied;
    }
}
