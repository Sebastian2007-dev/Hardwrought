package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Map;

/**
 * What hangs on a drying rack, and how far each piece has dried.
 *
 * <p>Drying needs the sun: daylight, open sky above the rack, and no rain. Only then does it go on,
 * so a fibre hung up in the evening is still green in the morning. {@link #DRY_TICKS} of good drying
 * is half a day — hung up in the morning, twisted into cord by the afternoon.
 */
public class DryingRackBlockEntity extends BlockEntity {
    public static final int SLOTS = 4;
    /** Ticks of sun, open sky and dry weather one piece needs. Half of a Minecraft day. */
    public static final int DRY_TICKS = 6000;
    private static final int STEP = 20;

    /** What each thing hung up becomes once it is dry. */
    private static final Map<Item, Item> DRIES_INTO = Map.of(ModItems.GREEN_FIBRE, ModItems.LEAF_STRING);

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final int[] progress = new int[SLOTS];

    public DryingRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRYING_RACK, pos, state);
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    public static boolean dries(ItemStack stack) {
        return DRIES_INTO.containsKey(stack.getItem());
    }

    /**
     * Whether the weather and the hour dry anything here right now: daylight, nothing overhead — a
     * roof or a tree's crown both count — and no rain.
     */
    public static boolean drying(Level level, BlockPos pos) {
        if (!level.isBrightOutside() || level.isRainingAt(pos.above())) return false;
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        return top <= pos.getY() + 1;
    }

    /** Hangs one piece up. False when it does not dry or the rack is full. */
    public boolean hang(ItemStack stack) {
        if (!dries(stack)) return false;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items.get(slot).isEmpty()) {
                items.set(slot, stack.copyWithCount(1));
                progress[slot] = 0;
                changed();
                return true;
            }
        }
        return false;
    }

    /** Takes one piece down: a dry one if there is one, otherwise the last hung up. */
    public ItemStack takeDown() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!items.get(slot).isEmpty() && !dries(items.get(slot))) return take(slot);
        }
        for (int slot = SLOTS - 1; slot >= 0; slot--) {
            if (!items.get(slot).isEmpty()) return take(slot);
        }
        return ItemStack.EMPTY;
    }

    private ItemStack take(int slot) {
        ItemStack taken = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        progress[slot] = 0;
        changed();
        return taken;
    }

    /** Lets every piece dry this many ticks more, turning those that are done. For tests too. */
    public void dry(int ticks) {
        boolean turned = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack piece = items.get(slot);
            Item dry = DRIES_INTO.get(piece.getItem());
            if (dry == null) continue;
            progress[slot] += ticks;
            if (progress[slot] >= DRY_TICKS) {
                items.set(slot, new ItemStack(dry));
                progress[slot] = 0;
                turned = true;
            }
        }
        if (turned) changed();
        else setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DryingRackBlockEntity rack) {
        if ((level.getGameTime() + pos.asLong()) % STEP != 0) return;
        if (drying(level, pos)) rack.dry(STEP);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, items);
        items.clear();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        for (int slot = 0; slot < SLOTS; slot++) progress[slot] = input.getIntOr("progress_" + slot, 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items, true);
        for (int slot = 0; slot < SLOTS; slot++) output.putInt("progress_" + slot, progress[slot]);
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
