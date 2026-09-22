package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.progression.HewnWorkbenchBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

/** Central registration point for Hardwrought blocks. */
public final class ModBlocks {
    public static final Block DIRT_SLAB = register("dirt_slab", SlabBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT));
    /** Section 71: the first workbench is cut out of a log, not joined out of boards. */
    public static final HewnWorkbenchBlock HEWN_WORKBENCH = register("hewn_workbench",
            HewnWorkbenchBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));

    /**
     * Section 56: the first furnace, and a poor one. Four fired bricks in the inventory square, so
     * a player can smelt before they own a crafting table.
     */
    public static final de.ipnats.hardwrought.metallurgy.BrickFurnaceBlock BRICK_FURNACE =
            register("brick_furnace", de.ipnats.hardwrought.metallurgy.BrickFurnaceBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).lightLevel(
                            state -> state.getValue(de.ipnats.hardwrought.metallurgy.BrickFurnaceBlock.LIT) ? 13 : 0));

    private ModBlocks() { }

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory,
                                                BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        T block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    public static void initialize() {
        // Loading this class registers its static block fields before their BlockItems are created.
    }
}
