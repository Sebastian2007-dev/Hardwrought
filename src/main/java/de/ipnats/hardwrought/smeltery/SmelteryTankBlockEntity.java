package de.ipnats.hardwrought.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The lava a smeltery tank holds, in millibuckets. The controller burns it as fuel. Each block keeps
 * its own share, but tanks joined face to face are filled and drained as one vessel.
 */
public class SmelteryTankBlockEntity extends BlockEntity {
    public static final int BUCKET = 1000;
    public static final int CAPACITY = 4 * BUCKET;

    private int lava;

    public SmelteryTankBlockEntity(BlockPos pos, BlockState state) {
        super(SmelteryBlocks.TANK_ENTITY, pos, state);
    }

    public int lava() {
        return lava;
    }

    /** Pours this much lava in; returns how much went in. */
    public int fill(int amount) {
        int added = Math.min(amount, CAPACITY - lava);
        if (added > 0) set(lava + added);
        return added;
    }

    /** Takes up to this much lava out; returns how much it took. */
    public int drain(int amount) {
        int taken = Math.min(amount, lava);
        if (taken > 0) set(lava - taken);
        return taken;
    }

    // ---------------------------------------------------------------- tanks joined into one

    /** Most tanks one vessel can take in: a whole wall of them, well past what anyone builds. */
    private static final int GROUP_LIMIT = 256;

    /** Every tank joined to this one, face to face, itself included. */
    public static java.util.List<SmelteryTankBlockEntity> group(Level level, BlockPos start) {
        java.util.List<SmelteryTankBlockEntity> found = new java.util.ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
        open.add(start.immutable());
        seen.add(start.immutable());
        while (!open.isEmpty() && found.size() < GROUP_LIMIT) {
            BlockPos pos = open.poll();
            if (!(level.getBlockEntity(pos) instanceof SmelteryTankBlockEntity tank)) continue;
            found.add(tank);
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next) && level.getBlockState(next).is(SmelteryBlocks.TANK)) open.add(next);
            }
        }
        return found;
    }

    /** The lava in the whole vessel this tank belongs to. */
    public static int lava(Level level, BlockPos pos) {
        int total = 0;
        for (SmelteryTankBlockEntity tank : group(level, pos)) total += tank.lava;
        return total;
    }

    /** The room left in the whole vessel this tank belongs to. */
    public static int room(Level level, BlockPos pos) {
        int total = 0;
        for (SmelteryTankBlockEntity tank : group(level, pos)) total += CAPACITY - tank.lava;
        return total;
    }

    /**
     * Pours lava into the vessel as into one: the lowest layer of tanks fills first, shared evenly
     * across it, then the next. Returns how much went in.
     */
    public static int fillGroup(Level level, BlockPos pos, int amount) {
        return spread(layers(level, pos, false), amount, true);
    }

    /** Takes lava from the vessel as from one: from the top layer down. Returns how much it took. */
    public static int drainGroup(Level level, BlockPos pos, int amount) {
        return spread(layers(level, pos, true), amount, false);
    }

    private static java.util.Collection<java.util.List<SmelteryTankBlockEntity>> layers(Level level, BlockPos pos,
                                                                                        boolean topFirst) {
        java.util.TreeMap<Integer, java.util.List<SmelteryTankBlockEntity>> byHeight =
                new java.util.TreeMap<>(topFirst ? java.util.Comparator.<Integer>reverseOrder()
                        : java.util.Comparator.<Integer>naturalOrder());
        for (SmelteryTankBlockEntity tank : group(level, pos)) {
            byHeight.computeIfAbsent(tank.worldPosition.getY(), y -> new java.util.ArrayList<>()).add(tank);
        }
        return byHeight.values();
    }

    /** Moves lava layer by layer, sharing each layer's part evenly so its surface stays level. */
    private static int spread(java.util.Collection<java.util.List<SmelteryTankBlockEntity>> layers, int amount, boolean in) {
        int moved = 0;
        for (java.util.List<SmelteryTankBlockEntity> layer : layers) {
            int left = amount - moved;
            while (left > 0) {
                java.util.List<SmelteryTankBlockEntity> open = new java.util.ArrayList<>();
                for (SmelteryTankBlockEntity tank : layer) {
                    if (in ? tank.lava < CAPACITY : tank.lava > 0) open.add(tank);
                }
                if (open.isEmpty()) break;
                int share = Math.max(1, left / open.size());
                for (SmelteryTankBlockEntity tank : open) {
                    if (left <= 0) break;
                    int step = in ? tank.fill(Math.min(share, left)) : tank.drain(Math.min(share, left));
                    left -= step;
                    moved += step;
                }
            }
            if (moved >= amount) break;
        }
        return moved;
    }

    private void set(int amount) {
        lava = amount;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lava = Math.clamp(input.getIntOr("lava", 0), 0, CAPACITY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("lava", lava);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
