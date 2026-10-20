package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.carried.Cell;
import de.ipnats.hardwrought.carried.Cells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;

/**
 * A charging station (stage E4): five places for cells, or for devices with their cells in them, or for
 * armour with a lining. It takes up to its {@link Appliance#ratedWatts} from the line and shares them out,
 * each cell taking no more than it can ({@link Cell#chargeWatts}), and gets four fifths of it into them.
 * Full or empty of cells, it takes nothing.
 */
public class ChargerBlockEntity extends ApplianceBlockEntity implements MenuProvider {
    public static final int SLOTS = 5;

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(ChargerBlockEntity.this, player);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ChargerBlockEntity.this.setChanged();
        }
    };
    private double charging;

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.CHARGER_ENTITY, pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    /** Whether anything in it can take more charge. */
    private boolean hungry() {
        for (ItemStack stack : items.getItems()) {
            ItemStack cell = Cells.cellOf(stack);
            if (!cell.isEmpty() && Cells.charge(cell) < Cells.capacity(cell) - 1.0) return true;
        }
        return false;
    }

    @Override
    protected boolean wants() {
        return hungry();
    }

    @Override
    protected void work(ServerLevel level, double power) {
        charging = 0.0;
        if (power <= 0.0) return;
        double budget = power * appliance().ratedWatts() * (WORK_TICKS / 20.0);
        int hungry = 0;
        for (ItemStack stack : items.getItems()) {
            ItemStack cell = Cells.cellOf(stack);
            if (!cell.isEmpty() && Cells.charge(cell) < Cells.capacity(cell) - 1.0) hungry++;
        }
        if (hungry == 0) return;
        for (ItemStack stack : items.getItems()) {
            ItemStack cell = Cells.cellOf(stack);
            Cell kind = Cells.kind(cell);
            if (kind == null) continue;
            double share = Math.min(budget / hungry, kind.chargeWatts() * (WORK_TICKS / 20.0));
            charging += Cells.fill(stack, share * Cells.CHARGE_EFFICIENCY);
        }
        items.setChanged();
    }

    @Override
    protected Component state() {
        return Component.translatable("message.hardwrought.meter.charger", String.format(Locale.ROOT, "%.0f", charging / (WORK_TICKS / 20.0)));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HopperMenu(id, inventory, items);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, list);
        for (int slot = 0; slot < SLOTS; slot++) items.setItem(slot, list.get(slot));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOTS; slot++) list.set(slot, items.getItem(slot));
        ContainerHelper.saveAllItems(output, list);
    }
}
