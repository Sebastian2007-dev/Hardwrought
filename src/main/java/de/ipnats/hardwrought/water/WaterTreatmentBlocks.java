package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricNodeBlock;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.knowledge.WorldRecipes;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The ladder of cleaning water after the bowl (see {@link WaterTreatment.Stage}): a sand and charcoal
 * filter, a ceramic filter, and the electric purifier. The two filters work by dripping, with no power,
 * and use up the charcoal put into them; the purifier wants current, like the other machines, and can
 * take the salt out of seawater as well.
 *
 * <p>Water poured on top of one soaks in, and comes out cleaned underneath, into a tank there or onto
 * the ground. A bucket of water or a pipe fills one too, and an empty bucket, an empty bowl or a pipe
 * drawing from it takes clean water out of it. An empty hand reads them.
 */
public final class WaterTreatmentBlocks {
    public static final FilterBlock SAND_FILTER = block("sand_filter",
            properties -> new FilterBlock(WaterTreatment.Stage.SAND, properties),
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD));
    public static final FilterBlock CERAMIC_FILTER = block("ceramic_filter",
            properties -> new FilterBlock(WaterTreatment.Stage.CERAMIC, properties),
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(2.5f, 6.0f)
                    .requiresCorrectToolForDrops().sound(SoundType.DECORATED_POT));
    public static final PurifierBlock PURIFIER = block("water_purifier", PurifierBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    public static final BlockEntityType<FilterEntity> FILTER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("water_filter"), new BlockEntityType<>(FilterEntity::new, Set.of(SAND_FILTER, CERAMIC_FILTER)));
    public static final BlockEntityType<PurifierEntity> PURIFIER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("water_purifier"), new BlockEntityType<>(PurifierEntity::new, Set.of(PURIFIER)));

    private WaterTreatmentBlocks() { }

    /** Touching the class registers everything; then pipes may fill them and draw from them. */
    public static void initialize() {
        FluidStorage.SIDED.registerForBlockEntity((filter, side) -> filter.treatment.both, FILTER_ENTITY);
        FluidStorage.SIDED.registerForBlockEntity((purifier, side) -> purifier.treatment.both, PURIFIER_ENTITY);
    }

    public static List<Item> items() {
        return List.of(SAND_FILTER.asItem(), CERAMIC_FILTER.asItem(), PURIFIER.asItem());
    }

    /** For the book: what each stage makes of the water it can clean. */
    public static List<WorldRecipes.WorldRecipe> worldRecipes() {
        return List.of(
                new WorldRecipes.WorldRecipe(Hardwrought.id("sand_filter/fresh_water"),
                        WorldRecipes.one(bucket(WaterQuality.SWAMP), new ItemStack(Items.CHARCOAL)),
                        bucket(WaterQuality.FRESH), new ItemStack(SAND_FILTER)),
                new WorldRecipes.WorldRecipe(Hardwrought.id("ceramic_filter/purified_water"),
                        WorldRecipes.one(bucket(WaterQuality.FRESH), new ItemStack(Items.CHARCOAL)),
                        bucket(WaterQuality.PURIFIED), new ItemStack(CERAMIC_FILTER)),
                new WorldRecipes.WorldRecipe(Hardwrought.id("water_purifier/purified_water"),
                        WorldRecipes.one(bucket(WaterQuality.SALT)), bucket(WaterQuality.PURIFIED), new ItemStack(PURIFIER)));
    }

    private static ItemStack bucket(WaterQuality quality) {
        return BucketWater.with(new ItemStack(Items.BUCKET), BucketWater.CAPACITY, quality);
    }

    // ---------------------------------------------------------------- using one by hand

    /**
     * A bucket of water fills the dirty side; an empty bucket or bowl is filled from the clean side;
     * charcoal goes into a filter's bed.
     */
    static InteractionResult useItem(WaterTreatment treatment, ItemStack stack, Level level, BlockPos pos, Player player,
                                     InteractionHand hand) {
        if (stack.is(Items.CHARCOAL) && treatment.stage().bucketsPerCharcoal() > 0) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!treatment.addCharcoal()) {
                player.sendOverlayMessage(Component.translatable("message.hardwrought.water_filter.bed_full"));
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.WATER_BUCKET) && BucketWater.isFull(stack)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            var water = de.ipnats.hardwrought.chemistry.SaltPan.water(WaterEvents.qualityOf(stack));
            if (!(treatment.input.amount == 0 || treatment.input.variant.equals(water))
                    || WaterTreatment.CAPACITY - treatment.input.amount < FluidConstants.BUCKET) {
                player.sendOverlayMessage(Component.translatable("message.hardwrought.water_filter.no_room"));
                return InteractionResult.FAIL;
            }
            treatment.input.variant = water;
            treatment.input.amount += FluidConstants.BUCKET;
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        int wanted = stack.is(Items.BUCKET) ? WaterAmounts.BUCKET : stack.is(Items.BOWL) ? WaterBowlItem.MILLIBUCKETS : 0;
        if (wanted > 0) {
            if (treatment.outputMillibuckets() < wanted) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            WaterQuality quality = treatment.outputQuality();
            treatment.output.amount -= wanted * (FluidConstants.BUCKET / 1000);
            if (treatment.output.amount <= 0) treatment.output.variant = net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.blank();
            ItemStack filled = stack.is(Items.BUCKET)
                    ? BucketWater.with(new ItemStack(Items.BUCKET), WaterAmounts.BUCKET, quality)
                    : WaterBowlItem.of(ModItems.WATER_BOWL, quality);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filled));
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** What an empty hand reads off it: what is in each side, and what is left of the bed. */
    static Component describe(WaterTreatment treatment) {
        Component in = treatment.inputQuality() == null ? Component.translatable("message.hardwrought.water_filter.nothing")
                : Component.translatable("message.hardwrought.water_filter.amount", treatment.inputMillibuckets(),
                Component.translatable("water_quality.hardwrought." + treatment.inputQuality().serializedName()));
        Component out = treatment.outputQuality() == null ? Component.translatable("message.hardwrought.water_filter.nothing")
                : Component.translatable("message.hardwrought.water_filter.amount", treatment.outputMillibuckets(),
                Component.translatable("water_quality.hardwrought." + treatment.outputQuality().serializedName()));
        if (treatment.stage().bucketsPerCharcoal() <= 0) {
            return Component.translatable("message.hardwrought.water_purifier.reads", in, out);
        }
        return Component.translatable("message.hardwrought.water_filter.reads", in, out, treatment.mediumMillibuckets());
    }

    // ---------------------------------------------------------------- the filters

    public static class FilterBlock extends Block implements EntityBlock {
        private final WaterTreatment.Stage stage;

        public FilterBlock(WaterTreatment.Stage stage, Properties properties) {
            super(properties);
            this.stage = stage;
        }

        public WaterTreatment.Stage stage() {
            return stage;
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
            if (!(level.getBlockEntity(pos) instanceof FilterEntity filter)) return InteractionResult.TRY_WITH_EMPTY_HAND;
            return useItem(filter.treatment, stack, level, pos, player, hand);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level.getBlockEntity(pos) instanceof FilterEntity filter)) return InteractionResult.PASS;
            if (!level.isClientSide()) player.sendOverlayMessage(describe(filter.treatment));
            return InteractionResult.SUCCESS;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FilterEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide() || type != FILTER_ENTITY) return null;
            return (tickLevel, pos, tickState, entity) -> {
                FilterEntity filter = (FilterEntity) entity;
                ServerLevel server = (ServerLevel) tickLevel;
                filter.treatment.soak(server, pos);
                filter.treatment.work(1.0);
                filter.treatment.drain(server, pos);
            };
        }
    }

    public static class FilterEntity extends BlockEntity {
        public final WaterTreatment treatment;

        public FilterEntity(BlockPos pos, BlockState state) {
            super(FILTER_ENTITY, pos, state);
            WaterTreatment.Stage stage = state.getBlock() instanceof FilterBlock filter ? filter.stage() : WaterTreatment.Stage.SAND;
            treatment = new WaterTreatment(stage, this::setChanged);
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            treatment.save(output);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            treatment.load(input);
        }
    }

    // ---------------------------------------------------------------- the purifier

    /**
     * The electric purifier, joined to a line through a coil set against it like the other machines.
     * At its rated voltage it purifies {@code 1000} millibuckets a second, and desalts seawater at a
     * quarter of that; less as the voltage drops ({@link MachineBlockEntity#rate}).
     */
    public static class PurifierBlock extends ElectricNodeBlock {
        public PurifierBlock(Properties properties) {
            super(properties);
        }

        @Override
        public Vec3 terminal(BlockState state) {
            return new Vec3(0.5, 1.0, 0.5);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
            if (!(level.getBlockEntity(pos) instanceof PurifierEntity purifier)) return InteractionResult.TRY_WITH_EMPTY_HAND;
            return useItem(purifier.treatment, stack, level, pos, player, hand);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(level.getBlockEntity(pos) instanceof PurifierEntity purifier)) return InteractionResult.PASS;
            if (!level.isClientSide()) player.sendOverlayMessage(describe(purifier.treatment));
            return InteractionResult.SUCCESS;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new PurifierEntity(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide() || type != PURIFIER_ENTITY) return null;
            return (tickLevel, pos, tickState, entity) -> {
                PurifierEntity purifier = (PurifierEntity) entity;
                ServerLevel server = (ServerLevel) tickLevel;
                purifier.treatment.soak(server, pos);
                purifier.treatment.work(MachineBlockEntity.rate(purifier.volts()));
                purifier.treatment.drain(server, pos);
            };
        }
    }

    public static class PurifierEntity extends ElectricBlockEntity {
        public static final double OHMS = 30.0;
        public final WaterTreatment treatment = new WaterTreatment(WaterTreatment.Stage.ELECTRIC, this::setChanged);

        public PurifierEntity(BlockPos pos, BlockState state) {
            super(PURIFIER_ENTITY, pos, state);
        }

        @Override
        public double conductance() {
            return 1.0 / OHMS;
        }

        @Override
        public boolean takesWires() {
            return false;
        }

        @Override
        public boolean mindsRain() {
            return true;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            treatment.save(output);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            treatment.load(input);
        }
    }

    private static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory,
                                             BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }
}
