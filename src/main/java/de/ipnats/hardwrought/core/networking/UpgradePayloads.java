package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The messages of working a piece up a tier at the smithing table: open, and how truly the pattern
 * was traced.
 *
 * <p>The trace is judged on the client, frame by frame, as the grindstone's pass is; the server keeps
 * what matters — which upgrade it is, that its template and bar are still there, and what the result
 * is worth.
 */
public final class UpgradePayloads {
    private UpgradePayloads() { }

    /**
     * Server to client: the piece in the main hand can be worked up at this table.
     *
     * @param pattern which template's pattern: 0 netherite, 1 mithril, 2 adamant
     * @param result  what it becomes
     */
    public record Open(BlockPos table, int pattern, Identifier result) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Hardwrought.id("upgrade_open_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buffer, open) -> {
                    buffer.writeBlockPos(open.table);
                    buffer.writeVarInt(open.pattern);
                    buffer.writeIdentifier(open.result);
                },
                buffer -> new Open(buffer.readBlockPos(), buffer.readVarInt(), buffer.readIdentifier()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the pattern was traced this truly, 0 to 1. */
    public record Finish(BlockPos table, float accuracy) implements CustomPacketPayload {
        public static final Type<Finish> TYPE = new Type<>(Hardwrought.id("upgrade_finish_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Finish> CODEC = StreamCodec.of(
                (buffer, finish) -> {
                    buffer.writeBlockPos(finish.table);
                    buffer.writeFloat(finish.accuracy);
                },
                buffer -> {
                    BlockPos table = buffer.readBlockPos();
                    float accuracy = buffer.readFloat();
                    return new Finish(table, Float.isFinite(accuracy) ? Math.clamp(accuracy, 0f, 1f) : 0f);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
