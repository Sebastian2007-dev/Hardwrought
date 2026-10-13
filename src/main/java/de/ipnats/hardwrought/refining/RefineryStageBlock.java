package de.ipnats.hardwrought.refining;

import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One tray of a refinery column, stacked on a {@link RefineryBaseBlock} and on each other, up to
 * {@value RefineryBaseBlock#MOST_STAGES}. Which cut it catches follows from how high it stands: the first
 * over the base catches heavy oil, then diesel, kerosene and naphtha; the fifth catches the gas, which goes
 * out into a gas line by its own pressure. Fluid pipes draw a tray's cut off it.
 */
public class RefineryStageBlock extends Block implements EntityBlock {
    public RefineryStageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity entity)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.openMenu(entity);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    /** Which tray of its column the stage at pos is, from 1; 0 if it stands on no base. */
    static int height(Level level, BlockPos pos) {
        for (int below = 1; below <= RefineryBaseBlock.MOST_STAGES; below++) {
            BlockState state = level.getBlockState(pos.below(below));
            if (state.getBlock() instanceof RefineryBaseBlock) return below;
            if (!(state.getBlock() instanceof RefineryStageBlock)) return 0;
        }
        return 0;
    }

    public static class Entity extends BlockEntity implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<BlockPos> {
        static final int BUCKETS = 4;

        /** One outlet for each liquid cut; only the one of the tray's height is ever filled. */
        private final ProcessTank[] liquids = new ProcessTank[RefineryBaseBlock.CUTS.size() - 1];
        public final GasStore gas = new GasStore(128, 8, false, this::setChanged);

        public Entity(BlockPos pos, BlockState state) {
            super(RefineryBlocks.STAGE_ENTITY, pos, state);
            for (int i = 0; i < liquids.length; i++) {
                liquids[i] = ProcessTank.outlet(BUCKETS, RefineryBaseBlock.CUTS.get(i).fluid(), this::setChanged);
            }
        }

        /** The outlet of a liquid cut by its tray number, 1 to 4. */
        ProcessTank liquid(int tray) {
            return liquids[tray - 1];
        }

        /** What pipes draw from this tray: whichever cut it holds. */
        public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> outlet() {
            return new net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage<>(java.util.List.of(liquids));
        }

        /** What the window shows: which tray this is, the state of the column, and the cut it holds. */
        RefineryMenu.Panel panel() {
            int tray = height(level, worldPosition);
            boolean heated = false, running = false;
            int progress = 0;
            if (tray > 0 && level.getBlockEntity(worldPosition.below(tray)) instanceof RefineryBaseBlock.Entity base) {
                heated = de.ipnats.hardwrought.chemistry.StillBlockEntity.heated(level, base.getBlockPos());
                running = base.getBlockState().getValue(RefineryBaseBlock.RUNNING);
                progress = base.progressPerMille();
            }
            RefineryMenu.Panel panel = new RefineryMenu.Panel(RefineryMenu.KIND_STAGE, heated, running, progress, tray);
            if (tray == RefineryBaseBlock.MOST_STAGES) {
                panel.gauge(RefineryMenu.substance(GasKind.LPG), (long) gas.amount(), (long) gas.capacity());
            } else if (tray > 0) {
                panel.liquid(RefineryBaseBlock.CUTS.get(tray - 1).fluid(), liquid(tray), BUCKETS * 1000);
            }
            return panel;
        }

        @Override
        public Component getDisplayName() {
            return getBlockState().getBlock().getName();
        }

        @Override
        public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
            return new RefineryMenu(id, RefineryMenu.data(this::panel), worldPosition);
        }

        @Override
        public BlockPos getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
            return worldPosition;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            for (int i = 0; i < liquids.length; i++) liquids[i].save(output, "cut_" + i);
            gas.save(output, "gas");
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            for (int i = 0; i < liquids.length; i++) liquids[i].load(input, "cut_" + i);
            gas.load(input, "gas");
        }
    }

    /** The cut a tray catches, with how much of each 100 mB of crude oil it is. */
    public record Cut(String name, Fluid fluid, int millibuckets, double gasUnits) { }
}
