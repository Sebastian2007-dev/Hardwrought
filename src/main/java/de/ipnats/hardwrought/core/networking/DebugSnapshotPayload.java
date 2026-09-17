package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Read-only diagnostics. No serverbound state-editing payload exists. */
public record DebugSnapshotPayload(List<String> lines) implements CustomPacketPayload {
    public static final int MAX_LINES = 32;
    public static final int MAX_LINE_LENGTH = 240;
    public static final Type<DebugSnapshotPayload> TYPE = new Type<>(Hardwrought.id("debug_snapshot_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugSnapshotPayload> CODEC = new StreamCodec<>() {
        @Override
        public DebugSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_LINES) throw new IllegalArgumentException("Invalid debug line count");
            List<String> lines = new ArrayList<>(count);
            for (int index = 0; index < count; index++) lines.add(buffer.readUtf(MAX_LINE_LENGTH));
            return new DebugSnapshotPayload(lines);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DebugSnapshotPayload payload) {
            buffer.writeVarInt(payload.lines.size());
            for (String line : payload.lines) buffer.writeUtf(line, MAX_LINE_LENGTH);
        }
    };

    public DebugSnapshotPayload {
        lines = List.copyOf(lines);
        if (lines.size() > MAX_LINES || lines.stream().anyMatch(line -> line.length() > MAX_LINE_LENGTH)) {
            throw new IllegalArgumentException("Debug snapshot exceeds protocol limits");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
