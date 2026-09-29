package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** What the ore drill's screen shows, and the screen asking for it again while it is open. */
public final class OreDrillPayloads {
    /** Never more ores in one answer than this; the rock tables hold far fewer. */
    public static final int MAX_ORES = 64;

    private OreDrillPayloads() { }

    /**
     * One ore the chunk under the drill holds.
     *
     * @param ore      the ore block's id
     * @param needs    the drill tier it needs
     * @param share    its share of what this drill brings up, 0 to 1; -1 where the drill cannot reach it
     * @param presence its share of everything the chunk holds, reachable or not, 0 to 1
     * @param known    how well the player knows it, as a knowledge level ordinal
     */
    public record Ore(Identifier ore, int needs, float share, float presence, int known) { }

    /**
     * The drill as it stands.
     *
     * @param open      true when the player has just used the drill, false for an update to an open screen
     * @param scan      true for the creative scanner's reading of a chunk rather than a drill
     * @param tier      the frame's tier level, 0 where the frame is not complete
     * @param missing   frame blocks still missing
     * @param status    {@code OreDrillBlockEntity.Status} ordinal
     * @param speed     turns per minute driven into it
     * @param seconds   seconds between two pieces of ore at the present speed, 0 where it is not working
     * @param progress  how far the next piece is, 0 to 1
     * @param output    the side the ore leaves by, as a 3D data value
     * @param into      the block the ore is put into; null where it falls to the ground
     * @param chunkX    the chunk worked
     */
    public record Info(boolean open, boolean scan, BlockPos pos, int tier, int missing, int status, float speed, float seconds,
                       float progress, int output, Identifier into, int chunkX, int chunkZ, List<Ore> ores)
            implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(Hardwrought.id("ore_drill_info_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Info> CODEC = new StreamCodec<>() {
            @Override
            public Info decode(RegistryFriendlyByteBuf buffer) {
                boolean open = buffer.readBoolean();
                boolean scan = buffer.readBoolean();
                BlockPos pos = buffer.readBlockPos();
                int tier = buffer.readVarInt();
                int missing = buffer.readVarInt();
                int status = buffer.readVarInt();
                float speed = buffer.readFloat();
                float seconds = buffer.readFloat();
                float progress = buffer.readFloat();
                int output = buffer.readVarInt();
                Identifier into = buffer.readBoolean() ? buffer.readIdentifier() : null;
                int chunkX = buffer.readVarInt();
                int chunkZ = buffer.readVarInt();
                int count = Math.min(buffer.readVarInt(), MAX_ORES);
                List<Ore> ores = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ores.add(new Ore(buffer.readIdentifier(), buffer.readVarInt(), buffer.readFloat(),
                            buffer.readFloat(), buffer.readVarInt()));
                }
                return new Info(open, scan, pos, tier, missing, status, speed, seconds, progress, output, into,
                        chunkX, chunkZ, ores);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Info info) {
                buffer.writeBoolean(info.open());
                buffer.writeBoolean(info.scan());
                buffer.writeBlockPos(info.pos());
                buffer.writeVarInt(info.tier());
                buffer.writeVarInt(info.missing());
                buffer.writeVarInt(info.status());
                buffer.writeFloat(info.speed());
                buffer.writeFloat(info.seconds());
                buffer.writeFloat(info.progress());
                buffer.writeVarInt(info.output());
                buffer.writeBoolean(info.into() != null);
                if (info.into() != null) buffer.writeIdentifier(info.into());
                buffer.writeVarInt(info.chunkX());
                buffer.writeVarInt(info.chunkZ());
                List<Ore> ores = info.ores().subList(0, Math.min(info.ores().size(), MAX_ORES));
                buffer.writeVarInt(ores.size());
                for (Ore ore : ores) {
                    buffer.writeIdentifier(ore.ore());
                    buffer.writeVarInt(ore.needs());
                    buffer.writeFloat(ore.share());
                    buffer.writeFloat(ore.presence());
                    buffer.writeVarInt(ore.known());
                }
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Sent by an open drill screen once a second, so what it shows stays current. */
    public record Watch(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Watch> TYPE = new Type<>(Hardwrought.id("ore_drill_watch_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Watch> CODEC = new StreamCodec<>() {
            @Override
            public Watch decode(RegistryFriendlyByteBuf buffer) {
                return new Watch(buffer.readBlockPos());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Watch watch) {
                buffer.writeBlockPos(watch.pos());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
