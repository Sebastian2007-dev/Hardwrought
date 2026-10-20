package de.ipnats.hardwrought.storage;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A store room as one desk sees it: every chest a port stands against, along the tubes that lead
 * back to the desk.
 *
 * <p>Nothing of this is kept. The tubes are followed afresh whenever the desk is asked for
 * something, so there is no network to go stale when a tube is broken or a chest is carried off —
 * and nothing that ticks while nobody stands at the desk. What is found is only ever the chests
 * themselves: an item lies in a chest the whole time, and the desk is a long arm, not a second
 * store.
 *
 * <p>A port reaches into its chest through the face it touches, the way a hopper there would. A
 * plain chest does not care; a furnace does, and so a port on a furnace's side feeds it and one
 * underneath empties it.
 */
public final class StorageNetwork {
    /** How many tubes, ports and desks one room may have before the rest is left unreached. */
    public static final int MAX_PARTS = 1024;
    /** How many chests one desk reaches. */
    public static final int MAX_STORES = 128;

    /** One chest on the network, and the face of it the port touches. */
    private record Store(Container container, Direction side) {
        int[] slots() {
            if (container instanceof WorldlyContainer sided) return sided.getSlotsForFace(side);
            int[] all = new int[container.getContainerSize()];
            for (int slot = 0; slot < all.length; slot++) all[slot] = slot;
            return all;
        }

        boolean takes(int slot, ItemStack stack) {
            return container.canPlaceItem(slot, stack)
                    && (!(container instanceof WorldlyContainer sided) || sided.canPlaceItemThroughFace(slot, stack, side));
        }

        boolean gives(int slot, ItemStack stack) {
            return !(container instanceof WorldlyContainer sided) || sided.canTakeItemThroughFace(slot, stack, side);
        }
    }

    private final List<Store> stores = new ArrayList<>();
    private final List<Maker> makers = new ArrayList<>();

    /** A block that joins the network without being a tube, a port or a desk — an assembler. */
    public interface Member {
        boolean joinsStorage(BlockState state);
    }

    /**
     * Something on the network that makes a thing (stage E5 of the electricity roadmap): a desk asked for
     * what the network has none of orders it here, and it puts what it makes back into the network.
     */
    public interface Maker {
        /** What it makes; empty for nothing. */
        ItemStack makes();

        /** Asked to make this many for the desk at this position. False where it cannot. */
        boolean order(BlockPos desk, int count);
    }

    private StorageNetwork() { }

    /** Whether this block carries the network on: a tube, a port, a desk or a member such as an assembler. */
    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof StorageTubeBlock || state.getBlock() instanceof StoragePortBlock
                || state.getBlock() instanceof StorageDeskBlock
                || state.getBlock() instanceof Member member && member.joinsStorage(state);
    }

    /** The makers on the network. */
    public List<Maker> makers() {
        return List.copyOf(makers);
    }

    /** A maker on the network that makes this kind of thing, or null. */
    public Maker maker(ItemStack like) {
        for (Maker maker : makers) if (ItemStack.isSameItemSameComponents(maker.makes(), like.copyWithCount(maker.makes().getCount()))) return maker;
        return null;
    }

    /**
     * Every tube, port, desk and member joined with this block along the tubes, itself first. The
     * current a desk runs on is carried along the tubes this way: a copper coil set on any tube or port
     * feeds every desk the tubes reach (see {@code Electricity.joints}).
     */
    public static List<BlockPos> parts(Level level, BlockPos start) {
        List<BlockPos> parts = new ArrayList<>();
        if (!isPart(level.getBlockState(start))) return parts;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            parts.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.size() >= MAX_PARTS || seen.contains(next) || !level.isLoaded(next)) continue;
                if (!isPart(level.getBlockState(next))) continue;
                seen.add(next);
                queue.add(next);
            }
        }
        return parts;
    }

    /** Whether current is carried along this block to the desks on its tubes: a tube or a port. */
    public static boolean carriesCurrent(BlockState state) {
        return state.getBlock() instanceof StorageTubeBlock || state.getBlock() instanceof StoragePortBlock;
    }

    /** A tube or port set down or taken away: the desks on either side of it work out their current again. */
    public static void rewire(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        Set<BlockPos> done = new HashSet<>();
        for (Direction side : Direction.values()) {
            for (BlockPos part : parts(level, pos.relative(side))) {
                if (!done.add(part)) continue;
                if (level.getBlockState(part).getBlock() instanceof StorageDeskBlock) {
                    de.ipnats.hardwrought.electricity.Electricity.update(level, part);
                }
            }
        }
    }

    /** Follows the tubes out from a desk and gathers what its ports stand against, nearest first. */
    public static StorageNetwork find(Level level, BlockPos desk) {
        StorageNetwork network = new StorageNetwork();
        Set<BlockPos> seen = new HashSet<>();
        // A double chest with a port on each half is still one chest.
        Set<BlockPos> claimed = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(desk.immutable());
        queue.add(desk.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof Member && level.getBlockEntity(pos) instanceof Maker maker && !maker.makes().isEmpty()) {
                network.makers.add(maker);
            }
            if (state.getBlock() instanceof StoragePortBlock && network.stores.size() < MAX_STORES) {
                Direction facing = state.getValue(StoragePortBlock.FACING);
                network.attach(level, pos.relative(facing), facing.getOpposite(), claimed);
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.size() >= MAX_PARTS || seen.contains(next) || !level.isLoaded(next)) continue;
                if (!isPart(level.getBlockState(next))) continue;
                seen.add(next);
                queue.add(next);
            }
        }
        return network;
    }

    private void attach(Level level, BlockPos pos, Direction side, Set<BlockPos> claimed) {
        if (claimed.contains(pos) || !level.isLoaded(pos)) return;
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof Container container) || sealed(entity)) return;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos half = ChestBlock.getConnectedBlockPos(pos, state);
            BlockEntity other = level.getBlockEntity(half);
            if (other != null && sealed(other)) return;
            Container both = ChestBlock.getContainer(chest, state, level, pos, true);
            if (both != null) {
                container = both;
                claimed.add(half);
            }
        }
        claimed.add(pos);
        stores.add(new Store(container, side));
    }

    /** Loot not yet rolled, or a lock on the lid: the desk does not reach in. */
    private static boolean sealed(BlockEntity entity) {
        if (entity instanceof RandomizableContainer loot && loot.getLootTable() != null) return true;
        return entity instanceof BaseContainerBlockEntity lockable && lockable.isLocked();
    }

    /** How many chests the desk reaches. */
    public int stores() {
        return stores.size();
    }

    /** Everything that can be taken out, like put with like, in the order it was come upon. */
    public Map<ItemVariant, Integer> contents() {
        Map<ItemVariant, Integer> held = new LinkedHashMap<>();
        for (Store store : stores) {
            for (int slot : store.slots()) {
                ItemStack stack = store.container().getItem(slot);
                if (stack.isEmpty() || !store.gives(slot, stack)) continue;
                held.merge(ItemVariant.of(stack), stack.getCount(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
            }
        }
        return held;
    }

    /** How many of this there are to take. */
    public int count(ItemStack like) {
        long count = 0;
        for (Store store : stores) {
            for (int slot : store.slots()) {
                ItemStack stack = store.container().getItem(slot);
                if (ItemStack.isSameItemSameComponents(stack, like) && store.gives(slot, stack)) count += stack.getCount();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /**
     * Puts a stack away: onto heaps of the same thing first, wherever they lie, and only then into
     * empty room — so that a thing ends up where the rest of it already is. Returns what found no
     * place.
     */
    public ItemStack insert(ItemStack stack) {
        ItemStack left = stack.copy();
        for (int pass = 0; pass < 2 && !left.isEmpty(); pass++) {
            for (Store store : stores) {
                boolean changed = false;
                for (int slot : store.slots()) {
                    if (left.isEmpty()) break;
                    Container container = store.container();
                    ItemStack there = container.getItem(slot);
                    if (pass == 0 ? !ItemStack.isSameItemSameComponents(there, left) : !there.isEmpty()) continue;
                    if (!store.takes(slot, left)) continue;
                    int room = container.getMaxStackSize(left) - there.getCount();
                    if (room <= 0) continue;
                    int moved = Math.min(room, left.getCount());
                    if (there.isEmpty()) container.setItem(slot, left.split(moved));
                    else {
                        there.grow(moved);
                        left.shrink(moved);
                    }
                    changed = true;
                }
                if (changed) store.container().setChanged();
            }
        }
        return left;
    }

    /** Takes up to so many of a thing out, from the nearest chest that has it first. */
    public ItemStack extract(ItemStack like, int amount) {
        ItemStack taken = ItemStack.EMPTY;
        int wanted = amount;
        for (Store store : stores) {
            boolean changed = false;
            for (int slot : store.slots()) {
                if (wanted <= 0) break;
                ItemStack there = store.container().getItem(slot);
                if (!ItemStack.isSameItemSameComponents(there, like) || !store.gives(slot, there)) continue;
                ItemStack part = store.container().removeItem(slot, Math.min(wanted, there.getCount()));
                if (part.isEmpty()) continue;
                wanted -= part.getCount();
                if (taken.isEmpty()) taken = part;
                else taken.grow(part.getCount());
                changed = true;
            }
            if (changed) store.container().setChanged();
            if (wanted <= 0) break;
        }
        return taken;
    }
}
