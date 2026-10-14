package de.ipnats.hardwrought.chemistry;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.water.BucketWater;
import de.ipnats.hardwrought.water.WaterEvents;
import de.ipnats.hardwrought.water.WaterQuality;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A shallow iron pan seawater is left to stand in until only its salt is left. See {@link SaltPan}.
 *
 * <p>A bucket of seawater, or seawater by pipe, goes in; fresh water is turned away, since it leaves
 * nothing worth the waiting. Under an open sky on a dry day the sun takes off a bucket in
 * {@value Entity#SUN_TICKS} ticks; a fire under the pan, the same fires a still sits on, a bucket in
 * {@value Entity#FIRE_TICKS}; both together add up. Every bucket gone leaves
 * {@value Entity#SALT_PER_BUCKET} salt in the pan, up to a stack: taken out with an empty hand, or by a
 * hopper underneath.
 */
public class SaltPanBlock extends Block implements EntityBlock {
    /** What the pan shows: nothing, standing seawater, or a crust of salt. */
    public enum Contents implements StringRepresentable {
        EMPTY("empty"), BRINE("brine"), SALT("salt");

        private final String name;

        Contents(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Contents> CONTENTS = EnumProperty.create("contents", Contents.class);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 5, 16);

    public SaltPanBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CONTENTS, Contents.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONTENTS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!BucketWater.isBucket(stack) || BucketWater.held(stack) <= 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof Entity pan)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (WaterEvents.qualityOf(stack) != WaterQuality.SALT) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.salt_pan.fresh"));
            return InteractionResult.FAIL;
        }
        int held = BucketWater.held(stack);
        long room = (Entity.CAPACITY - pan.brine.amount) * 1000 / FluidConstants.BUCKET;
        int poured = (int) Math.min(held, room);
        if (poured <= 0) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.salt_pan.full"));
            return InteractionResult.FAIL;
        }
        pan.brine.variant = SaltPan.water(WaterQuality.SALT);
        pan.brine.amount += poured * (FluidConstants.BUCKET / 1000);
        pan.changed();
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, BucketWater.with(stack, held - poured, WaterQuality.SALT));
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity pan)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (pan.saltCount > 0) {
            ItemStack salt = new ItemStack(ModItems.SALT, pan.saltCount);
            pan.saltCount = 0;
            pan.changed();
            if (!player.getInventory().add(salt)) player.spawnAtLocation((net.minecraft.server.level.ServerLevel) level, salt);
            level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        player.sendOverlayMessage(pan.describe());
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != SaltPan.ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<Entity>) Entity::evaporate;
        return ticker;
    }

    public static class Entity extends BlockEntity {
        /** Four buckets of seawater fit in the pan. */
        public static final long CAPACITY = 4 * FluidConstants.BUCKET;
        /** Ticks the sun takes to dry a bucket off an open pan: two minutes. */
        public static final int SUN_TICKS = 2400;
        /** Ticks a fire under the pan takes to boil a bucket off: thirty seconds. */
        public static final int FIRE_TICKS = 600;
        /** Salt one bucket of seawater leaves. */
        public static final int SALT_PER_BUCKET = 2;
        /** The pan holds no more salt than a stack; past that the water just stands. */
        public static final int MOST_SALT = 64;

        /** The seawater standing in it; only seawater goes in, and nothing is drawn back out. */
        public final SingleVariantStorage<FluidVariant> brine = new SingleVariantStorage<>() {
            @Override
            protected FluidVariant getBlankVariant() {
                return FluidVariant.blank();
            }

            @Override
            protected long getCapacity(FluidVariant variant) {
                return CAPACITY;
            }

            @Override
            protected boolean canInsert(FluidVariant variant) {
                return SaltPan.quality(variant) == WaterQuality.SALT;
            }

            @Override
            protected boolean canExtract(FluidVariant variant) {
                return false;
            }

            @Override
            protected void onFinalCommit() {
                changed();
            }
        };

        /** The salt left behind, as a hopper underneath sees it: taken out only. */
        public final SingleStackStorage salt = new SingleStackStorage() {
            @Override
            protected ItemStack getStack() {
                return saltCount <= 0 ? ItemStack.EMPTY : new ItemStack(ModItems.SALT, saltCount);
            }

            @Override
            protected void setStack(ItemStack stack) {
                saltCount = stack.isEmpty() ? 0 : stack.getCount();
            }

            @Override
            protected boolean canInsert(ItemVariant itemVariant) {
                return false;
            }

            @Override
            protected void onFinalCommit() {
                changed();
            }
        };

        private int saltCount;
        /** Seawater gone short of a whole bucket, in droplets. */
        private long dried;

        public Entity(BlockPos pos, BlockState state) {
            super(SaltPan.ENTITY, pos, state);
        }

        public long brineMillibuckets() {
            return brine.amount * 1000 / FluidConstants.BUCKET;
        }

        public int saltCount() {
            return saltCount;
        }

        /** Whether the sun is drying the pan just now: an open sky above it, daylight, and no rain. */
        public static boolean sunlit(Level level, BlockPos pos) {
            return level.canSeeSky(pos.above()) && level.isBrightOutside() && !level.isRainingAt(pos.above());
        }

        /** How much dries off in a tick here and now, in droplets. */
        public static long rate(Level level, BlockPos pos) {
            long rate = 0;
            if (sunlit(level, pos)) rate += FluidConstants.BUCKET / SUN_TICKS;
            if (StillBlockEntity.heated(level, pos)) rate += FluidConstants.BUCKET / FIRE_TICKS;
            return rate;
        }

        public static void evaporate(Level level, BlockPos pos, BlockState state, Entity pan) {
            if (pan.brine.amount <= 0 || pan.saltCount >= MOST_SALT) return;
            long gone = Math.min(rate(level, pos), pan.brine.amount);
            if (gone <= 0) return;
            pan.brine.amount -= gone;
            if (pan.brine.amount <= 0) pan.brine.variant = FluidVariant.blank();
            pan.dried += gone;
            boolean shown = false;
            while (pan.dried >= FluidConstants.BUCKET) {
                pan.dried -= FluidConstants.BUCKET;
                pan.saltCount = Math.min(MOST_SALT, pan.saltCount + SALT_PER_BUCKET);
                shown = true;
            }
            if (pan.brine.amount <= 0) shown = true;
            if (shown) pan.changed();
            else pan.setChanged();
        }

        /** What a look into the pan says: how much seawater, and whether anything is drying it. */
        Component describe() {
            if (brine.amount <= 0) return Component.translatable("message.hardwrought.salt_pan.empty");
            Level here = getLevel();
            String why = here == null || rate(here, worldPosition) <= 0 ? "still"
                    : StillBlockEntity.heated(here, worldPosition) ? "fire" : "sun";
            return Component.translatable("message.hardwrought.salt_pan.holds", brineMillibuckets(),
                    Component.translatable("message.hardwrought.salt_pan." + why));
        }

        void changed() {
            setChanged();
            if (level == null || level.isClientSide()) return;
            Contents contents = brine.amount > 0 ? Contents.BRINE : saltCount > 0 ? Contents.SALT : Contents.EMPTY;
            BlockState state = getBlockState();
            if (state.hasProperty(CONTENTS) && state.getValue(CONTENTS) != contents) {
                level.setBlock(worldPosition, state.setValue(CONTENTS, contents), Block.UPDATE_CLIENTS);
            }
        }

        @Override
        public void preRemoveSideEffects(BlockPos pos, BlockState state) {
            super.preRemoveSideEffects(pos, state);
            if (level != null && saltCount > 0) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(ModItems.SALT, saltCount));
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            if (brine.amount > 0) output.putLong("brine", brine.amount);
            if (saltCount > 0) output.putInt("salt", saltCount);
            if (dried > 0) output.putLong("dried", dried);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            brine.amount = input.getLongOr("brine", 0);
            brine.variant = brine.amount > 0 ? SaltPan.water(WaterQuality.SALT) : FluidVariant.blank();
            saltCount = input.getIntOr("salt", 0);
            dried = input.getLongOr("dried", 0);
        }
    }
}
