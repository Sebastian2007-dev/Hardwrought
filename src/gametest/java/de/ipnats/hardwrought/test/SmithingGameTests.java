package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.ForgingPayloads;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.smithing.Anvils;
import de.ipnats.hardwrought.smithing.ForgeBlock;
import de.ipnats.hardwrought.smithing.ForgeBlockEntity;
import de.ipnats.hardwrought.smithing.ForgeMultiblock;
import de.ipnats.hardwrought.smithing.ForgeQuality;
import de.ipnats.hardwrought.smithing.Forging;
import de.ipnats.hardwrought.smithing.ForgingState;
import de.ipnats.hardwrought.smithing.Hammers;
import de.ipnats.hardwrought.smithing.Heat;
import de.ipnats.hardwrought.smithing.Mask;
import de.ipnats.hardwrought.smithing.Smithing;
import de.ipnats.hardwrought.smithing.ToolParts;
import de.ipnats.hardwrought.smithing.WoodenAnvilBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.List;

/**
 * Specification milestone 9, sections 37 and 38: heat, the anvil, quality and heat treatment.
 */
public final class SmithingGameTests {
    /** Looked up when a test runs: the test class is loaded before the mod has registered its parts. */
    private static Item pickHead() {
        return ToolParts.part(ToolParts.SmithMetal.IRON, ToolParts.Part.PICKAXE_HEAD);
    }

    @GameTest
    public void metalCoolsOnItsOwnAndWaterAndFireTreatIron(GameTestHelper helper) {
        var materials = CoreLifecycle.require(helper.getLevel().getServer()).materials();
        Heat heat = new Heat(1200f, 1000);
        helper.assertTrue(Math.abs(heat.at(1000) - 1200) < 1e-6, "Just out of the fire it is as hot as the fire");
        helper.assertTrue(heat.at(1400) < 1000 && heat.at(1400) > 846,
                "Twenty seconds on it has cooled, and is still hot enough to work");
        helper.assertTrue(heat.at(6000) < Heat.BURNS_ABOVE, "After a few minutes it is safe to touch");

        ItemStack head = new ItemStack(pickHead());
        head.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(0.8f, ForgeQuality.Treatment.AIR));
        Smithing.heat(head, 900, 0, materials);
        Smithing.quench(head, 0, materials);
        helper.assertTrue(ForgeQuality.of(head).treatment() == ForgeQuality.Treatment.QUENCHED,
                "Iron plunged into water from working heat comes out hard");
        helper.assertFalse(head.has(ModDataComponents.HEAT), "and cold at once");
        Smithing.heat(head, 300, 0, materials);
        helper.assertTrue(ForgeQuality.of(head).treatment() == ForgeQuality.Treatment.TEMPERED,
                "Warmed gently after quenching, it tempers");
        Smithing.heat(head, 1000, 0, materials);
        helper.assertTrue(ForgeQuality.of(head).treatment() == ForgeQuality.Treatment.AIR,
                "Brought back to forging heat, it is soft again");

        ItemStack bronze = new ItemStack(ToolParts.part(ToolParts.SmithMetal.BRONZE, ToolParts.Part.PICKAXE_HEAD));
        bronze.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(0.8f, ForgeQuality.Treatment.AIR));
        Smithing.heat(bronze, 800, 0, materials);
        Smithing.quench(bronze, 0, materials);
        helper.assertTrue(ForgeQuality.of(bronze).treatment() == ForgeQuality.Treatment.AIR,
                "Bronze takes no hardening from water");

        ForgeQuality tempered = new ForgeQuality(1.0f, ForgeQuality.Treatment.TEMPERED);
        ForgeQuality poor = new ForgeQuality(0.1f, ForgeQuality.Treatment.AIR);
        helper.assertTrue(tempered.speedFactor() > 1.0 && tempered.durabilityFactor() > 1.0
                        && poor.speedFactor() < 1.0 && poor.durabilityFactor() < 1.0,
                "Section 37: good work beats vanilla, poor work falls short of it");
        helper.succeed();
    }

    @GameTest(maxTicks = 320)
    public void aFurnaceHeatsRawIronForTheAnvilInsteadOfSmeltingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertFalse(level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE,
                        Identifier.withDefaultNamespace("iron_ingot_from_smelting_raw_iron")))
                        .map(holder -> holder.value().placementInfo().ingredients().getFirst()
                                .acceptsItem(Items.RAW_IRON.builtInRegistryHolder()))
                        .orElse(false),
                "Raw iron is not smelted into a bar any more; that recipe now casts powder");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, ModBlocks.BRICK_FURNACE.defaultBlockState());
        var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 3));
        furnace.setItem(1, new ItemStack(Items.COAL, 2));

        BlockPos smeltingPos = helper.absolutePos(new BlockPos(3, 1, 1));
        level.setBlockAndUpdate(smeltingPos, Blocks.FURNACE.defaultBlockState());
        var smeltingFurnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(smeltingPos);
        smeltingFurnace.setItem(0, new ItemStack(Items.SAND, 2));
        smeltingFurnace.setItem(1, new ItemStack(Items.COAL));
        helper.runAfterDelay(260, () -> {
            ItemStack out = furnace.getItem(2);
            helper.assertTrue(out.is(Items.RAW_IRON) && out.getCount() == 3,
                    "The complete load comes out hot together: " + out);
            helper.assertTrue(furnace.getItem(0).isEmpty(),
                    "Heating consumes the complete input load instead of leaving two cold pieces behind");
            double celsius = Heat.of(out, level.getGameTime());
            helper.assertTrue(celsius > 1100 && celsius <= 1540 * Smithing.WORKING_MAX + 1,
                    "brought to working heat and no further: " + celsius);
            helper.assertTrue(smeltingFurnace.getItem(2).is(Items.GLASS)
                            && smeltingFurnace.getItem(2).getCount() == 1
                            && smeltingFurnace.getItem(0).is(Items.SAND)
                            && smeltingFurnace.getItem(0).getCount() == 1,
                    "A real smelting recipe still processes only one item per operation");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(smeltingPos, Blocks.AIR.defaultBlockState(), 2);
            helper.succeed();
        });
    }

    @GameTest
    public void rawOreAndBarsStillShiftClickIntoAFurnace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, ModBlocks.BRICK_FURNACE.defaultBlockState());
        var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
        try {
            player.getInventory().clearContent();
            // A belt slot: the belt is always open, pack or no pack. Menu slots 30 to 38 are the belt.
            player.getInventory().setItem(3, new ItemStack(Items.RAW_IRON, 5));
            player.getInventory().setItem(4, new ItemStack(Items.IRON_INGOT, 2));
            var menu = new net.minecraft.world.inventory.FurnaceMenu(1, player.getInventory(), furnace,
                    new net.minecraft.world.inventory.SimpleContainerData(4));
            menu.quickMoveStack(player, 33);
            helper.assertTrue(furnace.getItem(0).is(Items.RAW_IRON) && furnace.getItem(0).getCount() == 5,
                    "Shift-clicked raw iron goes into the furnace to be heated: " + furnace.getItem(0));
            furnace.setItem(0, ItemStack.EMPTY);
            menu.quickMoveStack(player, 34);
            helper.assertTrue(furnace.getItem(0).is(Items.IRON_INGOT),
                    "and so do bars, for the anvil: " + furnace.getItem(0));
        } finally {
            player.getInventory().clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest
    public void theAnvilTurnsHotRawIronIntoABarAndBarsIntoAHead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos anvil = player.blockPosition().relative(player.getDirection());
        level.setBlockAndUpdate(anvil, ModBlocks.WOODEN_ANVIL.defaultBlockState());
        long now = level.getGameTime();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAMMER));
            ItemStack raw = new ItemStack(Items.RAW_IRON);
            raw.set(ModDataComponents.HEAT, new Heat(1200f, now));
            player.setItemInHand(InteractionHand.OFF_HAND, raw);

            Mask lump = square(4, 4, 11, 11);
            Mask bar = square(2, 6, 13, 9);
            helper.assertFalse(Forging.begin(player, new ForgingPayloads.Begin(anvil,
                            BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), lump.toArray(), lump.toArray())),
                    "Two identical shapes are no work, and are refused");
            helper.assertTrue(Forging.begin(player, new ForgingPayloads.Begin(anvil,
                            BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), lump.toArray(), bar.toArray())),
                    "Hot raw iron in hand, hammer in the other: the work begins");
            hammerUntilDone(helper, player, anvil, bar);
            ItemStack made = findInBag(player, Items.IRON_INGOT);
            helper.assertTrue(made.is(Items.IRON_INGOT), "The lump has become a bar, in the bag: " + made);
            helper.assertTrue(Heat.of(made, level.getGameTime()) > 800, "and it is still hot from the work");
            helper.assertTrue(level.getBlockState(anvil).getValue(WoodenAnvilBlock.USES) >= 2,
                    "The wooden anvil took one piece's wear");
            made.setCount(0);

            ItemStack bars = new ItemStack(Items.IRON_INGOT, 4);
            bars.set(ModDataComponents.HEAT, new Heat(1250f, level.getGameTime()));
            player.setItemInHand(InteractionHand.OFF_HAND, bars);
            helper.assertTrue(Forging.begin(player, new ForgingPayloads.Begin(anvil,
                            BuiltInRegistries.ITEM.getKey(pickHead()), bar.toArray(), square(1, 3, 14, 5).toArray())),
                    "A pick head can be begun from hot bars");
            // Counted over the whole inventory, which holds the piece on the anvil and the spare bar.
            helper.assertTrue(player.getOffhandItem().getCount() == 3
                            && player.getOffhandItem().has(ModDataComponents.FORGING_STATE)
                            && player.getInventory().countItem(Items.IRON_INGOT) == 4,
                    "The three bars of the head stay in the off hand; the fourth goes into the bag");
            hammerUntilDone(helper, player, anvil, square(1, 3, 14, 5));
            ItemStack head = findInBag(player, pickHead());
            helper.assertTrue(player.getOffhandItem().is(Items.IRON_INGOT) && player.getOffhandItem().getCount() == 1,
                    "The spare bar is back in the off hand, ready for the next piece");
            ForgeQuality quality = ForgeQuality.of(head);
            helper.assertTrue(head.is(pickHead()) && quality != null, "The bars are now a pick head with a quality");
            helper.assertTrue(quality.craftsmanship() > 0.5f && quality.craftsmanship() <= Anvils.WOOD_CAP,
                    "Clean work on a wooden anvil is good, and never better than wood allows: "
                            + quality.craftsmanship());
        } finally {
            player.getInventory().clearContent();
            level.setBlock(anvil, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest
    public void givingUpOnAPieceGivesBackEverythingThatWentIntoIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos anvil = player.blockPosition().relative(player.getDirection());
        level.setBlockAndUpdate(anvil, ModBlocks.WOODEN_ANVIL.defaultBlockState());
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAMMER));
            ItemStack bars = new ItemStack(Items.IRON_INGOT, 3);
            bars.set(ModDataComponents.HEAT, new Heat(1200f, level.getGameTime()));
            player.setItemInHand(InteractionHand.OFF_HAND, bars);
            Mask bar = square(2, 6, 13, 9);
            Mask head = square(1, 3, 14, 5);
            helper.assertTrue(Forging.begin(player, new ForgingPayloads.Begin(anvil,
                    BuiltInRegistries.ITEM.getKey(pickHead()), bar.toArray(), head.toArray())), "The work begins");
            Forging.strike(player, anvil, 3, 4);
            helper.assertTrue(player.getOffhandItem().getCount() == 3,
                    "The three bars stay together in the off hand while they are worked");
            ItemStack splitOff = player.getOffhandItem().split(1);
            helper.assertFalse(Forging.strike(player, anvil, 3, 4), "Two bars split from the three cannot go on as the head");
            player.getOffhandItem().grow(splitOff.getCount());

            helper.assertTrue(Forging.cancel(player), "It can be given up");
            ItemStack back = player.getOffhandItem();
            helper.assertTrue(back.is(Items.IRON_INGOT) && back.getCount() == 3,
                    "and all three bars come back: " + back);
            helper.assertFalse(back.has(ModDataComponents.FORGING_STATE), "as plain bars, with nothing half-done in them");
            helper.assertTrue(Heat.of(back, level.getGameTime()) > 800, "and still as hot as they were");
            helper.assertFalse(Forging.cancel(player), "Plain bars have nothing to give up");
        } finally {
            player.getInventory().clearContent();
            level.setBlock(anvil, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest
    public void wastedBlowsLowerTheCraftsmanship(GameTestHelper helper) {
        Mask lump = square(4, 4, 11, 11);
        Mask bar = square(2, 6, 13, 9);
        ForgingState clean = new ForgingState(BuiltInRegistries.ITEM.getKey(pickHead()), lump, bar, 1.0f);
        ForgingState sloppy = clean;
        for (int blow = 0; blow < 6; blow++) {
            clean = clean.struck(clean.currentMask().strike(3, 5, bar), true, 1.0f);
            sloppy = sloppy.struck(sloppy.currentMask(), false, 1.0f);
        }
        helper.assertTrue(clean.craftsmanship() > sloppy.craftsmanship() + 0.4f,
                "Blows that land where they are needed make far better work than wasted ones: "
                        + clean.craftsmanship() + " vs " + sloppy.craftsmanship());
        helper.succeed();
    }

    @GameTest
    public void aHammerDoesNothingToColdMetal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos anvil = player.blockPosition().relative(player.getDirection());
        level.setBlockAndUpdate(anvil, ModBlocks.WOODEN_ANVIL.defaultBlockState());
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAMMER));
            Mask lump = square(4, 4, 11, 11);
            Mask bar = square(2, 6, 13, 9);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.RAW_IRON));
            helper.assertFalse(Forging.begin(player, new ForgingPayloads.Begin(anvil,
                            BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), lump.toArray(), bar.toArray())),
                    "Cold raw iron cannot even be started on");

            ItemStack hot = new ItemStack(Items.RAW_IRON);
            hot.set(ModDataComponents.HEAT, new Heat(1200f, level.getGameTime()));
            player.setItemInHand(InteractionHand.OFF_HAND, hot);
            helper.assertTrue(Forging.begin(player, new ForgingPayloads.Begin(anvil,
                    BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), lump.toArray(), bar.toArray())), "Hot, it can");
            ItemStack piece = player.getOffhandItem();
            piece.set(ModDataComponents.HEAT, new Heat(400f, level.getGameTime()));
            Mask shapeBefore = piece.get(ModDataComponents.FORGING_STATE).currentMask();
            helper.assertFalse(Forging.strike(player, anvil, 3, 5), "A blow on metal gone cold does nothing");
            ForgingState after = player.getOffhandItem().get(ModDataComponents.FORGING_STATE);
            helper.assertTrue(after.strikes() == 0 && after.currentMask().mismatch(shapeBefore) == 0,
                    "no shape changes and no blow is counted");
        } finally {
            player.getInventory().clearContent();
            level.setBlock(anvil, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest
    public void aForgedHeadGivesItsQualityToTheToolItBecomes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var holder = level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.withDefaultNamespace("iron_pickaxe"))).orElseThrow();
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        ItemStack head = new ItemStack(pickHead());
        head.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(0.9f, ForgeQuality.Treatment.TEMPERED));
        CraftingInput input = CraftingInput.of(1, 3, List.of(head, new ItemStack(Items.STICK), new ItemStack(Items.STICK)));
        helper.assertTrue(recipe.matches(input, level), "A pick head and two sticks make a pickaxe");
        ItemStack pickaxe = recipe.assemble(input);
        helper.assertTrue(pickaxe.is(Items.IRON_PICKAXE), "the vanilla iron pickaxe: " + pickaxe);
        ForgeQuality quality = ForgeQuality.of(pickaxe);
        helper.assertTrue(quality != null && quality.treatment() == ForgeQuality.Treatment.TEMPERED,
                "and it carries the head's craftsmanship and treatment");
        ItemStack plain = new ItemStack(Items.IRON_PICKAXE);
        helper.assertTrue(pickaxe.getMaxDamage() > plain.getMaxDamage(),
                "A well made, tempered pickaxe lasts longer than an unforged one");
        helper.assertTrue(pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState())
                        > plain.getDestroySpeed(Blocks.STONE.defaultBlockState()),
                "and digs faster");
        helper.succeed();
    }

    @GameTest
    public void hotMetalBurnsABareHandButNotAGlovedOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var runtime = CoreLifecycle.require(level.getServer());
        ServerPlayer bare = helper.makeMockServerPlayerInLevel();
        ServerPlayer gloved = helper.makeMockServerPlayerInLevel();
        long now = level.getGameTime();
        for (ServerPlayer player : List.of(bare, gloved)) {
            player.setGameMode(GameType.SURVIVAL);
            ItemStack hot = new ItemStack(Items.IRON_INGOT);
            hot.set(ModDataComponents.HEAT, new Heat(1100f, now));
            player.setItemInHand(InteractionHand.MAIN_HAND, hot);
        }
        runtime.equipment().setGloves(gloved, new ItemStack(ModItems.SMITHING_GLOVES));
        try {
            helper.assertTrue(de.ipnats.hardwrought.smithing.SmithingEvents.exposed(bare, now),
                    "Glowing iron in a bare hand burns it");
            helper.assertFalse(de.ipnats.hardwrought.smithing.SmithingEvents.exposed(gloved, now),
                    "and does nothing to a gloved one");
            bare.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // A slot that is neither hand: slot 0 would be the selected belt slot, which is the hand.
            ItemStack bagged = new ItemStack(Items.IRON_INGOT);
            bagged.set(ModDataComponents.HEAT, new Heat(1100f, now));
            bare.getInventory().setItem(5, bagged);
            helper.assertFalse(de.ipnats.hardwrought.smithing.SmithingEvents.exposed(bare, now),
                    "In the bag it burns nobody");
        } finally {
            runtime.equipment().setGloves(gloved, ItemStack.EMPTY);
            bare.getInventory().clearContent();
            gloved.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 480)
    public void theForgeGetsHotterWithABellowsAndALiningAndHeatsWhatLiesInIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos forgePos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(forgePos, ModBlocks.FORGE.defaultBlockState());
        ForgeBlockEntity forge = (ForgeBlockEntity) level.getBlockEntity(forgePos);
        forge.addFuel(4000);
        helper.assertTrue(forge.target(level, forgePos, level.getBlockState(forgePos)) == ForgeBlockEntity.BASE_C,
                "A plain forge heads for the heat coal alone gives");

        BlockPos bellows = forgePos.east();
        level.setBlockAndUpdate(bellows, ModBlocks.BELLOWS.defaultBlockState());
        helper.assertTrue(forge.target(level, forgePos, level.getBlockState(forgePos)) == ForgeBlockEntity.BASE_C,
                "A bellows nobody works does nothing");
        level.setBlockAndUpdate(bellows.east(), ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
        helper.assertTrue(forge.target(level, forgePos, level.getBlockState(forgePos)) == ForgeBlockEntity.BELLOWS_C,
                "Worked by a turning crank, it drives the forge hotter");
        level.setBlockAndUpdate(forgePos, level.getBlockState(forgePos).setValue(ForgeBlock.LINED, true));
        helper.assertTrue(forge.target(level, forgePos, level.getBlockState(forgePos)) == ForgeBlockEntity.LINED_BELLOWS_C,
                "and lined with refractory brick it reaches the most any fire here can");

        helper.assertTrue(forge.place(new ItemStack(Items.RAW_IRON), 0), "Raw iron goes into the metal place");
        helper.runAfterDelay(420, () -> {
            double celsius = Heat.of(forge.getItem(ForgeBlockEntity.FIRST_METAL), level.getGameTime());
            helper.assertTrue(celsius > 1000, "The raw iron lying in it has come up to working heat: " + celsius);
            helper.assertTrue(celsius <= 1538 * Smithing.WORKING_MAX + 1, "and not past it");
            for (BlockPos pos : List.of(forgePos, bellows, bellows.east())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void aHotHeadThrownIntoWaterIsQuenchedAndBoilsALayerOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos water = helper.absolutePos(new BlockPos(2, 2, 2));
        // A basin of stone, so the water stays put and what the quench took can be measured.
        for (BlockPos around : BlockPos.betweenClosed(water.offset(-1, -1, -1), water.offset(1, 0, 1))) {
            if (!around.equals(water)) level.setBlockAndUpdate(around, Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
        int before = de.ipnats.hardwrought.water.WaterStorage.amount(level, water);
        ItemStack head = new ItemStack(pickHead());
        head.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(0.8f, ForgeQuality.Treatment.AIR));
        head.set(ModDataComponents.HEAT, new Heat(1100f, level.getGameTime()));
        ItemEntity entity = new ItemEntity(level, water.getX() + 0.5, water.getY() + 0.3, water.getZ() + 0.5, head);
        level.addFreshEntity(entity);
        helper.runAfterDelay(20, () -> {
            ItemStack quenched = entity.getItem();
            helper.assertFalse(quenched.has(ModDataComponents.HEAT), "Water takes the heat out at once");
            helper.assertTrue(ForgeQuality.of(quenched).treatment() == ForgeQuality.Treatment.QUENCHED,
                    "and the iron comes out hardened");
            int after = de.ipnats.hardwrought.water.WaterStorage.amount(level, water);
            helper.assertTrue(after == before - de.ipnats.hardwrought.smithing.SmithingEvents.QUENCH_MILLIBUCKETS,
                    "and the steam was a layer of the water: " + before + " -> " + after);
            entity.discard();
            for (BlockPos around : BlockPos.betweenClosed(water.offset(-1, -1, -1), water.offset(1, 0, 1))) {
                level.setBlock(around, Blocks.AIR.defaultBlockState(), 2);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void aHotHeadThrownIntoACauldronIsQuenchedAndTakesALevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        ItemStack head = new ItemStack(pickHead());
        head.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(0.8f, ForgeQuality.Treatment.AIR));
        head.set(ModDataComponents.HEAT, new Heat(1100f, level.getGameTime()));
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, head);
        entity.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(entity);
        helper.runAfterDelay(20, () -> {
            ItemStack quenched = entity.getItem();
            helper.assertFalse(quenched.has(ModDataComponents.HEAT), "A cauldron of water quenches too");
            helper.assertTrue(ForgeQuality.of(quenched).treatment() == ForgeQuality.Treatment.QUENCHED,
                    "and hardens the iron just the same");
            var state = level.getBlockState(pos);
            helper.assertTrue(state.is(Blocks.WATER_CAULDRON)
                            && state.getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL) == 2,
                    "and it loses one level to the steam: " + state);
            entity.discard();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void whatLiesInAForgeKeepsItsHeatAndCoalOnlyBurnsForMetal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, ModBlocks.FORGE.defaultBlockState());
        ForgeBlockEntity forge = (ForgeBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(forge.place(new ItemStack(Items.COAL, 3), 0), "Coal goes into the fuel place");
        helper.assertFalse(forge.canPlaceItem(ForgeBlockEntity.FIRST_METAL, new ItemStack(Items.COAL)),
                "Coal does not go where the metal lies");
        helper.assertFalse(forge.canPlaceItem(1, new ItemStack(Items.COAL)),
                "A forge on its own has a single fuel place");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(forge.getItem(0).getCount() == 3 && forge.burnTicks() == 0,
                    "An empty hearth does not burn its coal");
            ItemStack bar = new ItemStack(Items.IRON_INGOT);
            bar.set(ModDataComponents.HEAT, new Heat(1000f, level.getGameTime() - 2000));
            forge.setItem(ForgeBlockEntity.FIRST_METAL, bar);
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(forge.getItem(0).getCount() == 2 && forge.burnTicks() > 1600,
                        "With metal in it, one coal catches and burns four times as long as in a furnace");
                double celsius = Heat.of(forge.getItem(ForgeBlockEntity.FIRST_METAL), level.getGameTime());
                helper.assertTrue(celsius >= 999, "A piece lying in the forge does not cool: " + celsius);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                helper.succeed();
            });
        });
    }

    @GameTest
    public void eightForgesMakeASharedTwoByTwoByTwoForge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    level.setBlockAndUpdate(corner.offset(x, y, z), ModBlocks.FORGE.defaultBlockState());
                }
            }
        }

        ForgeMultiblock.Structure structure = ForgeMultiblock.getOrForm(level, corner.offset(1, 1, 1));
        helper.assertTrue(structure != null, "Eight forge blocks form a multiblock");
        helper.assertTrue(structure.size() == ForgeMultiblock.Size.SMALL
                        && structure.fuelSlots() == 1 && structure.metalSlots() == 4,
                "The 2x2x2 forge has one fuel place and four metal places");
        helper.assertTrue(level.getBlockState(corner.offset(1, 1, 1)).getValue(ForgeBlock.PART) == 8,
                "Each block knows its part of the big furnace model");
        ForgeBlockEntity controller = ForgeMultiblock.controller(level, structure);
        ForgeBlockEntity farPart = (ForgeBlockEntity) level.getBlockEntity(corner.offset(1, 1, 1));
        helper.assertTrue(farPart.working() == controller, "Any block of it opens the one shared forge");
        for (int i = 0; i < 4; i++) {
            ItemStack piece = new ItemStack(Items.RAW_IRON);
            piece.set(ModDataComponents.HEAT, new Heat(500f + i, 0));
            helper.assertTrue(controller.place(piece, controller.layout()), "Metal goes into its places");
        }
        helper.assertTrue(controller.place(new ItemStack(Items.COAL, 5), controller.layout()), "Coal goes in");
        helper.assertFalse(controller.canPlaceItem(ForgeBlockEntity.FIRST_METAL + 4, new ItemStack(Items.RAW_IRON)),
                "A fifth metal place does not exist");
        helper.assertFalse(farPart.canPlaceItem(ForgeBlockEntity.FIRST_METAL, new ItemStack(Items.RAW_IRON)),
                "The other blocks hold nothing of their own");

        var menu = new de.ipnats.hardwrought.smithing.ForgeMenu(0, helper.makeMockServerPlayerInLevel().getInventory(),
                controller, new net.minecraft.world.inventory.SimpleContainerData(3), controller.layout());
        long forgeSlots = menu.slots.stream().filter(slot -> slot.container == controller).count();
        helper.assertTrue(forgeSlots == 1 + 4, "Its screen shows one fuel and four metal places, got " + forgeSlots);

        int before = countItems(level, corner, 2);
        level.destroyBlock(corner.offset(1, 1, 1), false);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    if (level.getBlockEntity(corner.offset(x, y, z)) instanceof ForgeBlockEntity part) {
                        helper.assertTrue(ForgeMultiblock.getOrForm(level, part.getBlockPos()) == null,
                                "A broken forge falls apart into single hearths");
                        helper.assertTrue(part.getBlockState().getValue(ForgeBlock.PART) == 0,
                                "A single hearth is drawn as one again");
                    }
                }
            }
        }
        int after = countItems(level, corner, 2);
        helper.assertTrue(after == before,
                "Everything that lay in it is shared out among the hearths that are left: " + before + " / " + after);
        helper.succeed();
    }

    @GameTest
    public void twentySixForgesMakeAHollowThreeByThreeByThreeForge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos center = corner.offset(1, 1, 1);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos pos = corner.offset(x, y, z);
                    level.setBlockAndUpdate(pos, pos.equals(center) ? Blocks.AIR.defaultBlockState()
                            : ModBlocks.FORGE.defaultBlockState());
                }
            }
        }

        ForgeMultiblock.Structure structure = ForgeMultiblock.getOrForm(level, corner.offset(2, 2, 2));
        helper.assertTrue(structure != null, "The hollow forge shell forms a multiblock");
        helper.assertTrue(structure.size() == ForgeMultiblock.Size.LARGE && structure.members().size() == 26
                        && structure.fuelSlots() == 4 && structure.metalSlots() == 9,
                "The 3x3x3 shell of twenty-six blocks has four fuel and nine metal places");
        helper.assertTrue(level.getBlockState(center).isAir(), "Its middle remains completely free");
        ForgeBlockEntity controller = ForgeMultiblock.controller(level, structure);
        for (int i = 0; i < 9; i++) {
            ItemStack piece = new ItemStack(Items.RAW_IRON);
            piece.set(ModDataComponents.HEAT, new Heat(500f + i, 0));
            helper.assertTrue(controller.place(piece, controller.layout()), "Every metal place takes a piece");
        }
        ItemStack tenth = new ItemStack(Items.RAW_IRON);
        tenth.set(ModDataComponents.HEAT, new Heat(900f, 0));
        helper.assertFalse(controller.place(tenth, controller.layout()), "A tenth different piece does not fit");
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(controller.place(new ItemStack(Items.COAL, 64), controller.layout()), "Four fuel places");
        }
        controller.addFuel(1600);

        level.setBlockAndUpdate(center, Blocks.STONE.defaultBlockState());
        helper.assertFalse(ForgeMultiblock.matches(level, structure), "A filled middle is no forge chamber");
        ForgeMultiblock.dissolve(level, structure);
        int fuel = 0;
        int stacks = 0;
        for (BlockPos member : structure.members()) {
            ForgeBlockEntity part = (ForgeBlockEntity) level.getBlockEntity(member);
            fuel += part.burnTicks();
            stacks += (int) part.items().stream().filter(stack -> !stack.isEmpty()).count();
        }
        helper.assertTrue(fuel == 1600 && stacks == 13,
                "Falling apart keeps every stack and the fire once, got " + stacks + " / " + fuel);
        helper.succeed();
    }

    /** Items lying in forges in an edge-sized cube from this corner, plus any dropped nearby. */
    private static int countItems(ServerLevel level, BlockPos corner, int edge) {
        int count = 0;
        for (int x = 0; x < edge; x++) {
            for (int y = 0; y < edge; y++) {
                for (int z = 0; z < edge; z++) {
                    if (level.getBlockEntity(corner.offset(x, y, z)) instanceof ForgeBlockEntity forge) {
                        for (ItemStack stack : forge.items()) count += stack.getCount();
                    }
                }
            }
        }
        var box = new net.minecraft.world.phys.AABB(corner).inflate(edge + 2);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) count += item.getItem().getCount();
        return count;
    }

    private static ItemStack findInBag(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    @GameTest
    public void aBiggerHammerMovesMoreMetalPerBlow(GameTestHelper helper) {
        Mask empty = Mask.of(new long[4]);
        Mask full = square(0, 0, 15, 15);
        helper.assertTrue(Hammers.reach(new ItemStack(ModItems.WOODEN_HAMMER)) == 1
                        && Hammers.reach(new ItemStack(ModItems.HAMMER)) == 2
                        && Hammers.reach(new ItemStack(ModItems.IRON_HAMMER)) == 3,
                "Wood, stone and iron hammers reach one, two and three squares");
        helper.assertTrue(empty.strike(8, 8, full, 1).count() == 1, "A wooden blow moves one square");
        helper.assertTrue(empty.strike(8, 8, full, 2).count() == 4, "A stone blow moves two by two");
        helper.assertTrue(empty.strike(8, 8, full, 3).count() == 9, "An iron blow moves three by three");
        helper.assertTrue(Hammers.reachNeeded(ToolParts.part(ToolParts.SmithMetal.IRON, ToolParts.Part.HAMMER_HEAD)) == 2
                        && Hammers.reachNeeded(Items.IRON_INGOT) == 1,
                "An iron hammer head wants at least a stone hammer; a bar can be beaten out with wood");
        helper.succeed();
    }

    /** Strikes every square that is still wrong until the piece is finished. */
    private static void hammerUntilDone(GameTestHelper helper, ServerPlayer player, BlockPos anvil, Mask target) {
        for (int guard = 0; guard < 300; guard++) {
            ForgingState state = player.getOffhandItem().get(ModDataComponents.FORGING_STATE);
            if (state == null) return;
            Mask current = state.currentMask();
            int[] wrong = firstWrong(current, target, Hammers.reach(player.getMainHandItem()));
            helper.assertTrue(wrong != null, "An unfinished piece has something wrong with it");
            Forging.strike(player, anvil, wrong[0], wrong[1]);
        }
        helper.fail("The piece was never finished");
    }

    private static int[] firstWrong(Mask current, Mask target, int reach) {
        for (int y = 0; y < Mask.SIZE; y++) {
            for (int x = 0; x < Mask.SIZE; x++) {
                if (current.get(x, y) != target.get(x, y)) {
                    // Aim so the wrong square sits at the near edge of the blow, which covers as much of
                    // the wrong edge as the hammer can.
                    int in = -Hammers.from(reach);
                    return new int[] { Math.min(Mask.SIZE - 1, x + in), Math.min(Mask.SIZE - 1, y + in) };
                }
            }
        }
        return null;
    }

    private static Mask square(int x1, int y1, int x2, int y2) {
        Mask mask = Mask.empty();
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) mask = mask.with(x, y, true);
        }
        return mask;
    }

    @GameTest
    public void theBookShowsAWoodenHammerForEverythingButAHammerHead(GameTestHelper helper) {
        var axeHead = ToolParts.part(ToolParts.SmithMetal.IRON, ToolParts.Part.AXE_HEAD);
        var hammerHead = ToolParts.part(ToolParts.SmithMetal.IRON, ToolParts.Part.HAMMER_HEAD);
        for (var recipe : Smithing.worldRecipes()) {
            List<ItemStack> hammers = recipe.inputs().get(1);
            if (recipe.makes(axeHead)) {
                helper.assertTrue(hammers.getFirst().is(ModItems.WOODEN_HAMMER),
                        "An iron axe head is forged with a wooden hammer, and the book says so first");
            }
            if (recipe.makes(hammerHead)) {
                helper.assertTrue(hammers.stream().noneMatch(stack -> stack.is(ModItems.WOODEN_HAMMER))
                                && hammers.getFirst().is(ModItems.HAMMER),
                        "A hammer head wants a stone hammer at least");
            }
        }
        helper.succeed();
    }
}
