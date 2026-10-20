package de.ipnats.hardwrought.haven;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A refrigerator or a freezer: a cabinet of insulated panels kept cold by a compressor. While it has
 * current and food in it, food goes off at {@link #FRIDGE_SPEED} of its speed in a refrigerator and not
 * at all in a freezer; without current it is a cupboard, and food in it goes off as anywhere else. Empty
 * of food, it takes nothing.
 */
public class ColdStoreBlockEntity extends ApplianceBlockEntity implements MenuProvider {
    public static final double FRIDGE_SPEED = 0.25, FREEZER_SPEED = 0.0;

    private final Larder larder = new Larder(this);

    public ColdStoreBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.COLD_STORE_ENTITY, pos, state);
    }

    public Larder larder() {
        return larder;
    }

    /** How fast food goes off in it while it is cold. */
    public double speed() {
        return appliance() == Appliance.FREEZER ? FREEZER_SPEED : FRIDGE_SPEED;
    }

    @Override
    protected boolean wants() {
        return larder.holdsFood();
    }

    @Override
    protected void work(ServerLevel level, double power) {
        // Half the power is not half as cold: it either holds its cold or it does not.
        larder.keep(level.getGameTime(), power >= 0.5 ? speed() : 1.0);
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
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        larder.save(output);
    }
}
