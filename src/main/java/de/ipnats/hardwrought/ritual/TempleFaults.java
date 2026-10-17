package de.ipnats.hardwrought.ritual;

import de.ipnats.hardwrought.Hardwrought;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * A temple that does not stand yet, shown to whoever asked its altar: every block of it still wrong or
 * missing is marked red in the world (by the client, {@code TempleFaultsClient}), the block the plan wants
 * there drawn over it, until it is put right.
 */
public final class TempleFaults {
    /** How many are marked at most: the nearest the altar. */
    static final int MOST = 4096;

    /** To the player who asked: where round this altar the temple of this theme is still wrong. */
    public record Faults(BlockPos altar, int theme, List<BlockPos> wrong) implements CustomPacketPayload {
        public static final Type<Faults> TYPE = new Type<>(Hardwrought.id("temple_faults"));
        public static final StreamCodec<ByteBuf, Faults> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Faults::altar, ByteBufCodecs.VAR_INT, Faults::theme,
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), Faults::wrong, Faults::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public Temple.Theme themeOf() {
            return Temple.Theme.values()[Math.clamp(theme, 0, Temple.Theme.values().length - 1)];
        }
    }

    private TempleFaults() { }

    static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(Faults.TYPE, Faults.CODEC);
    }

    /** Shows this player what is still wrong in the temple round this altar. */
    static void show(ServerLevel level, BlockPos altar, Temple.Theme theme, ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, Faults.TYPE)) return;
        ServerPlayNetworking.send(player, new Faults(altar, theme.ordinal(), Temple.wrong(level, altar, theme, MOST)));
    }
}
