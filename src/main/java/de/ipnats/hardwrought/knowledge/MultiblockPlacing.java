package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.ritual.RitualContent;
import de.ipnats.hardwrought.ritual.Temple;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * In creative a projection set down is built at once: the whole structure stands where its ghost was,
 * in one click. A temple is set down as its plan has it, stairs turned and pedestals in place, round
 * the altar where the ghost had it.
 */
public final class MultiblockPlacing {
    /** How far from the player a structure may be set down. */
    private static final double REACH = 160;

    /** The client asks: this structure, centred on this anchor, turned so many quarters. */
    public record Place(Identifier id, BlockPos anchor, int turns) implements CustomPacketPayload {
        public static final Type<Place> TYPE = new Type<>(Hardwrought.id("multiblock_place"));
        public static final StreamCodec<ByteBuf, Place> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Place::id, BlockPos.STREAM_CODEC, Place::anchor, ByteBufCodecs.VAR_INT, Place::turns, Place::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private MultiblockPlacing() { }

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(Place.TYPE, Place.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Place.TYPE, (payload, context) -> place(context.player(), payload));
    }

    private static void place(ServerPlayer player, Place payload) {
        Multiblocks.Multiblock multiblock = Multiblocks.byId(payload.id());
        if (multiblock == null || !player.isCreative()) return;
        if (player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(payload.anchor())) > REACH) return;
        build((ServerLevel) player.level(), multiblock, payload.anchor(), Math.floorMod(payload.turns(), 4));
        player.level().playSound(null, payload.anchor(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    /** Builds the structure whole, laid out as its projection lays it: centred on the anchor, turned. */
    public static void build(ServerLevel level, Multiblocks.Multiblock multiblock, BlockPos anchor, int turns) {
        for (Temple.Theme theme : Temple.Theme.values()) {
            if (Temple.multiblock(theme) != multiblock) continue;
            BlockPos altar = find(multiblock, anchor, turns, BuiltInRegistries.BLOCK.getKey(RitualContent.ALTAR));
            if (altar != null) Temple.buildNow(level, altar, theme);
            return;
        }
        Rotation rotation = Rotation.values()[turns];
        for (int y = 0; y < multiblock.height(); y++) {
            for (int z = 0; z < multiblock.depth(); z++) {
                for (int x = 0; x < multiblock.width(); x++) {
                    char symbol = multiblock.at(x, y, z);
                    if (symbol == ' ') continue;
                    List<Identifier> blocks = multiblock.legend().get(symbol);
                    if (blocks == null || blocks.isEmpty()) continue;
                    BlockState state = BuiltInRegistries.BLOCK.getValue(blocks.getFirst()).defaultBlockState().rotate(rotation);
                    level.setBlock(at(multiblock, anchor, turns, x, y, z), state, Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Where in the world the first block of this kind goes. */
    private static BlockPos find(Multiblocks.Multiblock multiblock, BlockPos anchor, int turns, Identifier block) {
        for (int y = 0; y < multiblock.height(); y++) {
            for (int z = 0; z < multiblock.depth(); z++) {
                for (int x = 0; x < multiblock.width(); x++) {
                    char symbol = multiblock.at(x, y, z);
                    if (symbol != ' ' && multiblock.legend().getOrDefault(symbol, List.of()).contains(block)) {
                        return at(multiblock, anchor, turns, x, y, z);
                    }
                }
            }
        }
        return null;
    }

    /** The world position of a structure position, as the projection has it: centred on the anchor and turned. */
    public static BlockPos at(Multiblocks.Multiblock multiblock, BlockPos anchor, int turns, int x, int y, int z) {
        int dx = x - multiblock.width() / 2, dz = z - multiblock.depth() / 2;
        for (int i = 0; i < turns; i++) {
            int t = dx;
            dx = -dz;
            dz = t;
        }
        return anchor.offset(dx, y, dz);
    }
}
