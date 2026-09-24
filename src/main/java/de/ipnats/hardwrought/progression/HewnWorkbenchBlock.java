package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A workbench hewn out of a standing log rather than joined from boards.
 *
 * <p>It is a crafting table in every way that matters — the same three-by-three grid opens on it —
 * and it looks like what it is: the lower half is still the log it was cut from, the upper half is
 * the worked surface. Section 71 asks for the physical interaction with the world to be believable,
 * and a bench that grows out of the tree it was cut from says more about a first workshop than any
 * recipe could.
 *
 * <p>The wood travels with the block, because a birch that turns into an oak stump when it is worked
 * is exactly the detail that makes a block look pasted into the world.
 */
public class HewnWorkbenchBlock extends CraftingTableBlock {
    public static final EnumProperty<HewnWood> WOOD = EnumProperty.create("wood", HewnWood.class);

    public HewnWorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WOOD, HewnWood.OAK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WOOD);
    }

    /** Creates an inventory stack whose model and placed state keep the selected timber. */
    public ItemStack itemStack(HewnWood wood) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(WOOD, wood));
        return stack;
    }

    /**
     * A hammer, with bronze nails in the bag, drives them into this bench one blow at a time until
     * it is the nailed one — see {@link NailDriving}. Nails in the hand alone only earn a reminder
     * that they want driving. Anything else falls through to the empty-hand use, which opens the grid.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (de.ipnats.hardwrought.smithing.Hammers.isHammer(stack)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player instanceof ServerPlayer worker && level instanceof ServerLevel server) {
                NailDriving.strike(worker, server, pos, state, hand);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModItems.BRONZE_NAILS)) {
            if (!level.isClientSide() && player instanceof ServerPlayer worker) {
                worker.sendSystemMessage(Component.translatable("message.hardwrought.nails_need_hammer"), true);
            }
            return InteractionResult.FAIL;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /**
     * Rebuilds the bench as the nailed one, in the same wood, facing whoever did the work. Costs one
     * handful of nails, which a player in creative keeps. Called once the last blow has landed;
     * public for the tests.
     */
    public static boolean nail(Level level, BlockPos pos, BlockState state, Player player, ItemStack nails) {
        if (!(state.getBlock() instanceof HewnWorkbenchBlock) || !nails.is(ModItems.BRONZE_NAILS)) {
            return false;
        }
        boolean creative = player != null && player.getAbilities().instabuild;
        Direction facing = player == null ? Direction.NORTH : player.getDirection().getOpposite();
        level.setBlockAndUpdate(pos, ModBlocks.NAILED_WORKBENCH.defaultBlockState()
                .setValue(NailedWorkbenchBlock.WOOD, state.getValue(WOOD))
                .setValue(NailedWorkbenchBlock.FACING, facing));
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.4f, 1.6f);
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 0.9f);
        if (!creative) nails.shrink(1);
        return true;
    }

    /** Creative pick-block must keep the same wood instead of silently returning the oak default. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
                                          boolean includeData) {
        return itemStack(state.getValue(WOOD));
    }
}
