package de.ipnats.hardwrought.refining;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.gasline.GasStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** The refinery column, its base and trays, and the cracker and reformer that work its cuts further. */
public final class RefineryBlocks {
    public static final RefineryBaseBlock BASE = block("refinery_base", RefineryBaseBlock::new, metal());
    public static final RefineryStageBlock STAGE = block("refinery_stage", RefineryStageBlock::new, metal());
    public static final ProcessorBlock CRACKER = block("cracker", p -> new ProcessorBlock(ProcessorBlock.CRACKING, p), metal());
    public static final ProcessorBlock REFORMER = block("reformer", p -> new ProcessorBlock(ProcessorBlock.REFORMING, p), metal());
    public static final FlareStackBlock FLARE = block("burning_stack", FlareStackBlock::new, metal()
            .lightLevel(state -> state.getValue(FlareStackBlock.LIT) ? 15 : 0));

    public static final BlockEntityType<RefineryBaseBlock.Entity> BASE_ENTITY = entity("refinery_base",
            new BlockEntityType<>(RefineryBaseBlock.Entity::new, Set.of(BASE)));
    public static final BlockEntityType<RefineryStageBlock.Entity> STAGE_ENTITY = entity("refinery_stage",
            new BlockEntityType<>(RefineryStageBlock.Entity::new, Set.of(STAGE)));
    public static final BlockEntityType<ProcessorBlock.Entity> PROCESSOR_ENTITY = entity("refinery_processor",
            new BlockEntityType<>(ProcessorBlock.Entity::new, Set.of(CRACKER, REFORMER)));
    public static final BlockEntityType<FlareStackBlock.Entity> FLARE_ENTITY = entity("burning_stack",
            new BlockEntityType<>(FlareStackBlock.Entity::new, Set.of(FLARE)));

    private RefineryBlocks() { }

    /** Touching the class registers everything; then pipes and gas lines are told what each holds. */
    public static void initialize() {
        FluidStorage.SIDED.registerForBlockEntity((base, side) -> base.storage(), BASE_ENTITY);
        FluidStorage.SIDED.registerForBlockEntity((stage, side) -> stage.outlet(), STAGE_ENTITY);
        FluidStorage.SIDED.registerForBlockEntity((processor, side) -> processor.storage(), PROCESSOR_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((stage, side) -> stage.gas, STAGE_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((processor, side) -> processor.gas, PROCESSOR_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((stack, side) -> side == Direction.DOWN ? null : stack.intake, FLARE_ENTITY);
    }

    public static List<Item> items() {
        return List.of(BASE.asItem(), STAGE.asItem(), CRACKER.asItem(), REFORMER.asItem(), FLARE.asItem());
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 8.0f).requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
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

    private static <T extends BlockEntityType<?>> T entity(String name, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id(name), type);
    }
}
