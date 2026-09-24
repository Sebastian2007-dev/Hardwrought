package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.metallurgy.Crushing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The crusher's contents: raw ore waiting to be broken, powder that has been, and how far the piece
 * in the jaws has got.
 *
 * <p>A container with two slots and no screen. Hoppers feed ore in from the top and sides and draw
 * powder out of the bottom; a player does the same by hand. Breaking the block drops both, which
 * the game does for any block entity that is a container.
 */
public class StarterCrusherBlockEntity extends BlockEntity implements WorldlyContainer, KineticHolder {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    /** Ticks of turning to break one chunk at {@link #RATED_SPEED}. Four seconds on a hand crank. */
    public static final int CRUSH_TICKS = 80;
    /** The speed the jaws are built for; driven faster they crush faster, slower slower. */
    public static final float RATED_SPEED = 16.0f;
    /** Strength taken per turn per minute: a hand crank at 16 turns carries one crusher, not two. */
    public static final float IMPACT = 8.0f;

    private static final int[] INPUT_SLOTS = { INPUT };
    private static final int[] OUTPUT_SLOTS = { OUTPUT };

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private float progress;
    private float speed;

    public StarterCrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STARTER_CRUSHER, pos, state);
    }

    /**
     * One tick. It works only while something turns into it and only while there is somewhere for
     * the powder to go. A crusher that loses its power keeps its progress: the piece stays half
     * broken in the jaws until the handle turns again.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, StarterCrusherBlockEntity crusher) {
        boolean working = crusher.canCrush() && crusher.speed != 0.0f;
        if (working) {
            int before = (int) crusher.progress;
            crusher.progress += Math.abs(crusher.speed) / RATED_SPEED;
            if (crusher.progress >= CRUSH_TICKS) {
                crusher.crushOne();
                crusher.progress = 0;
            }
            if (before / 20 != (int) crusher.progress / 20) {
                level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.4f,
                        0.6f + level.getRandom().nextFloat() * 0.2f);
            }
            crusher.setChanged();
        } else if (crusher.items.get(INPUT).isEmpty() && crusher.progress != 0) {
            crusher.progress = 0;
            crusher.setChanged();
        }
        if (state.getValue(StarterCrusherBlock.RUNNING) != working) {
            level.setBlock(pos, state.setValue(StarterCrusherBlock.RUNNING, working), Block.UPDATE_CLIENTS);
        }
    }

    private boolean canCrush() {
        Item result = Crushing.result(items.get(INPUT));
        if (result == null) return false;
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || (output.is(result) && output.getCount() < output.getMaxStackSize());
    }

    private void crushOne() {
        Item result = Crushing.result(items.get(INPUT));
        items.get(INPUT).shrink(1);
        ItemStack output = items.get(OUTPUT);
        if (output.isEmpty()) {
            items.set(OUTPUT, new ItemStack(result));
        } else {
            output.grow(1);
        }
    }

    /**
     * Moves as much of the held stack into the input as fits. Returns false when nothing moved,
     * because it is not ore or because the slot is full of something else.
     */
    public boolean insertFrom(ItemStack held, boolean consume) {
        if (!Crushing.isCrushable(held)) return false;
        ItemStack input = items.get(INPUT);
        int room;
        if (input.isEmpty()) {
            room = held.getMaxStackSize();
        } else if (ItemStack.isSameItemSameComponents(input, held)) {
            room = input.getMaxStackSize() - input.getCount();
        } else {
            room = 0;
        }
        int moved = Math.min(room, held.getCount());
        if (moved <= 0) return false;
        if (input.isEmpty()) {
            items.set(INPUT, held.copyWithCount(moved));
        } else {
            input.grow(moved);
        }
        if (consume) held.shrink(moved);
        setChanged();
        return true;
    }

    /** Hands out the powder, or the ore when there is no powder yet. Empty when there is neither. */
    public ItemStack takeOut() {
        int slot = !items.get(OUTPUT).isEmpty() ? OUTPUT : INPUT;
        ItemStack taken = removeItemNoUpdate(slot);
        if (slot == INPUT) progress = 0;
        return taken;
    }

    public int progress() {
        return (int) progress;
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        progress = input.getFloatOr("progress", input.getIntOr("progress", 0));
        speed = input.getFloatOr("speed", 0.0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putFloat("progress", progress);
        output.putFloat("speed", speed);
    }

    // ---------------------------------------------------------------- container

    @Override
    public float kineticSpeed() {
        return speed;
    }

    @Override
    public void setKineticSpeed(float speed) {
        this.speed = speed;
        setChanged();
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && Crushing.isCrushable(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /** Ore goes in from the top and the sides, powder comes out of the bottom. */
    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT;
    }
}
