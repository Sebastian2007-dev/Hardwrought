package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The forge — the smith's hearth. Eight refractory bricks in hand line it, once; a flint and steel,
 * a fire charge or lighting sticks light the coal in it; anything else opens it, with its fuel, the
 * pieces lying in it and how hot it is. A bare hand that takes a glowing piece out still gets burnt
 * for it, and so do feet that stand in the fire.
 */
public class ForgeBlock extends Block implements EntityBlock {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final BooleanProperty LINED = BooleanProperty.create("lined");
    /**
     * Where this block sits in a larger forge, so that together the blocks draw one big hearth.
     * 0 is a forge on its own; see {@link ForgeMultiblock#partIndex}.
     */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, ForgeMultiblock.MAX_PART);
    /** How full the pit is with coal, 0 for an empty hearth; see {@link ForgeBlockEntity#fill}. */
    public static final IntegerProperty FUEL = IntegerProperty.create("fuel", 0, 4);
    public static final int LINING_BRICKS = 8;

    public ForgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(LINED, false).setValue(PART, 0)
                .setValue(FUEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, LINED, PART, FUEL);
    }

    /** Ticks of fire one item of fuel is worth, or 0 where it is not fuel for a forge. */
    public static int fuelValue(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 1600;
        if (stack.is(Items.COAL_BLOCK)) return 16000;
        return 0;
    }

    /** Whether this lights a forge: a flint and steel, a fire charge, or the lighting sticks. */
    public static boolean lights(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(ModItems.LIGHTING_STICKS);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (lights(stack)) return light(stack, level, pos, player, hand);
        if (!stack.is(ModItems.REFRACTORY_BRICK)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof ForgeBlockEntity)) return InteractionResult.PASS;
        ForgeMultiblock.Structure structure = ForgeMultiblock.getOrForm(level, pos);
        boolean lined = structure == null ? state.getValue(LINED) : ForgeMultiblock.isLined(level, structure);
        if (lined) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        boolean keep = player.getAbilities().instabuild;
        if (!keep && stack.getCount() < LINING_BRICKS) {
            if (player instanceof ServerPlayer worker) {
                worker.sendSystemMessage(Component.translatable("message.hardwrought.forge_lining_needs",
                        LINING_BRICKS), true);
            }
            return InteractionResult.FAIL;
        }
        if (structure == null) level.setBlockAndUpdate(pos, state.setValue(LINED, true));
        else ForgeMultiblock.line((ServerLevel) level, structure);
        if (!keep) stack.shrink(LINING_BRICKS);
        level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Sets the coal in the pit alight. Coal laid in a forge does not catch by itself; once lit, the
     * fire takes the next coal as each one burns down, and goes out when there is none left.
     */
    private InteractionResult light(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof ForgeBlockEntity forge)) return InteractionResult.PASS;
        if (forge.burnTicks() > 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ForgeMultiblock.getOrForm(level, pos);
        if (!forge.working().ignite()) {
            if (player instanceof ServerPlayer worker) {
                worker.sendSystemMessage(Component.translatable("message.hardwrought.forge_needs_fuel"), true);
            }
            return InteractionResult.FAIL;
        }
        if (stack.is(Items.FIRE_CHARGE)) {
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        } else {
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f,
                    level.getRandom().nextFloat() * 0.4f + 0.8f);
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
                    ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        }
        return InteractionResult.SUCCESS;
    }

    /** Standing in a burning hearth burns, carefully or not. */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.Entity entity) {
        if (state.getValue(LIT) && !entity.fireImmune() && level instanceof ServerLevel server) {
            entity.hurtServer(server, level.damageSources().hotFloor(), 1.0f);
        }
        super.stepOn(level, pos, state, entity);
    }

    /** Opens the forge: its fuel, its pieces, and how hot it is. Any block of a joined forge opens the whole. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ForgeBlockEntity forge)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ForgeMultiblock.Structure structure = ForgeMultiblock.getOrForm(level, pos);
        ForgeBlockEntity working = structure == null ? forge : ForgeMultiblock.controller(level, structure);
        if (working == null) return InteractionResult.PASS;
        player.openMenu(working);
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (!level.getBlockState(pos.above()).isAir()) return;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.2 + random.nextDouble() * 0.6,
                    pos.getY() + 1.02, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.01, 0.0);
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
            level.playLocalSound(pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.4f, 1.0f, false);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.FORGE) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<ForgeBlockEntity>) ForgeBlockEntity::serverTick;
        return ticker;
    }
}
