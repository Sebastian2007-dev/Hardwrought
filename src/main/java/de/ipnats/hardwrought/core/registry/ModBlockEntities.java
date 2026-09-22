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

    private ModBlockEntities() { }

    /** Touching the class registers everything in it; called from the mod initializer. */
    public static void initialize() { }
}
