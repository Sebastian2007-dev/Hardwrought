package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The packets of an open storage desk: the list of what its chests hold, and the clicks on it.
 *
 * <p>The list is not a row of slots, so it cannot ride on the menu's own syncing. It names kinds
 * and counts; a click names the kind that was clicked. See {@code StorageDeskMenu}.
 */
public final class StoragePayloads {
    /** A store room of more kinds than this shows the first of them; nothing is lost, only unlisted. */
    public static final int MAX_ENTRIES = 2048;

    /** Left click: fetch a stack, or put away what is on the cursor. */
    public static final int TAKE = 0;
    /** Right click: fetch half a stack, or set one down. */
    public static final int HALF = 1;
    /** Shift-click: a stack straight into the inventory. */
    public static final int QUICK = 2;
    /** One notch of the wheel: one of the kind out of the inventory and into the store room. */
    public static final int PUT_ONE = 3;
    /** One notch of the wheel: one of the kind out of the store room and into the inventory. */
    public static final int FETCH_ONE = 4;
    /** The button by a workshop bench's grid: everything on it back into the store room. */
    public static final int CLEAR_GRID = 5;

    private StoragePayloads() { }

    /**
     * One kind of thing in the store room.
     *
     * @param stack one of it; the number travels apart, because it may be many stacks' worth
     */
    public record Entry(ItemStack stack, int count) { }

    /**
     * What the desk reaches.
     *
     * @param containerId the open menu this is for, so a list that arrives late is not shown in the next
     * @param stores      how many chests are on the tubes
     * @param powered     whether the desk has current; without it the list is empty
     */
    public record Contents(int containerId, List<Entry> entries, int stores, boolean powered)
            implements CustomPacketPayload {
        public static final Type<Contents> TYPE = new Type<>(Hardwrought.id("storage_contents_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Contents> CODEC = new StreamCodec<>() {
            @Override
            public Contents decode(RegistryFriendlyByteBuf buffer) {
                int containerId = buffer.readVarInt();
                int size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
                List<Entry> entries = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    entries.add(new Entry(ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt()));
                }
                return new Contents(containerId, entries, buffer.readVarInt(), buffer.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Contents contents) {
                buffer.writeVarInt(contents.containerId);
                buffer.writeVarInt(contents.entries.size());
                for (Entry entry : contents.entries) {
                    ItemStack.STREAM_CODEC.encode(buffer, entry.stack());
                    buffer.writeVarInt(entry.count());
                }
                buffer.writeVarInt(contents.stores);
                buffer.writeBoolean(contents.powered);
            }
        };

        public Contents {
            entries = List.copyOf(entries);
            if (entries.size() > MAX_ENTRIES) entries = entries.subList(0, MAX_ENTRIES);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * A click on the list.
     *
     * @param clicked the kind that was clicked, empty for bare room
     * @param action  one of {@link #TAKE}, {@link #HALF}, {@link #QUICK}, {@link #PUT_ONE} and {@link #FETCH_ONE}
     */
    public record Click(int containerId, ItemStack clicked, int action) implements CustomPacketPayload {
        public static final Type<Click> TYPE = new Type<>(Hardwrought.id("storage_click_v1"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Click> CODEC = new StreamCodec<>() {
            @Override
            public Click decode(RegistryFriendlyByteBuf buffer) {
                return new Click(buffer.readVarInt(), ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(buffer),
                        buffer.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Click click) {
                buffer.writeVarInt(click.containerId);
                ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(buffer, click.clicked);
                buffer.writeVarInt(click.action);
            }
        };

        public Click {
            if (clicked == null) clicked = ItemStack.EMPTY;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
