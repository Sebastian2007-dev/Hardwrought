package de.ipnats.hardwrought.steam;

import de.ipnats.hardwrought.power.Exhaust;
import de.ipnats.hardwrought.smithing.ForgeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The firebox under a boiler: an iron box lined with refractory brick, with a grate for coal, charcoal,
 * coke or logs and a door to feed it by. Hoppers feed it too. Laid fuel has to be lit, with a flint and
 * steel, a fire charge or lighting sticks; once lit it keeps going as long as fuel follows.
 *
 * <p>It gives {@value Entity#WATTS} W to what stands on it. A redstone signal closes its damper: the
 * fire then only smoulders, at a fifth of its heat and of its appetite, and comes back when it opens.
 * Its smoke goes out over the boiler on it, or up a flue pipe set on that.
 */
public class FireboxBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public FireboxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** How many ticks an item burns here: coal and coke as in a forge, a log briefly. */
    public static int burnTime(ItemStack stack) {
        int forge = ForgeBlock.fuelValue(stack);
        if (forge > 0) return forge;
        return stack.is(ItemTags.LOGS_THAT_BURN) ? 300 : 0;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity box)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (ForgeBlock.lights(stack)) {
            if (state.getValue(LIT) || !box.hasFuel()) return InteractionResult.FAIL;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            box.light();
            if (stack.isDamageableItem()) stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            else if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        if (burnTime(stack) > 0) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            ItemStack held = box.items.getFirst();
            if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack)) return InteractionResult.FAIL;
            int room = stack.getMaxStackSize() - held.getCount();
            int moved = Math.min(room, stack.getCount());
            if (moved <= 0) return InteractionResult.FAIL;
            if (held.isEmpty()) box.items.set(0, stack.copyWithCount(moved));
            else held.grow(moved);
            if (!player.getAbilities().instabuild) stack.shrink(moved);
            box.setChanged();
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.5f, 0.8f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** An empty hand reads it: lit or not, the fuel, and the damper. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity box)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            ItemStack held = box.items.getFirst();
            String fire = !state.getValue(LIT) ? "out" : box.damped(level, pos) ? "damped" : "lit";
            player.sendOverlayMessage(Component.translatable("message.hardwrought.firebox." + fire,
                    held.getCount(), held.isEmpty() ? Component.translatable("message.hardwrought.firebox.no_fuel") : held.getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        Direction front = state.getValue(FACING);
        double x = pos.getX() + 0.5 + front.getStepX() * 0.52, z = pos.getZ() + 0.5 + front.getStepZ() * 0.52;
        level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.4 * Math.abs(front.getStepZ()),
                pos.getY() + 0.25, z + (random.nextDouble() - 0.5) * 0.4 * Math.abs(front.getStepX()), 0, 0, 0);
        if (random.nextInt(10) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.8f, 0.9f, false);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != SteamBlocks.FIREBOX_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::serverTick;
        return ticker;
    }

    public static class Entity extends BlockEntity implements WorldlyContainer {
        /** The heat of a good fire on the grate, in watts. */
        public static final double WATTS = 10_000;
        /** What is left of it with the damper shut, of heat and of fuel alike. */
        public static final double DAMPED = 0.2;
        private static final int[] SLOTS = {0};

        final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
        /** Ticks the fuel on the grate still burns for, in ticks of an open damper. */
        private double burning;
        private double smoke;

        public Entity(BlockPos pos, BlockState state) {
            super(SteamBlocks.FIREBOX_ENTITY, pos, state);
        }

        boolean hasFuel() {
            return burning > 0 || !items.getFirst().isEmpty();
        }

        boolean damped(Level level, BlockPos pos) {
            return level.hasNeighborSignal(pos);
        }

        /** The heat it gives now, in watts. */
        public double watts() {
            if (level == null || !getBlockState().getValue(LIT)) return 0;
            return damped(level, worldPosition) ? WATTS * DAMPED : WATTS;
        }

        /** Lights what lies on the grate; for tests too. */
        public void light() {
            if (level == null || !hasFuel()) return;
            if (burning <= 0) catchFuel();
            level.setBlock(worldPosition, getBlockState().setValue(LIT, true), Block.UPDATE_ALL);
            setChanged();
        }

        private boolean catchFuel() {
            ItemStack fuel = items.getFirst();
            int time = burnTime(fuel);
            if (time <= 0) return false;
            burning += time;
            fuel.shrink(1);
            setChanged();
            return true;
        }

        public static void serverTick(Level level, BlockPos pos, BlockState state, Entity box) {
            if (!state.getValue(LIT) || !(level instanceof ServerLevel server)) return;
            boolean damped = box.damped(level, pos);
            box.burning -= damped ? DAMPED : 1.0;
            if (box.burning <= 0 && !box.catchFuel()) {
                box.burning = 0;
                level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
                box.setChanged();
                return;
            }
            // A unit of carbon dioxide for every ten seconds of a full fire, out over the boiler on it.
            box.smoke += damped ? DAMPED : 1.0;
            if (box.smoke >= 200) {
                box.smoke -= 200;
                BlockPos above = pos.above();
                Exhaust.emit(server, level.getBlockState(above).getBlock() instanceof BoilerBlock ? above : pos, 1, 0);
            }
            box.setChanged();
        }

        public void setFuelForTesting(ItemStack stack) {
            items.set(0, stack);
            setChanged();
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            items.set(0, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(input, items);
            burning = input.getDoubleOr("burning", 0);
            smoke = input.getDoubleOr("smoke", 0);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            ContainerHelper.saveAllItems(output, items);
            output.putDouble("burning", burning);
            output.putDouble("smoke", smoke);
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return items.getFirst().isEmpty();
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
            return burnTime(stack) > 0;
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(this, player);
        }

        @Override
        public void clearContent() {
            items.set(0, ItemStack.EMPTY);
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return SLOTS;
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
            return canPlaceItem(slot, stack);
        }

        /** Nothing comes out of a fire. */
        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return false;
        }
    }
}
