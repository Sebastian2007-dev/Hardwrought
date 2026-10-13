package de.ipnats.hardwrought.fluids;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.chemistry.StillBlockEntity;
import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.machinery.PipeWrenchItem;
import de.ipnats.hardwrought.oil.DrillingRigBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** The blocks that move and hold liquids: fluid pipes, pumps, the tank, and the wrench that sets pipes. */
public final class FluidBlocks {
    public static final Map<FluidPipeTier, FluidPipeBlock> PIPES = new EnumMap<>(FluidPipeTier.class);

    static {
        for (FluidPipeTier tier : FluidPipeTier.values()) {
            PIPES.put(tier, block("fluid_pipe_" + tier.serializedName(), properties -> new FluidPipeBlock(tier, properties),
                    BlockBehaviour.Properties.of().strength(1.5f + tier.ordinal() * 0.5f, 6.0f).requiresCorrectToolForDrops()
                            .sound(SoundType.COPPER).noOcclusion()));
        }
    }

    public static final MechanicalPumpBlock MECHANICAL_PUMP = block("mechanical_pump", MechanicalPumpBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER));
    public static final ElectricPumpBlock ELECTRIC_PUMP = block("electric_pump", ElectricPumpBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));
    public static final FluidTankBlock TANK = block("fluid_tank", FluidTankBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS).noOcclusion());
    /** Never placed; see {@link FluidDisplayBlock}. */
    public static final FluidDisplayBlock DISPLAY;

    static {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id("fluid_display"));
        DISPLAY = Registry.register(BuiltInRegistries.BLOCK, key, new FluidDisplayBlock(BlockBehaviour.Properties.of()
                .noCollision().noOcclusion().noLootTable().setId(key)));
    }

    public static final Item PIPE_WRENCH;

    static {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id("pipe_wrench"));
        PIPE_WRENCH = Registry.register(BuiltInRegistries.ITEM, key, new PipeWrenchItem(new Item.Properties().stacksTo(1).setId(key)));
    }

    public static final BlockEntityType<FluidPipeBlockEntity> PIPE_ENTITY = entity("fluid_pipe",
            new BlockEntityType<>(FluidPipeBlockEntity::new, Set.copyOf(PIPES.values())));
    public static final BlockEntityType<MechanicalPumpBlock.Entity> MECHANICAL_PUMP_ENTITY = entity("mechanical_pump",
            new BlockEntityType<>(MechanicalPumpBlock.Entity::new, Set.of(MECHANICAL_PUMP)));
    public static final BlockEntityType<ElectricPumpBlock.Entity> ELECTRIC_PUMP_ENTITY = entity("electric_pump",
            new BlockEntityType<>(ElectricPumpBlock.Entity::new, Set.of(ELECTRIC_PUMP)));
    public static final BlockEntityType<FluidTankBlock.Entity> TANK_ENTITY = entity("fluid_tank",
            new BlockEntityType<>(FluidTankBlock.Entity::new, Set.of(TANK)));

    private FluidBlocks() { }

    /** Touching the class registers everything in it; then what holds a liquid is offered to the pipes. */
    public static void initialize() {
        // Tanks that touch are one store.
        FluidStorage.SIDED.registerForBlockEntity((tank, side) -> FluidTankBlock.storage(tank.getLevel(), tank.getBlockPos()), TANK_ENTITY);
        // A well gives up its oil to a pipe that draws from it; a still takes crude oil in to distil.
        FluidStorage.SIDED.registerForBlockEntity((rig, side) -> rig.oilOutlet(), ModBlockEntities.DRILLING_RIG);
        FluidStorage.SIDED.registerForBlockEntity((still, side) -> still.oilInlet(), ModBlockEntities.STILL);
    }

    /** Everything a player can hold, for the creative tab. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (FluidPipeBlock pipe : PIPES.values()) items.add(pipe.asItem());
        items.add(MECHANICAL_PUMP.asItem());
        items.add(ELECTRIC_PUMP.asItem());
        items.add(TANK.asItem());
        items.add(PIPE_WRENCH);
        return items;
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
