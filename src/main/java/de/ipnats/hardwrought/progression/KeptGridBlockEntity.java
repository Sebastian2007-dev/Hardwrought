package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.UUID;

/**
 * The crafting grid of a bench that was built rather than cut: what was left lying on it stays
 * there for the next time.
 *
 * <p>Deliberately not a {@link net.minecraft.world.Container}. A hopper would treat one as an
 * inventory and start feeding the grid, which turns a workbench into an autocrafter by accident.
 * The contents are only ever reached through the bench's own menu, and dropped when it is broken.
 *
 * <p>One worker at a time. Two players at the same grid would each hold their own copy of it in
 * their open menu, and whichever closed last would decide what was on the bench — which is how an
 * item gets duplicated. {@link #claim} is how a menu takes the bench, {@link #release} how it gives
 * it back.
 */
public class KeptGridBlockEntity extends BlockEntity {
    public static final int SIZE = 9;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    /** Who has the grid open right now. Not saved: nobody has anything open across a restart. */
    private UUID worker;

    public KeptGridBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KEPT_GRID, pos, state);
    }

    /** A copy of what lies on the bench, slot by slot. */
    public List<ItemStack> contents() {
        return items.stream().map(ItemStack::copy).toList();
    }

    /** Replaces what lies on the bench with the grid as it now stands. */
    public void store(List<ItemStack> grid) {
        for (int slot = 0; slot < SIZE; slot++) {
            items.set(slot, slot < grid.size() ? grid.get(slot).copy() : ItemStack.EMPTY);
        }
        setChanged();
    }

    /**
     * Takes the bench for this player. Refused while somebody else is really still working at it;
     * a claim left behind by a player who has since closed the menu or left is simply taken over.
     */
    public boolean claim(ServerPlayer player) {
        if (worker != null && !worker.equals(player.getUUID()) && stillWorking(player)) return false;
        worker = player.getUUID();
        return true;
    }

    public void release(Player player) {
        if (player.getUUID().equals(worker)) worker = null;
    }

    private boolean stillWorking(ServerPlayer asking) {
        if (asking.level().getServer() == null) return false;
        ServerPlayer other = asking.level().getServer().getPlayerList().getPlayer(worker);
        return other != null && other.containerMenu instanceof KeptCraftingMenu menu && menu.isAt(this);
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
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }
}
