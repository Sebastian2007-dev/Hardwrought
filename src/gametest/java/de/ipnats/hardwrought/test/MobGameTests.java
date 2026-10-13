package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.mobs.Breaching;
import de.ipnats.hardwrought.mobs.CreeperLeap;
import de.ipnats.hardwrought.mobs.HeartWood;
import de.ipnats.hardwrought.mobs.MobEquipment;
import de.ipnats.hardwrought.mobs.Perception;
import de.ipnats.hardwrought.mobs.SleepDebt;
import de.ipnats.hardwrought.mobs.TreeHeartBlock;
import de.ipnats.hardwrought.mobs.TreeHearts;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Mob specification, milestone "Mobs I": tools that decide what a zombie breaks through, the
 * creeper's leap, phantoms that follow sleep debt, mobs that hear and see rather than know.
 */
public final class MobGameTests {
    @GameTest
    public void zombieToolsGrowCommonerAndBetterWithTimeAndDepth(GameTestHelper helper) {
        helper.assertTrue(MobEquipment.toolChance(0, 64) < MobEquipment.toolChance(20, 64),
                "A month-old world has more armed zombies than a new one");
        helper.assertTrue(MobEquipment.toolChance(0, -40) > MobEquipment.toolChance(0, 64),
                "and the deep has more than the surface");
        helper.assertTrue(MobEquipment.toolChance(10_000, -60) <= 0.25, "but never most of them");
        helper.assertTrue(MobEquipment.tool(MobEquipment.Tool.AXE, 0) == ModItems.FLINT_HATCHET
                        && MobEquipment.tool(MobEquipment.Tool.AXE, 3) == Items.IRON_AXE,
                "Tools run from flint to iron");
        helper.succeed();
    }

    @GameTest
    public void whatAZombieBreaksIsWhatItsToolIsFor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
        helper.assertTrue(Breaching.rate(level, pos, planks, ItemStack.EMPTY) == 0,
                "A bare-handed zombie does not get through planks");
        helper.assertTrue(Breaching.rate(level, pos, Blocks.DIRT.defaultBlockState(), ItemStack.EMPTY) > 0,
                "but it digs through loose soil");
        helper.assertTrue(Breaching.rate(level, pos, planks, new ItemStack(Items.IRON_AXE)) > 0
                        && Breaching.rate(level, pos, stone, new ItemStack(Items.IRON_AXE)) == 0,
                "An axe opens wood, not stone");
        helper.assertTrue(Breaching.rate(level, pos, stone, new ItemStack(ModItems.FLINT_PICKAXE)) > 0
                        && Breaching.rate(level, pos, iron, new ItemStack(ModItems.FLINT_PICKAXE)) == 0,
                "A flint pickaxe gets through stone, never through an iron wall");
        helper.assertTrue(Breaching.rate(level, pos, planks, new ItemStack(Items.IRON_AXE))
                        > Breaching.rate(level, pos, planks, new ItemStack(ModItems.FLINT_HATCHET)),
                "A better tool is quicker");
        helper.assertTrue(Breaching.rate(level, pos, Blocks.CHEST.defaultBlockState(), new ItemStack(Items.IRON_AXE)) == 0,
                "and nothing holding a block entity is ever broken");
        helper.succeed();
    }

    /** A player in a plank box, a zombie outside it: with an axe it gets in, bare-handed it does not. */
    @GameTest(maxTicks = 900)
    public void anAxeZombieBreaksIntoAPlankBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos inside = helper.absolutePos(new BlockPos(5, 2, 2));
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = inside.offset(x, y, z);
                    boolean hollow = x == 0 && z == 0 && (y == 0 || y == 1);
                    level.setBlockAndUpdate(pos, hollow ? Blocks.AIR.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
                }
            }
        }
        floor(helper);
        var player = villager(helper, new BlockPos(5, 2, 2));
        Zombie armed = zombie(helper, new BlockPos(1, 1, 2), new ItemStack(Items.IRON_AXE));
        armed.setTarget(player);
        helper.succeedWhen(() -> {
            int left = 0;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (level.getBlockState(inside.offset(x, y, z)).is(Blocks.OAK_PLANKS)) left++;
                    }
                }
            }
            armed.setTarget(player);
            helper.assertTrue(left < 34, "The zombie with the axe cuts its way into the box: " + left + " planks left; target="
                    + armed.getTarget() + " path=" + armed.getNavigation().getPath() + " at=" + armed.blockPosition()
                    + " player=" + player.blockPosition() + " alive=" + armed.isAlive());
        });
    }

    @GameTest(maxTicks = 300)
    public void aBareHandedZombieCannotCutPlanks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos inside = helper.absolutePos(new BlockPos(5, 2, 2));
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    boolean hollow = x == 0 && z == 0 && (y == 0 || y == 1);
                    level.setBlockAndUpdate(inside.offset(x, y, z),
                            hollow ? Blocks.AIR.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
                }
            }
        }
        floor(helper);
        var player = villager(helper, new BlockPos(5, 2, 2));
        Zombie bare = zombie(helper, new BlockPos(1, 1, 2), ItemStack.EMPTY);
        bare.setTarget(player);
        helper.runAfterDelay(250, () -> {
            int left = 0;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (level.getBlockState(inside.offset(x, y, z)).is(Blocks.OAK_PLANKS)) left++;
                    }
                }
            }
            helper.assertTrue(left == 34, "Without a tool the box holds: " + left + " planks left");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void anIgnitedCreeperLeapsAtItsTarget(GameTestHelper helper) {
        floor(helper);
        var player = villager(helper, new BlockPos(5, 1, 2));
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(2, 1, 2));
        creeper.setTarget(player);
        creeper.ignite();
        helper.succeedWhen(() -> {
            helper.assertTrue(((CreeperLeap.Leaper) creeper).hardwrought$leapt(), "The creeper leaps before it goes off: swell="
                    + creeper.getSwelling(1f) + " target=" + creeper.getTarget() + " dist=" + creeper.distanceTo(player)
                    + " ground=" + creeper.onGround() + " alive=" + creeper.isAlive());
            // Its work is done; the blast itself is vanilla's and would only wreck the test area.
            creeper.discard();
        });
    }

    @GameTest
    public void sleepDebtNotWakefulnessBringsPhantoms(GameTestHelper helper) {
        double oneBad = SleepDebt.afterNight(0, 0.3);
        helper.assertTrue(SleepDebt.phantomPressure(oneBad) == 0, "One bad night brings no phantoms");
        double threeBad = SleepDebt.afterNight(SleepDebt.afterNight(oneBad, 0.3), 0.3);
        helper.assertTrue(SleepDebt.phantomPressure(threeBad) > 72_000, "Several do");
        helper.assertTrue(SleepDebt.afterNight(threeBad, 0.9) < threeBad, "and a good night pays some of it off");
        helper.assertTrue(SleepDebt.phantomPressure(0) == 0, "Staying awake, with no bad sleep, brings none");
        helper.succeed();
    }

    @GameTest
    public void noiseIsMuffledByWallsAndHeardByIdleMobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 from = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 2, 2)));
        Vec3 to = Vec3.atCenterOf(helper.absolutePos(new BlockPos(6, 2, 2)));
        double open = Perception.reachThrough(level, from, to, 16);
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 2, 2)), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(4, 2, 2)), Blocks.STONE.defaultBlockState());
        double walled = Perception.reachThrough(level, from, to, 16);
        helper.assertTrue(open == 16 && Math.abs(walled - 16 * 0.55 * 0.55) < 1e-9,
                "Two walls between muffle a sound twice: " + open + " -> " + walled);

        Zombie listener = zombie(helper, new BlockPos(6, 1, 5), ItemStack.EMPTY);
        BlockPos mined = helper.absolutePos(new BlockPos(1, 1, 5));
        // The test's mock player is always in creative mode, and mobs ignore those entirely.
        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(creative.isCreative(), "The mock player is in creative mode");
        level.gameEvent(creative, GameEvent.BLOCK_DESTROY, mined);
        helper.assertTrue(Perception.noise(listener) == null, "A player in creative mode is not heard");
        Perception.alert(level, Vec3.atCenterOf(mined), Perception.loudness(GameEvent.BLOCK_DESTROY));
        helper.assertTrue(Perception.noise(listener) != null, "Mining by a survival player is, and the zombie will go to look");
        helper.succeed();
    }

    @GameTest
    public void creakingHeartsGrowInEveryForest(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        double forest = TreeHearts.chance(biomes.getOrThrow(Biomes.FOREST));
        double dark = TreeHearts.chance(biomes.getOrThrow(Biomes.DARK_FOREST));
        helper.assertTrue(forest > 0, "A heart can grow in an ordinary forest");
        helper.assertTrue(dark > forest, "and more readily in a dark one");
        helper.assertTrue(dark < 0.1, "but less readily than in the pale garden, where vanilla grows one tree in ten");
        helper.assertTrue(TreeHearts.chance(biomes.getOrThrow(Biomes.PALE_GARDEN)) == 0,
                "The pale garden keeps vanilla's hearts");
        helper.assertTrue(TreeHearts.chance(biomes.getOrThrow(Biomes.DESERT)) == 0, "A desert grows none");
        helper.succeed();
    }

    @GameTest
    public void aTreeHeartWakesBetweenLogsOfItsOwnWood(GameTestHelper helper) {
        BlockPos below = new BlockPos(1, 1, 1), heart = below.above(), above = heart.above();
        helper.setBlock(below, Blocks.BIRCH_LOG);
        helper.setBlock(above, Blocks.BIRCH_LOG);
        BlockState birchHeart = ModBlocks.TREE_CREAKING_HEART.defaultBlockState().setValue(TreeHeartBlock.WOOD, HeartWood.BIRCH);
        helper.setBlock(heart, birchHeart);
        helper.assertTrue(CreakingHeartBlock.hasRequiredLogs(birchHeart, helper.getLevel(), helper.absolutePos(heart)),
                "A birch heart lives between birch logs");
        helper.setBlock(above, Blocks.PALE_OAK_LOG);
        helper.assertFalse(CreakingHeartBlock.hasRequiredLogs(birchHeart, helper.getLevel(), helper.absolutePos(heart)),
                "but not with pale oak above it");
        helper.assertTrue(HeartWood.of(Blocks.PALE_OAK_LOG.defaultBlockState()) == null,
                "Pale oak is left to vanilla's own heart");
        helper.succeed();
    }

    @GameTest
    public void aHurtOakHeartBleedsResinAlongOak(GameTestHelper helper) {
        BlockPos below = new BlockPos(2, 1, 2), heart = below.above(), above = heart.above();
        helper.setBlock(below, Blocks.OAK_LOG);
        helper.setBlock(above, Blocks.OAK_LOG);
        helper.setBlock(heart, ModBlocks.TREE_CREAKING_HEART.defaultBlockState()
                .setValue(TreeHeartBlock.WOOD, HeartWood.OAK)
                .setValue(CreakingHeartBlock.STATE, net.minecraft.world.level.block.state.properties.CreakingHeartState.AWAKE));
        var creaking = helper.spawnWithNoFreeWill(EntityTypes.CREAKING, new BlockPos(4, 1, 4));
        net.minecraft.world.level.block.entity.CreakingHeartBlockEntity entity = helper.getBlockEntity(heart,
                net.minecraft.world.level.block.entity.CreakingHeartBlockEntity.class);
        entity.setCreakingInfo(creaking);
        entity.creakingHurt();
        int resin = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 0, 1), new BlockPos(3, 4, 3))) {
            if (helper.getBlockState(pos).is(Blocks.RESIN_CLUMP)) resin++;
        }
        helper.assertTrue(resin > 0, "Resin creeps out along the oak around the heart");
        helper.succeed();
    }

    @GameTest
    public void aWitchPicksHerPotionForTheTarget(GameTestHelper helper) {
        var dying = villager(helper, new BlockPos(1, 2, 1));
        dying.setHealth(4);
        helper.assertTrue(de.ipnats.hardwrought.mobs.WitchTactics.choose(net.minecraft.world.item.alchemy.Potions.POISON, dying)
                        .is(net.minecraft.world.item.alchemy.Potions.HARMING), "A target nearly dead gets harming");
        var armored = villager(helper, new BlockPos(3, 2, 1));
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            armored.setItemSlot(slot, new ItemStack(switch (slot) {
                case HEAD -> Items.IRON_HELMET;
                case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS;
                default -> Items.IRON_BOOTS;
            }));
        }
        // Armor counts from the tick after it is put on.
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(de.ipnats.hardwrought.mobs.WitchTactics.choose(net.minecraft.world.item.alchemy.Potions.HARMING, armored)
                            .is(net.minecraft.world.item.alchemy.Potions.WEAKNESS), "One in heavy armor gets weakness: " + armored.getArmorValue());
            helper.assertTrue(de.ipnats.hardwrought.mobs.WitchTactics.choose(net.minecraft.world.item.alchemy.Potions.HEALING, armored)
                            .is(net.minecraft.world.item.alchemy.Potions.HEALING), "Healing an ally stays vanilla's choice");
            helper.succeed();
        });
    }

    @GameTest
    public void slimesShrugOffBlowsAndHeavyHittersShakeOffStagger(GameTestHelper helper) {
        var slime = helper.spawnWithNoFreeWill(EntityTypes.SLIME, new BlockPos(1, 2, 1));
        var magma = helper.spawnWithNoFreeWill(EntityTypes.MAGMA_CUBE, new BlockPos(3, 2, 1));
        helper.assertTrue(de.ipnats.hardwrought.mobs.MobTraits.incomingFactor(slime, null,
                        de.ipnats.hardwrought.combat.CombatDamageType.BLUNT) < 1
                        && de.ipnats.hardwrought.mobs.MobTraits.incomingFactor(slime, null,
                        de.ipnats.hardwrought.combat.CombatDamageType.SLASH) > 1,
                "A slime takes less from blows and more from cuts");
        helper.assertTrue(de.ipnats.hardwrought.mobs.MobTraits.incomingFactor(magma, null,
                de.ipnats.hardwrought.combat.CombatDamageType.BLUNT) == 1, "a magma cube is not a slime in this");
        var brute = helper.spawnWithNoFreeWill(EntityTypes.PIGLIN_BRUTE, new BlockPos(5, 2, 1));
        helper.assertTrue(de.ipnats.hardwrought.mobs.MobTraits.staggerTicks(brute, 40) < 40
                        && de.ipnats.hardwrought.mobs.MobTraits.staggerTicks(slime, 40) == 40,
                "A piglin brute shakes a stagger off, an ordinary mob does not");
        helper.succeed();
    }

    @GameTest
    public void aBlazeHeatsTheAirAroundIt(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(new BlockPos(1, 1, 2));
        player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        double before = de.ipnats.hardwrought.mobs.MobHeat.nearby(player);
        var blaze = helper.spawnWithNoFreeWill(EntityTypes.BLAZE, new BlockPos(3, 1, 2));
        double after = de.ipnats.hardwrought.mobs.MobHeat.nearby(player);
        helper.assertTrue(after > before + 5, "A blaze two blocks off warms the air: " + before + " -> " + after);
        blaze.discard();
        helper.succeed();
    }

    @GameTest
    public void windBlowsOutFlamesAndCandles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        BlockPos fire = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos candle = helper.absolutePos(new BlockPos(3, 1, 2));
        level.setBlockAndUpdate(fire, Blocks.FIRE.defaultBlockState());
        level.setBlockAndUpdate(candle, Blocks.CANDLE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
        de.ipnats.hardwrought.mobs.MobWorld.wind(level, Vec3.atCenterOf(fire));
        helper.assertFalse(level.getBlockState(fire).is(Blocks.FIRE), "A gust puts out a weak fire");
        helper.assertFalse(level.getBlockState(candle).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),
                "and blows out a candle");
        helper.succeed();
    }

    @GameTest
    public void skeletonsAimWorseInTheDarkAndFarOff(GameTestHelper helper) {
        floor(helper);
        var skeleton = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new BlockPos(1, 1, 2));
        var near = villager(helper, new BlockPos(4, 1, 2));
        double close = de.ipnats.hardwrought.mobs.SkeletonAim.spread(skeleton, near, de.ipnats.hardwrought.mobs.SkeletonAim.Shot.NORMAL);
        near.teleportTo(near.getX() + 20, near.getY(), near.getZ());
        double far = de.ipnats.hardwrought.mobs.SkeletonAim.spread(skeleton, near, de.ipnats.hardwrought.mobs.SkeletonAim.Shot.NORMAL);
        helper.assertTrue(far > close, "Far off, the aim spreads: " + close + " -> " + far);
        helper.assertTrue(de.ipnats.hardwrought.mobs.SkeletonAim.spread(skeleton, near, de.ipnats.hardwrought.mobs.SkeletonAim.Shot.ACCURATE)
                        < far, "an accurate shot is tighter");
        helper.succeed();
    }

    @GameTest
    public void aHuntedMobPanicsAndFightsInsteadOfFleeing(GameTestHelper helper) {
        floor(helper);
        var target = villager(helper, new BlockPos(5, 1, 2));
        var archer = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new BlockPos(2, 1, 2));
        archer.setTarget(target);
        helper.assertTrue(de.ipnats.hardwrought.mobs.Panic.startFlight(archer), "A first flight is a flight");
        de.ipnats.hardwrought.mobs.Panic.endFlight(archer);
        helper.assertTrue(de.ipnats.hardwrought.mobs.Panic.startFlight(archer), "so is a second");
        de.ipnats.hardwrought.mobs.Panic.endFlight(archer);
        helper.assertFalse(de.ipnats.hardwrought.mobs.Panic.startFlight(archer),
                "but the third in a short while is one too many");
        helper.assertTrue(de.ipnats.hardwrought.mobs.Panic.panicking(archer), "and it turns to fight");

        var spider = helper.spawnWithNoFreeWill(EntityTypes.CAVE_SPIDER, new BlockPos(2, 1, 4));
        spider.setTarget(target);
        de.ipnats.hardwrought.mobs.Panic.startFlight(spider);
        spider.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(target), 1);
        helper.assertTrue(de.ipnats.hardwrought.mobs.Panic.panicking(spider), "Hit while running, it turns on its pursuer");
        helper.succeed();
    }

    @GameTest
    public void staminaRegenerationIsBrewedFromHoney(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (String recipe : new String[] {"potion_awkward_honey_bottle", "potion_stamina_regeneration_redstone",
                "potion_stamina_regeneration_glowstone_dust", "potion_stamina_regeneration_gunpowder"}) {
            var key = net.minecraft.resources.ResourceKey.create(Registries.RECIPE,
                    de.ipnats.hardwrought.Hardwrought.id("brewing/" + recipe));
            helper.assertTrue(server.getRecipeManager().byKey(key).isPresent(), "The brewing recipe " + recipe + " is loaded");
        }
        var potion = de.ipnats.hardwrought.core.registry.ModEffects.STRONG_STAMINA_REGENERATION_POTION.value();
        helper.assertTrue(potion.getEffects().getFirst().getAmplifier() == 1
                        && potion.getEffects().getFirst().getEffect().equals(de.ipnats.hardwrought.core.registry.ModEffects.STAMINA_REGENERATION),
                "Glowstone makes it stamina regeneration II");
        helper.succeed();
    }

    /** A target that stays one; the mock player is in creative mode and so ignored by mobs. */
    private static net.minecraft.world.entity.LivingEntity villager(GameTestHelper helper, BlockPos relative) {
        var villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, relative);
        villager.setPersistenceRequired();
        return villager;
    }

    /** Stone under the whole test area: it has no ground of its own to stand on. */
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 5; z++) {
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static Zombie zombie(GameTestHelper helper, BlockPos at, ItemStack tool) {
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, tool);
        // A helmet, or the test sun burns it before it has done anything.
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setPersistenceRequired();
        return zombie;
    }
}
