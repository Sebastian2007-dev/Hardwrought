package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.metallurgy.BrickFurnaceBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

/** Block entities this mod adds. */
public final class ModBlockEntities {
    public static final BlockEntityType<BrickFurnaceBlockEntity> BRICK_FURNACE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("brick_furnace"),
            new BlockEntityType<>(BrickFurnaceBlockEntity::new, Set.of(ModBlocks.BRICK_FURNACE)));

    /** Holds nothing but the angle its renderer draws the bar at. */
    public static final BlockEntityType<de.ipnats.hardwrought.machinery.ShaftBlockEntity> SHAFT =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("shaft"),
                    new BlockEntityType<>(de.ipnats.hardwrought.machinery.ShaftBlockEntity::new,
                            Set.of(ModBlocks.SHAFT)));

    /** When a hand crank was last pushed round, and the angle its handle is drawn at. */
    public static final BlockEntityType<de.ipnats.hardwrought.machinery.HandCrankBlockEntity> HAND_CRANK =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("hand_crank"),
                    new BlockEntityType<>(de.ipnats.hardwrought.machinery.HandCrankBlockEntity::new,
                            Set.of(ModBlocks.HAND_CRANK)));

    /** The crusher's ore, its powder and how far the current piece has got. */
    public static final BlockEntityType<de.ipnats.hardwrought.machinery.StarterCrusherBlockEntity> STARTER_CRUSHER =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("starter_crusher"),
                    new BlockEntityType<>(de.ipnats.hardwrought.machinery.StarterCrusherBlockEntity::new,
                            Set.of(ModBlocks.STARTER_CRUSHER)));

    /** The grid of a nailed bench, kept between one use and the next. */
    public static final BlockEntityType<de.ipnats.hardwrought.progression.KeptGridBlockEntity> KEPT_GRID =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("kept_grid"),
                    new BlockEntityType<>(de.ipnats.hardwrought.progression.KeptGridBlockEntity::new,
                            Set.of(ModBlocks.NAILED_WORKBENCH)));

    /** The forge's fire and the pieces lying in it. */
    public static final BlockEntityType<de.ipnats.hardwrought.smithing.ForgeBlockEntity> FORGE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("forge"),
                    new BlockEntityType<>(de.ipnats.hardwrought.smithing.ForgeBlockEntity::new,
                            Set.of(ModBlocks.FORGE)));

    /** The speed of a plain turning part: a gear, a bellows. */
    public static final BlockEntityType<de.ipnats.hardwrought.machinery.KineticBlockEntity> KINETIC =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("kinetic"),
                    new BlockEntityType<>((pos, state) -> new de.ipnats.hardwrought.machinery.KineticBlockEntity(
                            ModBlockEntities.KINETIC, pos, state),
                            Set.of(ModBlocks.COGWHEEL, ModBlocks.LARGE_COGWHEEL, ModBlocks.BELLOWS)));

    /** The speed of a water wheel and what its water gives it. */
    public static final BlockEntityType<de.ipnats.hardwrought.machinery.WaterWheelBlockEntity> WATER_WHEEL =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("water_wheel"),
                    new BlockEntityType<>(de.ipnats.hardwrought.machinery.WaterWheelBlockEntity::new,
                            Set.of(ModBlocks.WATER_WHEEL)));

    /** The speed of a windmill and what its wind gives it. */
    public static final BlockEntityType<de.ipnats.hardwrought.machinery.WindmillBlockEntity> WINDMILL =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("windmill"),
                    new BlockEntityType<>(de.ipnats.hardwrought.machinery.WindmillBlockEntity::new,
                            Set.of(ModBlocks.WINDMILL)));

    /** What hangs on a drying rack, and how dry it is. */
    public static final BlockEntityType<de.ipnats.hardwrought.progression.DryingRackBlockEntity> DRYING_RACK =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("drying_rack"),
                    new BlockEntityType<>(de.ipnats.hardwrought.progression.DryingRackBlockEntity::new,
                            Set.of(ModBlocks.DRYING_RACK)));

    private ModBlockEntities() { }

    /** Touching the class registers everything in it; called from the mod initializer. */
    public static void initialize() { }
}
