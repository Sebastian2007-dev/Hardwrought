package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.nether.AshGrove;
import de.ipnats.hardwrought.nether.Ashcork;
import de.ipnats.hardwrought.nether.CastObsidian;
import de.ipnats.hardwrought.nether.HeatShield;
import de.ipnats.hardwrought.nether.NetherCrossing;
import de.ipnats.hardwrought.nether.NetherOres;
import de.ipnats.hardwrought.survival.Thermal;
import net.fabricmc.fabric.api.biome.v1.NetherBiomes;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.equipment.ArmorType;
import de.ipnats.hardwrought.nether.PortalStability;
import de.ipnats.hardwrought.progression.BlockBreaking;
import de.ipnats.hardwrought.progression.BreakingVerdict;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Crossing into the Nether: unstable portals, safe landing, and a start over that can be begun by hand. */
public final class NetherGameTests {
    @GameTest
    public void netherrackGivesWayToBareHandsSlowly(GameTestHelper helper) {
        BreakingVerdict verdict = BlockBreaking.verdict(ItemStack.EMPTY, Blocks.NETHERRACK.defaultBlockState());
        helper.assertTrue(verdict == BreakingVerdict.DIGGABLE, "Netherrack is dug by hand, got " + verdict);
        helper.assertTrue(verdict.speedFactor() < 1, "and slowly");
        helper.assertTrue(BlockBreaking.handful(Blocks.NETHERRACK.defaultBlockState()).is(Items.NETHERRACK),
                "and comes away whole, to build the first shelter of");
        helper.succeed();
    }

    @GameTest
    public void theAshGroveIsANetherBiomeWhereWaterBoilsClean(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        var grove = biomes.get(AshGrove.KEY);
        helper.assertTrue(grove.isPresent(), "The Ash Grove biome loads");
        helper.assertTrue(NetherBiomes.canGenerateInNether(AshGrove.KEY), "and generates in the Nether");
        helper.assertTrue(grove.get().is(BiomeTags.IS_NETHER), "counts as the Nether, for fortresses and bastions");
        helper.assertTrue(grove.get().is(AshGrove.BOILS_WATER), "and its water is always boiled");
        helper.assertTrue(grove.get().value().getBaseTemperature() < 2.0f, "It is cooler than the rest of the Nether");
        helper.assertTrue(helper.getLevel().registryAccess().lookupOrThrow(Registries.FEATURE).get(Ashcork.TREE).isPresent(),
                "and the ashcork tree it grows loads");
        var features = grove.get().value().getGenerationSettings().features().stream()
                .flatMap(step -> step.stream()).map(feature -> feature.unwrapKey().orElseThrow().identifier().toString()).toList();
        helper.assertTrue(features.contains("hardwrought:ash_grove_ground"), "Its ground is made: Nether grass, and water for lava");
        helper.assertFalse(features.stream().anyMatch(id -> id.contains("spring") || id.contains("magma") || id.contains("fire")),
                "and no lava spring, magma or fire is placed in it: " + features);
        helper.succeed();
    }

    @GameTest
    public void netherGrassCarriesAshcorkAndDiesBackWhenCovered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos grass = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(grass, AshGrove.NETHER_GRASS.defaultBlockState(), 2);
        helper.assertTrue(Ashcork.SAPLING.defaultBlockState().canSurvive(level, grass.above()), "An ashcork sapling takes on Nether grass");
        helper.assertTrue(BlockBreaking.verdict(ItemStack.EMPTY, AshGrove.NETHER_GRASS.defaultBlockState()) == BreakingVerdict.DIGGABLE,
                "and the turf is dug by hand like the netherrack under it");
        for (var turf : java.util.List.of(Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM)) {
            helper.assertTrue(BlockBreaking.verdict(ItemStack.EMPTY, turf.defaultBlockState()) == BreakingVerdict.DIGGABLE
                            && BlockBreaking.handful(turf.defaultBlockState()).is(Items.NETHERRACK),
                    turf + " is dug by hand too, and comes up as netherrack");
        }
        for (var wart : java.util.List.of(Blocks.WARPED_WART_BLOCK, Blocks.NETHER_WART_BLOCK)) {
            helper.assertTrue(BlockBreaking.verdict(ItemStack.EMPTY, wart.defaultBlockState()).breaks(), wart + " gives way to a hand");
        }
        level.setBlock(grass.above(), Blocks.NETHERRACK.defaultBlockState(), 2);
        AshGrove.NETHER_GRASS.defaultBlockState().randomTick(level, grass, level.getRandom());
        helper.assertTrue(level.getBlockState(grass).is(Blocks.NETHERRACK), "Covered over, it dies back to netherrack");
        helper.succeed();
    }

    @GameTest
    public void ashcorkGrowsOnNetherrackAndGivesBarkArmourAgainstTheHeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sapling = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(sapling.below(), Blocks.NETHERRACK.defaultBlockState(), 2);
        helper.assertTrue(Ashcork.SAPLING.defaultBlockState().canSurvive(level, sapling), "An ashcork sapling takes on netherrack");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(HeatShield.of(player) == 0, "Bare, nothing keeps the heat off");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Ashcork.armor(ArmorType.HELMET)));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Ashcork.armor(ArmorType.CHESTPLATE)));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Ashcork.armor(ArmorType.LEGGINGS)));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Ashcork.armor(ArmorType.BOOTS)));
        double full = HeatShield.of(player);
        helper.assertTrue(Math.abs(full - 1.0) < 1e-9, "A full set of bark covers the body, got " + full);
        double comfort = Thermal.comfort(false);
        double nether = 40;
        double felt = HeatShield.shield(nether, full, false);
        helper.assertTrue(felt < nether && felt > comfort, "It keeps most of the Nether's heat off, but not all: " + felt);
        helper.assertTrue(HeatShield.shield(-10, full, false) == -10, "and does nothing at all for the cold");
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        helper.assertTrue(net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingSafeArmor(player),
                "Piglins leave a wearer of bark alone, as one in gold");
        helper.succeed();
    }

    @GameTest
    public void waterOnLavaCastsObsidianTooBrittleForAPortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lava = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(lava.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(lava, Blocks.LAVA.defaultBlockState(), 2);
        level.setBlockAndUpdate(lava.east(), Blocks.WATER.defaultBlockState());
        level.neighborChanged(lava, Blocks.WATER, null);
        helper.assertTrue(level.getBlockState(lava).is(CastObsidian.BLOCK),
                "Water on a lava source casts obsidian, got " + level.getBlockState(lava));
        helper.assertFalse(CastObsidian.BLOCK.defaultBlockState().is(BlockTags.NETHER_PORTAL_FRAME), "which is no portal frame");
        helper.assertTrue(CastObsidian.BLOCK.defaultBlockState().is(BlockTags.NEEDS_DIAMOND_TOOL),
                "and wants what obsidian wants to mine it: steel or better");
        // The water simulation is shared by every test running at once: leave no water behind in it.
        de.ipnats.hardwrought.water.WaterStorage.setAmount(level, lava.east(), 0);
        level.setBlock(lava.east(), Blocks.AIR.defaultBlockState(), 2);
        helper.succeed();
    }

    @GameTest
    public void theNetherHasOrdinaryOresAndAStonePickFromBlackstone(GameTestHelper helper) {
        // Which biomes they go into is the Nether's biome source, and the test world's Nether is flat; so
        // only that the features load can be asked here.
        var placed = helper.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        for (NetherOres.Ore ore : NetherOres.Ore.values()) {
            helper.assertTrue(placed.get(net.minecraft.resources.ResourceKey.create(Registries.PLACED_FEATURE,
                    de.ipnats.hardwrought.Hardwrought.id("ore_nether_" + ore.id() + "_ore"))).isPresent(),
                    "The Nether " + ore.id() + " ore's vein loads");
        }
        ItemStack flint = new ItemStack(de.ipnats.hardwrought.core.registry.ModItems.FLINT_PICKAXE);
        ItemStack stone = new ItemStack(de.ipnats.hardwrought.core.registry.ModItems.STONE_PICKAXE);
        var coal = NetherOres.Ore.COAL.block().defaultBlockState();
        var iron = NetherOres.Ore.IRON.block().defaultBlockState();
        helper.assertTrue(coal.is(BlockTags.ORES) && iron.is(BlockTags.ORES), "Nether ores are ores");
        helper.assertTrue(flint.isCorrectToolForDrops(coal), "A flint pick takes Nether coal");
        helper.assertFalse(flint.isCorrectToolForDrops(iron), "but not Nether iron, as overworld iron");
        helper.assertTrue(stone.isCorrectToolForDrops(iron), "which wants a stone pick");
        helper.assertTrue(new ItemStack(NetherOres.BLACKSTONE_PIECE).is(net.minecraft.tags.TagKey.create(Registries.ITEM,
                de.ipnats.hardwrought.Hardwrought.id("rock_pieces"))), "and a stone pick is made of blackstone pieces as well");
        helper.assertTrue("hardwrought:iron".equals(String.valueOf(
                de.ipnats.hardwrought.metallurgy.Smelting.materialOf(NetherOres.Ore.IRON.block().asItem())))
                || "minecraft:iron".equals(String.valueOf(
                de.ipnats.hardwrought.metallurgy.Smelting.materialOf(NetherOres.Ore.IRON.block().asItem()))),
                "Nether iron ore melts as iron: " + de.ipnats.hardwrought.metallurgy.Smelting.materialOf(NetherOres.Ore.IRON.block().asItem()));
        helper.succeed();
    }

    @GameTest
    public void metalOnMagmaHeatsToIronsWorkingHeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos magma = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(magma, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        ItemEntity iron = new ItemEntity(level, magma.getX() + 0.5, magma.getY() + 1.0, magma.getZ() + 0.5, new ItemStack(Items.RAW_IRON));
        iron.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.addFreshEntity(iron);
        for (int i = 0; i < 60; i++) de.ipnats.hardwrought.nether.MagmaHeating.warm(level, iron);
        var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(level.getServer());
        double[] range = de.ipnats.hardwrought.smithing.Smithing.workingRange(Items.RAW_IRON, runtime.materials());
        double heat = de.ipnats.hardwrought.smithing.Heat.of(iron.getItem(), level.getGameTime());
        helper.assertTrue(heat >= range[0] && heat <= range[1],
                "Raw iron left on magma comes up to its working heat, " + range[0] + " to " + range[1] + ": " + heat);
        iron.discard();
        helper.succeed();
    }

    @GameTest
    public void aBackpackAndGlovesGoThroughAnUnstablePortal(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(de.ipnats.hardwrought.core.registry.ModItems.STARTER_BACKPACK));
        player.getInventory().setItem(1, new ItemStack(de.ipnats.hardwrought.core.registry.ModItems.SMITHING_GLOVES));
        player.getInventory().setItem(2, new ItemStack(Items.IRON_INGOT, 3));
        NetherCrossing.leaveBehind(helper.getLevel(), player, helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(player.getInventory().getItem(0).is(de.ipnats.hardwrought.core.registry.ModItems.STARTER_BACKPACK)
                        && player.getInventory().getItem(1).is(de.ipnats.hardwrought.core.registry.ModItems.SMITHING_GLOVES),
                "The backpack and the gloves come along");
        helper.assertTrue(player.getInventory().getItem(2).isEmpty(), "and everything else stays behind");
        helper.succeed();
    }

    @GameTest
    public void noPortalIsStableWithoutStabilisers(GameTestHelper helper) {
        BlockPos portal = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(portal, Blocks.NETHER_PORTAL.defaultBlockState(), 2);
        helper.assertFalse(PortalStability.isStable(helper.getLevel(), portal), "A bare obsidian frame is unstable");
        helper.succeed();
    }

    @GameTest
    public void twoRunningStabilisersAgainstTheFrameHoldAPortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Set as they stand, without the shape updates that would break a portal whose frame is not yet whole.
        int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
        // A portal two wide and three high, in the X plane, its frame of obsidian around it.
        BlockPos corner = helper.absolutePos(new BlockPos(2, 2, 2));
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                boolean inner = x >= 0 && x <= 1 && y >= 0 && y <= 2;
                level.setBlock(corner.offset(x, y, 0), inner
                        ? Blocks.NETHER_PORTAL.defaultBlockState().setValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS,
                                net.minecraft.core.Direction.Axis.X)
                        : Blocks.OBSIDIAN.defaultBlockState(), flags);
            }
        }
        var running = de.ipnats.hardwrought.nether.PortalStabilizers.PROVISIONAL.defaultBlockState()
                .setValue(de.ipnats.hardwrought.nether.PortalStabilizers.ACTIVE, true);
        var cold = running.setValue(de.ipnats.hardwrought.nether.PortalStabilizers.ACTIVE, false);
        level.setBlock(corner.offset(-2, 0, 0), running, flags);
        helper.assertFalse(PortalStability.isStable(level, corner.offset(1, 1, 0)), "One stabiliser is not enough");
        level.setBlock(corner.offset(3, 1, 0), cold, flags);
        helper.assertFalse(PortalStability.isStable(level, corner.offset(1, 1, 0)), "nor one that is not running");
        level.setBlock(corner.offset(3, 1, 0), running, flags);
        helper.assertTrue(PortalStability.isStable(level, corner), "Two running against the frame make it stable");
        helper.assertTrue(PortalStability.isStable(level, corner.offset(1, 2, 0)), "the whole portal, whichever block is asked");
        level.setBlock(corner.offset(3, 1, 0), Blocks.AIR.defaultBlockState(), flags);
        level.setBlock(corner.offset(0, -1, 1), running, flags);
        helper.assertTrue(PortalStability.isStable(level, corner), "Against a face of the frame counts as well");
        helper.succeed();
    }

    @GameTest
    public void aProvisionalStabiliserRunsOnlyOnHeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        var block = de.ipnats.hardwrought.nether.PortalStabilizers.PROVISIONAL;
        level.setBlock(pos, block.defaultBlockState(), 2);
        de.ipnats.hardwrought.nether.PortalStabilizers.Provisional.feel(level.getBlockState(pos), level, pos);
        helper.assertFalse(level.getBlockState(pos).getValue(de.ipnats.hardwrought.nether.PortalStabilizers.ACTIVE),
                "In the cool of the overworld it does not run");
        level.setBlock(pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        de.ipnats.hardwrought.nether.PortalStabilizers.Provisional.feel(level.getBlockState(pos), level, pos);
        helper.assertTrue(level.getBlockState(pos).getValue(de.ipnats.hardwrought.nether.PortalStabilizers.ACTIVE),
                "On magma it takes the heat and runs");
        helper.assertTrue(level.getBlockState(pos).getLightEmission() > 0, "and glows while it does");
        helper.succeed();
    }

    @GameTest
    public void landingNeedsSafeGroundAndRoomToStand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(feet.below(), Blocks.NETHERRACK.defaultBlockState(), 2);
        helper.assertTrue(NetherCrossing.standable(level, feet), "Netherrack under, air above: a place to stand");
        level.setBlock(feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        helper.assertFalse(NetherCrossing.standable(level, feet), "Not on magma");
        level.setBlock(feet.below(), Blocks.LAVA.defaultBlockState(), 2);
        helper.assertFalse(NetherCrossing.standable(level, feet), "Not on lava");
        level.setBlock(feet.below(), Blocks.NETHERRACK.defaultBlockState(), 2);
        level.setBlock(feet.above(), Blocks.NETHERRACK.defaultBlockState(), 2);
        helper.assertFalse(NetherCrossing.standable(level, feet), "Not without room for the head");
        helper.succeed();
    }

    @GameTest
    public void anUnstableCrossingKeepsEverythingUntilItsOwnerComesBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos portal = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.getInventory().setItem(3, new ItemStack(Items.IRON_INGOT, 12));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));

        NetherCrossing.leaveBehind(level, player, portal);

        helper.assertTrue(player.getInventory().isEmpty() && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                "Nothing, armour included, goes through an unstable portal");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(portal).inflate(3)).isEmpty(),
                "and nothing is dropped to be lost: it is kept");
        helper.assertTrue(player.getAttached(NetherCrossing.LEFT_BEHIND).get(level.dimension()).size() == 2,
                "Both stacks are kept for the side they were left on");

        helper.assertTrue(NetherCrossing.takeBack(player), "Back on that side, they are given back");
        helper.assertTrue(player.getInventory().getItem(3).is(Items.IRON_INGOT)
                        && player.getInventory().getItem(3).getCount() == 12,
                "each into the slot it came from");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "armour back on");
        helper.assertFalse(player.hasAttached(NetherCrossing.LEFT_BEHIND), "and nothing is kept twice");
        helper.assertFalse(NetherCrossing.takeBack(player), "A second time there is nothing to give back");
        helper.succeed();
    }
}
