package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The packets behind the overlay that names what a player is looking at.
 *
 * <p>The client can see the block in front of it; what it cannot know is whether this player has
 * found out what that block is. So it asks about one thing at a time, and the answer is a level and
 * nothing else — never the whole of what the player knows. What a chest holds the client does not
 * know either until it is opened, so that is asked the same way.
 */
public final class LookPayloads {
    private LookPayloads() { }

    /**
     * What is this, as far as I know?
     *
     * @param entity  true where the subject is an entity type rather than an item
     * @param subject the item of the block looked at, or the type of the creature
     */
    public record Request(boolean entity, Identifier subject) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Hardwrought.id("look_request_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = new StreamCodec<>() {
            @Override
            public Request decode(RegistryFriendlyByteBuf buffer) {
                return new Request(buffer.readBoolean(), buffer.readIdentifier());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Request request) {
                buffer.writeBoolean(request.entity);
                buffer.writeIdentifier(request.subject);
            }
        };

        public Request {
            if (subject == null) throw new IllegalArgumentException("A look needs a subject");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** What is in the thing standing here? Asked only of a block the client sees holding something. */
    public record Inside(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Inside> TYPE = new Type<>(Hardwrought.id("look_inside_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Inside> CODEC = new StreamCodec<>() {
            @Override
            public Inside decode(RegistryFriendlyByteBuf buffer) {
                return new Inside(buffer.readBlockPos());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Inside inside) {
                buffer.writeBlockPos(inside.pos);
            }
        };

        public Inside {
            if (pos == null) throw new IllegalArgumentException("A look inside needs a place");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * What a container holds, like stacks already put together.
     *
     * @param stacks   what is in it, each with how well the asking player knows it
     * @param more     how many further kinds of thing did not fit on the panel
     * @param progress how far along the work in it is from 0 to 1, negative where it does none
     * @param tanks    the liquids and gases in it, each with how much of how much
     */
    public record Contents(BlockPos pos, List<Held> stacks, int more, float progress, List<Tank> tanks)
            implements CustomPacketPayload {
        public static final Type<Contents> TYPE = new Type<>(Hardwrought.id("look_contents_v2"));
        /** Liquids and gases shown at most: a machine's inlet and outlet, a gas in and a gas out. */
        public static final int MAX_TANKS = 4;

        /**
         * One liquid or gas in it.
         *
         * @param name     what it is, or that the tank is empty
         * @param icon     the liquid or gas as the book draws it, empty for an empty tank
         * @param amount   how much, in millibuckets or units of gas
         * @param capacity how much fits
         * @param gas      whether it is counted in units of gas rather than millibuckets
         */
        public record Tank(Component name, ItemStack icon, long amount, long capacity, boolean gas) { }
        /** Three rows of nine: a single chest with nothing alike in it. */
        public static final int MAX_STACKS = 27;

        /**
         * One kind of thing in the container.
         *
         * @param stack one of it; the number travels apart, because put together it may be more than
         *              a stack is allowed to hold
         * @param level the ordinal of a {@link de.ipnats.hardwrought.knowledge.KnowledgeLevel}
         */
        public record Held(ItemStack stack, int count, int level) { }

        public static final StreamCodec<RegistryFriendlyByteBuf, Contents> CODEC = new StreamCodec<>() {
            @Override
            public Contents decode(RegistryFriendlyByteBuf buffer) {
                BlockPos pos = buffer.readBlockPos();
                int size = Math.min(buffer.readVarInt(), MAX_STACKS);
                List<Held> stacks = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    stacks.add(new Held(ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarInt()));
                }
                int more = buffer.readVarInt();
                float progress = buffer.readFloat();
                int tankCount = Math.min(buffer.readVarInt(), MAX_TANKS);
                List<Tank> tanks = new ArrayList<>(tankCount);
                for (int index = 0; index < tankCount; index++) {
                    tanks.add(new Tank(net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buffer),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarLong(), buffer.readVarLong(),
                            buffer.readBoolean()));
                }
                return new Contents(pos, stacks, more, progress, tanks);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Contents contents) {
                buffer.writeBlockPos(contents.pos);
                buffer.writeVarInt(contents.stacks.size());
                for (Held held : contents.stacks) {
                    ItemStack.STREAM_CODEC.encode(buffer, held.stack());
                    buffer.writeVarInt(held.count());
                    buffer.writeVarInt(held.level());
                }
                buffer.writeVarInt(contents.more);
                buffer.writeFloat(contents.progress);
                buffer.writeVarInt(contents.tanks.size());
                for (Tank tank : contents.tanks) {
                    net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer, tank.name());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, tank.icon());
                    buffer.writeVarLong(tank.amount());
                    buffer.writeVarLong(tank.capacity());
                    buffer.writeBoolean(tank.gas());
                }
            }
        };

        public Contents {
            if (pos == null) throw new IllegalArgumentException("Contents need a place");
            stacks = List.copyOf(stacks);
            if (stacks.size() > MAX_STACKS) stacks = stacks.subList(0, MAX_STACKS);
            tanks = List.copyOf(tanks.size() > MAX_TANKS ? tanks.subList(0, MAX_TANKS) : tanks);
        }

        /** The answer for a block that holds nothing, or nothing this player may be told about. */
        public static Contents none(BlockPos pos) {
            return new Contents(pos, List.of(), 0, -1.0F, List.of());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * How well the asking player knows the subject.
     *
     * @param level the ordinal of a {@link de.ipnats.hardwrought.knowledge.KnowledgeLevel}
     */
    public record Answer(boolean entity, Identifier subject, int level) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = new Type<>(Hardwrought.id("look_answer_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = new StreamCodec<>() {
            @Override
            public Answer decode(RegistryFriendlyByteBuf buffer) {
                return new Answer(buffer.readBoolean(), buffer.readIdentifier(), buffer.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Answer answer) {
                buffer.writeBoolean(answer.entity);
                buffer.writeIdentifier(answer.subject);
                buffer.writeVarInt(answer.level);
            }
        };

        public Answer {
            if (subject == null) throw new IllegalArgumentException("An answer needs a subject");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
