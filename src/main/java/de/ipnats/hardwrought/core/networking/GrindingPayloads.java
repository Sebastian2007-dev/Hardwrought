package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.smithing.Grinding;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The messages of a session at the grindstone: open, and one message for every pass.
 *
 * <p>The moment of a pass is judged on the client — it is a matter of a few frames, which no round
 * trip to the server would survive — and the server keeps the rest: whether there is a part in the
 * hand, whether it can still be ground, and what the pass does to it.
 */
public final class GrindingPayloads {
    private GrindingPayloads() { }

    /** Server to client: the part in the main hand goes to the grindstone at this position. */
    public record Open(BlockPos grindstone) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Hardwrought.id("grinding_open_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buffer, open) -> buffer.writeBlockPos(open.grindstone), buffer -> new Open(buffer.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: one pass over this grindstone, and how well it was timed. */
    public record Pass(BlockPos grindstone, Grinding.Outcome outcome) implements CustomPacketPayload {
        public static final Type<Pass> TYPE = new Type<>(Hardwrought.id("grinding_pass_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pass> CODEC = StreamCodec.of(
                (buffer, pass) -> {
                    buffer.writeBlockPos(pass.grindstone);
                    buffer.writeByte(pass.outcome.ordinal());
                },
                buffer -> new Pass(buffer.readBlockPos(),
                        Grinding.Outcome.values()[Math.clamp(buffer.readByte(), 0, Grinding.Outcome.values().length - 1)]));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
