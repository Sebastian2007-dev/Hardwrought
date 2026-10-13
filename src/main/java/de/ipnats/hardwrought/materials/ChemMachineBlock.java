package de.ipnats.hardwrought.materials;

import de.ipnats.hardwrought.electricity.ElectricNodeBlock;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A chemistry machine's block: joined to a line through a copper coil set against it, filled by hoppers,
 * pipes and gas lines, and opened with an empty hand. See {@link ChemMachineBlockEntity}.
 */
public class ChemMachineBlock extends ElectricNodeBlock {
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    private final ChemMachine machine;

    public ChemMachineBlock(ChemMachine machine, Properties properties) {
        super(properties);
        this.machine = machine;
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    public ChemMachine machine() {
        return machine;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 15.0 / 16.0, 0.5);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() && !stack.is(Items.SHEARS) && level.getBlockEntity(pos) instanceof ChemMachineBlockEntity entity
                && entity.fluid() != null && FluidStorageUtil.interactWithFluidStorage(entity.fluid(), player, hand)) {
            return InteractionResult.SUCCESS;
        }
        InteractionResult wires = super.useItemOn(stack, state, level, pos, player, hand, hit);
        return wires == InteractionResult.PASS ? InteractionResult.TRY_WITH_EMPTY_HAND : wires;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ChemMachineBlockEntity entity)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.openMenu(entity);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 1.02, z = pos.getZ() + 0.5;
        switch (machine) {
            case ARC_FURNACE -> {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (random.nextDouble() - 0.5) * 0.4, y,
                        z + (random.nextDouble() - 0.5) * 0.4, 0, 0.1, 0);
                level.addParticle(ParticleTypes.SMOKE, x, y + 0.2, z, 0, 0.03, 0);
            }
            case ELECTROLYZER -> level.addParticle(ParticleTypes.BUBBLE_POP, x + (random.nextDouble() - 0.5) * 0.6, y - 0.1,
                    z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            case CHEMICAL_REACTOR -> level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0, 0.03, 0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChemMachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != MaterialBlocks.CHEM_MACHINE_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<ChemMachineBlockEntity>) ChemMachineBlockEntity::serverTick;
        return ticker;
    }
}
