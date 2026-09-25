package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.progression.HewnWorkbenchBlock;
import de.ipnats.hardwrought.progression.NailedWorkbenchBlock;
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
    /** Tier two: the same timber rebuilt as split boards and held together with iron nails. */
    public static final NailedWorkbenchBlock NAILED_WORKBENCH = register("nailed_workbench",
            NailedWorkbenchBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));

    /**
     * Section 56: the first furnace, and a poor one. Four fired bricks in the inventory square, so
     * a player can smelt before they own a crafting table.
     */
    public static final de.ipnats.hardwrought.metallurgy.BrickFurnaceBlock BRICK_FURNACE =
            register("brick_furnace", de.ipnats.hardwrought.metallurgy.BrickFurnaceBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).lightLevel(
                            state -> state.getValue(de.ipnats.hardwrought.metallurgy.BrickFurnaceBlock.LIT) ? 13 : 0));

    /**
     * Milestone-72 groundwork: a bar that turns. Deliberately the smallest thing that can prove the
     * render path for machinery, and deliberately without any mechanics attached to it yet.
     */
    public static final de.ipnats.hardwrought.machinery.ShaftBlock SHAFT = register("shaft",
            de.ipnats.hardwrought.machinery.ShaftBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).noOcclusion());
    /** The crank that drives a line of them. One block, one switch, no mechanics. */
    public static final de.ipnats.hardwrought.machinery.CrankBoxBlock CRANK_BOX = register("crank_box",
            de.ipnats.hardwrought.machinery.CrankBoxBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));
    /**
     * Section 72: the crank a player turns by hand. Drives the shaft it is mounted on for as long as
     * the use button is held on it, and costs stamina for every stroke.
     */
    public static final de.ipnats.hardwrought.machinery.HandCrankBlock HAND_CRANK = register("hand_crank",
            de.ipnats.hardwrought.machinery.HandCrankBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).noOcclusion());
    /**
     * Section 53: the first machine on the driveline. Breaks raw ore down to powder while something
     * turns into it.
     */
    public static final de.ipnats.hardwrought.machinery.StarterCrusherBlock STARTER_CRUSHER = register(
            "starter_crusher", de.ipnats.hardwrought.machinery.StarterCrusherBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.COBBLESTONE).noOcclusion());
    /** The head of an ore drill; see {@code OreDrillBlockEntity}. */
    public static final de.ipnats.hardwrought.machinery.OreDrillBlock ORE_DRILL = register("ore_drill",
            de.ipnats.hardwrought.machinery.OreDrillBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4.0f, 6.0f));
    public static final de.ipnats.hardwrought.machinery.DrillFrameBlock DRILL_FRAME_BRONZE = register("drill_frame_bronze",
            properties -> new de.ipnats.hardwrought.machinery.DrillFrameBlock(
                    de.ipnats.hardwrought.geology.DrillTier.BRONZE, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).strength(3.0f + 0, 6.0f)
                    .requiresCorrectToolForDrops());
    public static final de.ipnats.hardwrought.machinery.DrillFrameBlock DRILL_FRAME_IRON = register("drill_frame_iron",
            properties -> new de.ipnats.hardwrought.machinery.DrillFrameBlock(
                    de.ipnats.hardwrought.geology.DrillTier.IRON, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).strength(3.0f + 1, 6.0f)
                    .requiresCorrectToolForDrops());
    public static final de.ipnats.hardwrought.machinery.DrillFrameBlock DRILL_FRAME_NICKEL = register("drill_frame_nickel",
            properties -> new de.ipnats.hardwrought.machinery.DrillFrameBlock(
                    de.ipnats.hardwrought.geology.DrillTier.NICKEL, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).strength(3.0f + 2, 6.0f)
                    .requiresCorrectToolForDrops());
    public static final de.ipnats.hardwrought.machinery.DrillFrameBlock DRILL_FRAME_CHROMIUM = register("drill_frame_chromium",
            properties -> new de.ipnats.hardwrought.machinery.DrillFrameBlock(
                    de.ipnats.hardwrought.geology.DrillTier.CHROMIUM, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).strength(3.0f + 3, 6.0f)
                    .requiresCorrectToolForDrops());
    public static final de.ipnats.hardwrought.machinery.DrillFrameBlock DRILL_FRAME_TITANIUM = register("drill_frame_titanium",
            properties -> new de.ipnats.hardwrought.machinery.DrillFrameBlock(
                    de.ipnats.hardwrought.geology.DrillTier.TITANIUM, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS).strength(3.0f + 4, 6.0f)
                    .requiresCorrectToolForDrops());
    /** Section 60: bores down to a reservoir and pumps it; see {@code DrillingRigBlock}. */
    public static final de.ipnats.hardwrought.oil.DrillingRigBlock DRILLING_RIG = register("drilling_rig",
            de.ipnats.hardwrought.oil.DrillingRigBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4.0f, 6.0f).noOcclusion());
    /** Section 62: a copper pot still over a fire; see {@code StillBlock}. */
    public static final de.ipnats.hardwrought.chemistry.StillBlock STILL = register("still",
            de.ipnats.hardwrought.chemistry.StillBlock::new,
            BlockBehaviour.Properties.of().strength(2.5f, 6.0f).requiresCorrectToolForDrops()
                    .sound(net.minecraft.world.level.block.SoundType.COPPER).noOcclusion());
    /** Section 38: the first anvil, a hardwood stump. Wears out; see {@code WoodenAnvilBlock}. */
    public static final de.ipnats.hardwrought.smithing.WoodenAnvilBlock WOODEN_ANVIL = register("wooden_anvil",
            de.ipnats.hardwrought.smithing.WoodenAnvilBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
    /** Section 38: the smith's hearth. Hotter with a bellows, hotter still lined; see {@code ForgeBlockEntity}. */
    public static final de.ipnats.hardwrought.smithing.ForgeBlock FORGE = register("forge",
            de.ipnats.hardwrought.smithing.ForgeBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).lightLevel(
                    state -> state.getValue(de.ipnats.hardwrought.smithing.ForgeBlock.LIT) ? 12 : 0));
    /**
     * Gas in the world: carbon dioxide, methane and carbon monoxide, up to eight units a block. Walked
     * and seen through; never placed by hand and never dropped. See {@code GasBlock}.
     */
    public static final de.ipnats.hardwrought.environment.GasBlock GAS = register("gas",
            de.ipnats.hardwrought.environment.GasBlock::new,
            BlockBehaviour.Properties.of().replaceable().noCollision().noOcclusion().noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false)
                    .noTerrainParticles());
    /** Hung over a forge, takes its smoke and spent air out of the room; see {@code ForgeHoodBlock}. */
    public static final de.ipnats.hardwrought.smithing.ForgeHoodBlock FORGE_HOOD = register("forge_hood",
            de.ipnats.hardwrought.smithing.ForgeHoodBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).noOcclusion());
    /** Carries what a hood catches to wherever its open end is; see {@code GasPipeBlock}. */
    public static final de.ipnats.hardwrought.smithing.GasPipeBlock GAS_PIPE = register("gas_pipe",
            de.ipnats.hardwrought.smithing.GasPipeBlock::new,
            BlockBehaviour.Properties.of().strength(1.5f, 6.0f).requiresCorrectToolForDrops()
                    .sound(net.minecraft.world.level.block.SoundType.COPPER).noOcclusion());
    /** Blows a forge beside it hotter, while something turns it. */
    public static final de.ipnats.hardwrought.smithing.BellowsBlock BELLOWS = register("bellows",
            de.ipnats.hardwrought.smithing.BellowsBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));
    /**
     * The turning bar itself, and the one block here that is never placed, never dropped and has no
     * item. A block model is baked into the chunk mesh once and cannot move, so the moving part of a
     * machine has to be drawn by a renderer — and a renderer draws a block state. This is that state
     * and nothing else. Every later machine with a moving part will want one of these.
     */
    public static final Block SHAFT_BAR = register("shaft_bar", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).noOcclusion());

    /** Section 73: a small gear on a shaft. Meets other small gears beside it, large ones across a corner. */
    public static final de.ipnats.hardwrought.machinery.CogwheelBlock COGWHEEL = register("cogwheel",
            de.ipnats.hardwrought.machinery.CogwheelBlock::small,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
    /** Twice the size: gearing a small one to it doubles the speed or the strength. */
    public static final de.ipnats.hardwrought.machinery.CogwheelBlock LARGE_COGWHEEL = register("large_cogwheel",
            de.ipnats.hardwrought.machinery.CogwheelBlock::large,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
    /** Section 73: a box of bevel gears that turns a line round a corner. */
    public static final de.ipnats.hardwrought.machinery.GearboxBlock GEARBOX = register("gearbox",
            de.ipnats.hardwrought.machinery.GearboxBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));
    /** Section 74: the hub of a water wheel. The wheel around it is drawn. */
    public static final de.ipnats.hardwrought.machinery.WaterWheelBlock WATER_WHEEL = register("water_wheel",
            de.ipnats.hardwrought.machinery.WaterWheelBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
    /** Section 74: the hub of a windmill. The sails around it are drawn. */
    public static final de.ipnats.hardwrought.machinery.WindmillBlock WINDMILL = register("windmill",
            de.ipnats.hardwrought.machinery.WindmillBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());

    // Moving parts, never placed: drawn turning by the renderers, like the shaft bar.
    public static final Block COGWHEEL_GEAR = register("cogwheel_gear", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
    public static final Block LARGE_COGWHEEL_GEAR = register("large_cogwheel_gear", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
    public static final Block WATER_WHEEL_RIM = register("water_wheel_rim", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
    public static final Block WINDMILL_SAILS = register("windmill_sails", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());
    public static final Block BELT_STRIP = register("belt_strip", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());

    /** Sticks lashed into a frame: green leaf fibre hung on it dries in the sun into cord. */
    public static final de.ipnats.hardwrought.progression.DryingRackBlock DRYING_RACK = register("drying_rack",
            de.ipnats.hardwrought.progression.DryingRackBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).noOcclusion());

    /** The hand crank's handle, drawn turning by its renderer. Never placed, like the shaft bar. */
    public static final Block HAND_CRANK_HANDLE = register("hand_crank_handle", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).noOcclusion());

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
