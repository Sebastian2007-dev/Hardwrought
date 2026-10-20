package de.ipnats.hardwrought.haven;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Map;

/**
 * An ice box: an insulated chest with a block of ice in it, and the step before the refrigerator. While
 * there is ice in it, food in it goes off at {@link #SPEED} of its speed. The ice melts: a block of ice
 * lasts {@link #ICE_TICKS}, packed ice four times as long and blue ice sixteen; when the last is gone it
 * is a chest like any other.
 */
public class IceBoxBlockEntity extends BlockEntity implements MenuProvider {
    public static final double SPEED = 0.5;
    public static final long ICE_TICKS = 12000;
    private static final Map<Item, Long> LASTS = Map.of(Items.ICE, ICE_TICKS, Items.PACKED_ICE, ICE_TICKS * 4,
            Items.BLUE_ICE, ICE_TICKS * 16, Items.SNOW_BLOCK, ICE_TICKS / 2);

    private final Larder larder = new Larder(this);
    /** How much of the ice now melting is still left, in ticks. */
    private long melting;

    public IceBoxBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.ICE_BOX_ENTITY, pos, state);
    }

    public Larder larder() {
        return larder;
    }

    /** Whether there is ice in it now. */
    public boolean cold() {
        if (melting > 0) return true;
        for (ItemStack stack : larder.items().getItems()) if (LASTS.containsKey(stack.getItem())) return true;
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IceBoxBlockEntity box) {
        if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.asLong()) % 20 != 0) return;
        box.melt(20);
        box.larder.keep(server.getGameTime(), box.cold() ? SPEED : 1.0);
    }

    /** The ice melts by this many ticks, a new block taken when the last is gone. */
    private void melt(long ticks) {
        melting -= ticks;
        if (melting > 0) return;
        melting = 0;
        for (int slot = 0; slot < Larder.SLOTS; slot++) {
            ItemStack stack = larder.items().getItem(slot);
            Long lasts = LASTS.get(stack.getItem());
            if (lasts == null) continue;
            stack.shrink(1);
            melting = lasts;
            larder.items().setChanged();
            return;
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return larder.menu(id, inventory);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, larder.items());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        larder.load(input);
        melting = input.getLongOr("melting", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        larder.save(output);
        output.putLong("melting", melting);
    }
}
