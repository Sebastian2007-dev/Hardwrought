package de.ipnats.hardwrought.chemistry;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.smithing.ForgeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * The still's pot and what is waiting around it: the charge, the empty bottles, the products and the
 * container the charge came in. It works only over a fire (see {@link #heated}) and only when there is
 * room for everything a batch can give, so a batch never spills.
 *
 * <p>Hoppers work it like a furnace: charge in from the top, bottles from the sides, products and
 * empty containers out of the bottom.
 */
public class StillBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int CHARGE = 0;
    public static final int BOTTLES = 1;
    public static final int FIRST_OUTPUT = 2;
    public static final int OUTPUTS = 6;
    public static final int RETURNED = FIRST_OUTPUT + OUTPUTS;
    public static final int SIZE = RETURNED + 1;
    public static final int DATA_COUNT = 2;

    private static final int[] TOP = {CHARGE};
    private static final int[] SIDE = {BOTTLES};
    private static final int[] BOTTOM;

    static {
        BOTTOM = new int[OUTPUTS + 1];
        for (int i = 0; i < OUTPUTS; i++) BOTTOM[i] = FIRST_OUTPUT + i;
        BOTTOM[OUTPUTS] = RETURNED;
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int progress;
    private boolean heated;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? progress : heated ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) progress = value;
            else heated = value != 0;
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public StillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STILL, pos, state);
    }

    /**
     * Whatever burns right under the pot heats it: a lit furnace of any kind, a campfire, a burning
     * forge, open fire, lava or magma.
     */
    public static boolean heated(Level level, BlockPos still) {
        BlockState below = level.getBlockState(still.below());
        if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.LAVA) || below.is(Blocks.MAGMA_BLOCK)) {
            return true;
        }
        if (below.getBlock() instanceof AbstractFurnaceBlock || below.getBlock() instanceof CampfireBlock) {
            return below.getOptionalValue(BlockStateProperties.LIT).orElse(false);
        }
        return below.getBlock() instanceof ForgeBlock && below.getValue(ForgeBlock.LIT);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StillBlockEntity still) {
        still.heated = heated(level, pos);
        boolean working = still.heated && still.canStart();
        if (working) {
            still.progress++;
            if (still.progress >= StillRecipes.BATCH_TICKS) {
                still.finish(level, pos);
                still.progress = 0;
            }
            still.setChanged();
        } else if (still.progress != 0 && !still.canStart()) {
            still.progress = 0;
            still.setChanged();
        }
        if (state.getValue(StillBlock.RUNNING) != working) {
            level.setBlock(pos, state.setValue(StillBlock.RUNNING, working), Block.UPDATE_CLIENTS);
        }
    }

    private boolean canStart() {
        ItemStack charge = items.get(CHARGE);
        if (!StillRecipes.accepts(charge)) return false;
        int bottles = StillRecipes.bottlesFor(charge);
        if (bottles > 0 && (!items.get(BOTTLES).is(Items.GLASS_BOTTLE) || items.get(BOTTLES).getCount() < bottles)) {
            return false;
        }
        if (!roomFor(StillRecipes.mostOf(charge))) return false;
        ItemStack returned = items.get(RETURNED);
        return returned.isEmpty() || (returned.is(Items.BUCKET) && returned.getCount() < returned.getMaxStackSize())
                || charge.is(de.ipnats.hardwrought.core.registry.ModItems.RAW_SULFUR);
    }

    /** Whether every one of these would fit into the output places at once. */
    private boolean roomFor(List<ItemStack> products) {
        List<ItemStack> outputs = new ArrayList<>();
        for (int i = 0; i < OUTPUTS; i++) outputs.add(items.get(FIRST_OUTPUT + i).copy());
        for (ItemStack product : products) {
            if (!insert(outputs, product.copy())) return false;
        }
        return true;
    }

    private static boolean insert(List<ItemStack> outputs, ItemStack product) {
        for (ItemStack there : outputs) {
            if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, product)
                    && there.getCount() + product.getCount() <= there.getMaxStackSize()) {
                there.grow(product.getCount());
                return true;
            }
        }
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i).isEmpty()) {
                outputs.set(i, product);
                return true;
            }
        }
        return false;
    }

    private void finish(Level level, BlockPos pos) {
        ItemStack charge = items.get(CHARGE);
        StillRecipes.Batch batch = StillRecipes.batch(charge, level.getRandom());
        if (batch == null) return;
        charge.shrink(1);
        if (batch.bottles() > 0) items.get(BOTTLES).shrink(batch.bottles());
        List<ItemStack> outputs = new ArrayList<>();
        for (int i = 0; i < OUTPUTS; i++) outputs.add(items.get(FIRST_OUTPUT + i));
        for (ItemStack product : batch.products()) insert(outputs, product.copy());
        for (int i = 0; i < OUTPUTS; i++) items.set(FIRST_OUTPUT + i, outputs.get(i));
        if (!batch.returned().isEmpty()) {
            ItemStack returned = items.get(RETURNED);
            if (returned.isEmpty()) items.set(RETURNED, batch.returned().copy());
            else returned.grow(1);
        }
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 0.8f);
    }

    public int progress() {
        return progress;
    }

    public boolean isHeated() {
        return heated;
    }

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.hardwrought.still");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new StillMenu(id, inventory, this, data);
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("progress", progress);
    }

    // ---------------------------------------------------------------- container

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
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
        if (slot == CHARGE) return StillRecipes.accepts(stack);
        if (slot == BOTTLES) return stack.is(Items.GLASS_BOTTLE);
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= FIRST_OUTPUT;
    }
}
