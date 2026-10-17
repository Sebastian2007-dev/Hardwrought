package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModEffects;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.health.Disease;
import de.ipnats.hardwrought.health.Diseases;
import de.ipnats.hardwrought.health.Health;
import de.ipnats.hardwrought.survival.PlayerVitals;
import de.ipnats.hardwrought.water.WaterQuality;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Catching a disease, how it runs, what it does to a body, and what helps. */
public final class HealthGameTests {
    /** A player in the level, connected; mock players are creative, so the survival tick never sees them. */
    @SuppressWarnings("removal")
    private static ServerPlayer patient(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Diseases.set(player, Health.NONE);
        return player;
    }

    /** Seconds of illness with the body as it is given, resting. */
    private static void live(ServerPlayer player, PlayerVitals vitals, int seconds) {
        for (int second = 0; second < seconds; second++) Diseases.tick(player, vitals, true, second);
    }

    private static double load(ServerPlayer player, Disease disease) {
        Health.Infection infection = Diseases.health(player).infection(disease);
        return infection == null ? 0 : infection.load();
    }

    @GameTest
    public void aStomachBugRunsItsCourseAndIsThenKnown(GameTestHelper helper) {
        ServerPlayer player = patient(helper);
        PlayerVitals well = PlayerVitals.defaults();
        Diseases.infect(player, Disease.GASTRO, 10);
        helper.assertTrue(Diseases.health(player).infection(Disease.GASTRO).stage() == Disease.Stage.INCUBATING,
                "Caught, it takes hold unnoticed");
        helper.assertFalse(Diseases.sick(player), "and nothing is felt yet");
        live(player, well, Disease.GASTRO.incubation() + 5);
        helper.assertTrue(Diseases.sick(player), "A minute later it is felt");
        helper.assertTrue(player.hasEffect(ModEffects.THIRST), "and it costs water");
        helper.assertTrue(Diseases.hungerFactor(player) > 1 && Diseases.fatigueFactor(player) > 1,
                "A sick body is hungrier and wearier");
        helper.assertFalse(Diseases.restless(player), "and lying in bed does not make it restless");

        double highest = 0;
        for (int second = 0; second < 3000 && Diseases.health(player).infection(Disease.GASTRO) != null; second++) {
            Diseases.tick(player, well, true, second);
            highest = Math.max(highest, load(player, Disease.GASTRO));
        }
        helper.assertTrue(Diseases.health(player).infection(Disease.GASTRO) == null,
                "Looked after, the body throws it off by itself, at worst at " + highest);
        helper.assertTrue(highest < Diseases.CRITICAL, "without it ever turning critical: " + highest);
        helper.assertTrue(Diseases.health(player).known().contains(Disease.GASTRO), "Once through it, it can be named");
        Diseases.infect(player, Disease.GASTRO, 10);
        helper.assertTrue(Diseases.health(player).infection(Disease.GASTRO) == null,
                "and for a while it cannot be caught again");
        helper.succeed();
    }

    @GameTest
    public void teaAndRestHelp(GameTestHelper helper) {
        ServerPlayer untreated = patient(helper);
        ServerPlayer treated = patient(helper);
        PlayerVitals well = PlayerVitals.defaults();
        Diseases.infect(untreated, Disease.GASTRO, 10);
        Diseases.infect(treated, Disease.GASTRO, 10);
        treated.addEffect(new MobEffectInstance(ModEffects.CHAMOMILE, 20 * 600, 0));
        live(untreated, well, 400);
        live(treated, well, 400);
        helper.assertTrue(load(treated, Disease.GASTRO) < load(untreated, Disease.GASTRO) - 10,
                "Chamomile tea settles a stomach bug: " + load(treated, Disease.GASTRO) + " against "
                        + load(untreated, Disease.GASTRO));

        ServerPlayer starved = patient(helper);
        Diseases.infect(starved, Disease.GASTRO, 10);
        live(starved, well.withHydration(10), 400);
        helper.assertTrue(load(starved, Disease.GASTRO) > load(untreated, Disease.GASTRO),
                "and a body short of water fights it worse");
        helper.succeed();
    }

    @GameTest
    public void aColdIsCaughtInTheColdAndBringsAFever(GameTestHelper helper) {
        ServerPlayer player = patient(helper);
        PlayerVitals well = PlayerVitals.defaults();
        PlayerVitals chilled = new PlayerVitals(100, 100, well.nutrition(), 15, 35.0, 0.8, 0, 0, 0);
        live(player, chilled, 130);
        helper.assertTrue(Diseases.health(player).infection(Disease.COLD) != null, "Two minutes cold and wet catch a cold");
        live(player, chilled, Disease.COLD.incubation() + 30);
        helper.assertTrue(Diseases.fever(player) > 0.8, "Felt, it brings a fever: " + Diseases.fever(player));
        double fever = Diseases.fever(player);
        player.addEffect(new MobEffectInstance(ModEffects.WILLOW_BARK, 20 * 300, 0));
        helper.assertTrue(Diseases.fever(player) < fever / 2, "and willow bark brings it down");
        double cold = load(player, Disease.COLD);
        live(player, chilled, 120);
        ServerPlayer warm = patient(helper);
        Diseases.infect(warm, Disease.COLD, cold);
        Diseases.set(warm, Diseases.health(warm).with(Disease.COLD,
                new Health.Infection(Disease.COLD, Diseases.health(player).infection(Disease.COLD).age() - 120, cold, cold)));
        live(warm, well, 120);
        helper.assertTrue(load(player, Disease.COLD) > load(warm, Disease.COLD),
                "Left in the cold and wet it grows worse than in the warm");
        helper.succeed();
    }

    @GameTest
    public void badWaterAndRawMeatUpsetTheStomach(GameTestHelper helper) {
        // Drinking itself is for players the survival tick sees; what it rolls is checked here.
        helper.assertTrue(WaterQuality.SWAMP.illnessRisk() > 0.3 && WaterQuality.PURIFIED.illnessRisk() == 0,
                "Swamp water often gives a stomach bug, purified water never");
        ServerPlayer drinker = patient(helper);
        Diseases.drankBad(drinker, de.ipnats.hardwrought.survival.SurvivalSystem.BAD_WATER_LOAD);
        helper.assertTrue(Diseases.health(drinker).infection(Disease.GASTRO) != null, "A bad drink gives one");

        ServerPlayer eater = patient(helper);
        for (int bite = 0; bite < 40 && Diseases.health(eater).infection(Disease.GASTRO) == null; bite++) {
            Diseases.ate(eater, new ItemStack(Items.CHICKEN));
        }
        helper.assertTrue(Diseases.health(eater).infection(Disease.GASTRO) != null, "and so does raw chicken");
        ServerPlayer cooked = patient(helper);
        for (int bite = 0; bite < 40; bite++) Diseases.ate(cooked, new ItemStack(Items.COOKED_CHICKEN));
        helper.assertTrue(Diseases.health(cooked).infection(Disease.GASTRO) == null, "but not cooked chicken");
        helper.succeed();
    }

    @GameTest
    public void aBiteOpensAWoundAndABandageKeepsItClean(GameTestHelper helper) {
        ServerPlayer player = patient(helper);
        var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(helper.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.EVENT);
        de.ipnats.hardwrought.health.Wounds.hurt(player, helper.getLevel().damageSources().mobAttack(zombie), 2.0f);
        var open = player.getEffect(ModEffects.OPEN_WOUND);
        helper.assertTrue(open != null && open.getAmplifier() == 1, "A bite leaves a dirty wound open");

        var effect = ModEffects.OPEN_WOUND.value();
        for (int check = 0; check < 2000 && Diseases.health(player).infection(Disease.WOUND) == null; check++) {
            effect.applyEffectTick(helper.getLevel(), player, 1);
        }
        helper.assertTrue(Diseases.health(player).infection(Disease.WOUND) != null, "Left open, it becomes infected");

        ServerPlayer bound = patient(helper);
        de.ipnats.hardwrought.health.Wounds.hurt(bound, helper.getLevel().damageSources().mobAttack(zombie), 2.0f);
        ((de.ipnats.hardwrought.health.MedicineItem) ModItems.BANDAGE).apply(helper.getLevel(), bound);
        helper.assertTrue(!bound.hasEffect(ModEffects.OPEN_WOUND) && bound.hasEffect(ModEffects.BANDAGED),
                "A bandage closes it");
        for (int check = 0; check < 2000; check++) effect.applyEffectTick(helper.getLevel(), bound, 1);
        helper.assertTrue(Diseases.health(bound).infection(Disease.WOUND) == null, "and nothing gets into a bound wound");
        de.ipnats.hardwrought.health.Wounds.hurt(bound, helper.getLevel().damageSources().mobAttack(zombie), 2.0f);
        helper.assertTrue(!bound.hasEffect(ModEffects.BANDAGED) && bound.hasEffect(ModEffects.OPEN_WOUND),
                "A new bite tears the bandage off");
        if (zombie != null) zombie.discard();
        helper.succeed();
    }

    @GameTest
    public void woundsComeInThreeLevelsAndFireLeavesNone(GameTestHelper helper) {
        var level = helper.getLevel();
        var skeleton = net.minecraft.world.entity.EntityTypes.SKELETON.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        var blaze = net.minecraft.world.entity.EntityTypes.BLAZE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        ServerPlayer burnt = patient(helper);
        de.ipnats.hardwrought.health.Wounds.hurt(burnt, level.damageSources().fireball(
                net.minecraft.world.entity.EntityTypes.SMALL_FIREBALL.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT), blaze), 6.0f);
        helper.assertFalse(burnt.hasEffect(ModEffects.OPEN_WOUND), "Set on fire by a blaze, a player is burnt, not wounded");

        int[] levels = {
                de.ipnats.hardwrought.health.Wounds.level(5.0f, false),
                de.ipnats.hardwrought.health.Wounds.level(8.0f, false),
                de.ipnats.hardwrought.health.Wounds.level(13.0f, false),
                de.ipnats.hardwrought.health.Wounds.level(2.0f, true)};
        helper.assertTrue(levels[0] == 0 && levels[1] == 1 && levels[2] == 2, "Harder blows, worse wounds: " + java.util.Arrays.toString(levels));
        helper.assertTrue(levels[3] == 1, "and a bite is a level worse than a blow as hard");
        ServerPlayer grazed = patient(helper);
        de.ipnats.hardwrought.health.Wounds.hurt(grazed, level.damageSources().mobAttack(skeleton), 5.0f);
        helper.assertTrue(grazed.getEffect(ModEffects.OPEN_WOUND).getAmplifier() == 0, "A blow of five is a graze");

        double graze = de.ipnats.hardwrought.health.Wounds.infectionOdds(0);
        double deep = de.ipnats.hardwrought.health.Wounds.infectionOdds(1);
        double severe = de.ipnats.hardwrought.health.Wounds.infectionOdds(2);
        helper.assertTrue(graze < 0.08 && deep > 0.15 && deep < 0.4 && severe > 0.5 && severe < 0.8,
                "Left alone, a graze is rarely infected, a deep wound sometimes, a severe one mostly: "
                        + graze + " / " + deep + " / " + severe);
        if (skeleton != null) skeleton.discard();
        if (blaze != null) blaze.discard();
        helper.succeed();
    }

    @GameTest
    public void eachKindOfHarmLeavesItsOwnAilment(GameTestHelper helper) {
        var level = helper.getLevel();
        var sources = level.damageSources();
        ServerPlayer player = patient(helper);
        de.ipnats.hardwrought.health.Ailments.harmed(player, sources.onFire(), 1.0f);
        var burn = player.getEffect(ModEffects.BURN);
        helper.assertTrue(burn != null && burn.getAmplifier() == 0 && !player.hasEffect(ModEffects.OPEN_WOUND),
                "A moment on fire is a first-degree burn, and no wound");
        for (int i = 0; i < 20; i++) de.ipnats.hardwrought.health.Ailments.harmed(player, sources.onFire(), 1.0f);
        helper.assertTrue(player.getEffect(ModEffects.BURN).getAmplifier() == 2, "a long while on fire a third-degree one");
        de.ipnats.hardwrought.health.Ailments.harmed(player, sources.freeze(), 2.0f);
        helper.assertTrue(player.hasEffect(ModEffects.FROSTBITE), "Frost leaves frostbite");
        de.ipnats.hardwrought.health.Ailments.harmed(player, sources.drown(), 2.0f);
        helper.assertTrue(player.hasEffect(ModEffects.WATER_IN_LUNGS), "Drowning leaves water in the lungs");
        de.ipnats.hardwrought.health.Ailments.harmed(player, sources.magic(), 1.0f);
        helper.assertFalse(player.hasEffect(ModEffects.DRAIN), "The sting of spirit on a wound drains nothing");
        de.ipnats.hardwrought.health.Ailments.harmed(player, sources.wither(), 1.0f);
        helper.assertTrue(player.hasEffect(ModEffects.DRAIN), "but the wither does");
        helper.assertTrue(de.ipnats.hardwrought.health.Ailments.recoveryFactor(player) < 1.0
                        && de.ipnats.hardwrought.health.Ailments.hungerFactor(player) > 1.0,
                "and the drain slows breath and quickens hunger");
        helper.succeed();
    }

    @GameTest
    public void adrenalineHoldsBackWhatAFightCostsUntilItIsOver(GameTestHelper helper) {
        ServerPlayer player = patient(helper);
        player.setHealth(20.0f);
        // Three blows taken in a fight: it runs high, and part of each comes straight back.
        for (int blow = 0; blow < 3; blow++) {
            player.setHealth(player.getHealth() - 4.0f);
            de.ipnats.hardwrought.survival.Adrenaline.taken(player, 4.0f);
        }
        float felt = player.getHealth();
        helper.assertTrue(felt > 20.0f - 12.0f, "In the fight, less of the twelve damage is felt: " + felt);
        helper.assertTrue(de.ipnats.hardwrought.survival.Adrenaline.fighting(player), "and the player is fighting");
        double cost = de.ipnats.hardwrought.survival.Adrenaline.maskStamina(player, 10.0);
        helper.assertTrue(cost < 10.0, "Stamina spent in it is felt less too: " + cost);
        de.ipnats.hardwrought.survival.Adrenaline.settleForTesting(player);
        helper.assertTrue(player.hasEffect(ModEffects.ADRENALINE), "It shows, while it runs");
        Diseases.infect(player, Disease.WOUND, 20);
        helper.assertTrue(Diseases.health(player).infection(Disease.WOUND) == null, "and nothing takes hold in the fight");
        float hunger = de.ipnats.hardwrought.survival.Adrenaline.maskHunger(player, 1.0f);
        helper.assertTrue(hunger < 0.75f && hunger > 0, "Hunger is spent less in it: " + hunger);
        player.getFoodData().setFoodLevel(0);
        float before = player.getHealth();
        player.hurtServer(helper.getLevel(), player.damageSources().starve(), 1.0f);
        helper.assertTrue(player.getHealth() == before, "and an empty stomach does not hurt");
        player.getFoodData().setFoodLevel(20);
        // The fight over: the account is paid, down to half a heart and no further.
        de.ipnats.hardwrought.survival.Adrenaline.endFightForTesting(player);
        for (int second = 0; second < 30; second++) de.ipnats.hardwrought.survival.Adrenaline.settleForTesting(player);
        helper.assertTrue(player.getHealth() <= 20.0f - 12.0f + 0.01f && player.getHealth() >= 1.0f,
                "Afterwards it is all felt: " + player.getHealth());
        helper.assertFalse(player.hasEffect(ModEffects.ADRENALINE), "and the rush is gone");
        helper.succeed();
    }

    @GameTest
    public void fatigueRunHighIsFeltInStages(GameTestHelper helper) {
        helper.assertTrue(de.ipnats.hardwrought.survival.Tiredness.stage(40) == -1
                        && de.ipnats.hardwrought.survival.Tiredness.stage(55) == 0
                        && de.ipnats.hardwrought.survival.Tiredness.stage(75) == 1
                        && de.ipnats.hardwrought.survival.Tiredness.stage(90) == 2,
                "Tired from 50, exhausted from 70, overtired from 85");
        ServerPlayer player = patient(helper);
        double rested = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        de.ipnats.hardwrought.survival.Tiredness.apply(player, 90);
        var tired = player.getEffect(ModEffects.TIRED);
        helper.assertTrue(tired != null && tired.getAmplifier() == 2, "Overtired, it shows at its third level");
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < rested,
                "and the player is slower for it");
        de.ipnats.hardwrought.survival.Tiredness.apply(player, 10);
        helper.assertFalse(player.hasEffect(ModEffects.TIRED), "Rested, it is gone");
        helper.succeed();
    }

    @GameTest
    public void goldenApplesAreNoFoodButGiveStamina(GameTestHelper helper) {
        for (var apple : java.util.List.of(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE)) {
            ItemStack stack = new ItemStack(apple);
            var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
            helper.assertTrue(food != null && food.nutrition() == 0 && food.saturation() == 0, apple + " fills no stomach");
            int expected = apple == Items.GOLDEN_APPLE ? 0 : 2;
            boolean stamina = stack.get(net.minecraft.core.component.DataComponents.CONSUMABLE).onConsumeEffects().stream()
                    .filter(effect -> effect instanceof net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect)
                    .flatMap(effect -> ((net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect) effect).effects().stream())
                    .anyMatch(effect -> effect.getEffect().equals(ModEffects.STAMINA_REGENERATION) && effect.getAmplifier() == expected);
            helper.assertTrue(stamina, apple + " gives stamina regeneration " + (expected + 1));
        }
        helper.assertTrue(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).getMaxStackSize() == 8, "The enchanted apple stacks to eight");
        var recipes = helper.getLevel().getServer().getRecipeManager();
        helper.assertTrue(recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                de.ipnats.hardwrought.Hardwrought.id("enchanted_golden_apple"))).isPresent(), "and is made again from blocks of gold");
        helper.succeed();
    }

    @GameTest
    public void medicineCleansAndCures(GameTestHelper helper) {
        ServerPlayer player = patient(helper);
        Diseases.infect(player, Disease.WOUND, 40);
        ((de.ipnats.hardwrought.health.MedicineItem) ModItems.ANTISEPTIC).apply(helper.getLevel(), player);
        helper.assertTrue(load(player, Disease.WOUND) == 40 - de.ipnats.hardwrought.health.MedicineItem.ANTISEPTIC_CLEANS,
                "Antiseptic cleans much of an infection out of a wound: " + load(player, Disease.WOUND));
        ((de.ipnats.hardwrought.health.MedicineItem) ModItems.SPIRIT).apply(helper.getLevel(), player);
        helper.assertTrue(load(player, Disease.WOUND) == 40 - de.ipnats.hardwrought.health.MedicineItem.ANTISEPTIC_CLEANS
                        - de.ipnats.hardwrought.health.MedicineItem.SPIRIT_CLEANS, "spirit some");

        PlayerVitals well = PlayerVitals.defaults();
        ServerPlayer untreated = patient(helper);
        ServerPlayer treated = patient(helper);
        Diseases.infect(untreated, Disease.COLD, 30);
        Diseases.infect(treated, Disease.COLD, 30);
        ((de.ipnats.hardwrought.health.MedicineItem) ModItems.ANTIBIOTICS).apply(helper.getLevel(), treated);
        live(untreated, well, 400);
        live(treated, well, 400);
        helper.assertTrue(load(treated, Disease.COLD) < load(untreated, Disease.COLD) - 10,
                "Antibiotics fight it far better: " + load(treated, Disease.COLD) + " against " + load(untreated, Disease.COLD));
        ((de.ipnats.hardwrought.health.MedicineItem) ModItems.FEVER_PILLS).apply(helper.getLevel(), untreated);
        var willow = untreated.getEffect(ModEffects.WILLOW_BARK);
        helper.assertTrue(willow != null && willow.getAmplifier() == 1, "Fever pills are willow bark, stronger");

        var batch = de.ipnats.hardwrought.chemistry.StillRecipes.batch(new ItemStack(ModItems.MASH), helper.getLevel().getRandom());
        helper.assertTrue(batch != null && batch.bottles() == 1 && batch.products().getFirst().is(ModItems.SPIRIT),
                "The still turns mash and a bottle into spirit");
        var reactor = de.ipnats.hardwrought.materials.ChemMachine.CHEMICAL_REACTOR.recipes();
        for (var made : new net.minecraft.world.item.Item[] {ModItems.ANTISEPTIC, ModItems.FEVER_PILLS, ModItems.ANTIBIOTICS}) {
            helper.assertTrue(reactor.stream().anyMatch(recipe -> recipe.outputs().getFirst().is(made)),
                    "The chemical reactor makes " + made);
        }
        helper.succeed();
    }

    @GameTest
    public void aCriticalIllnessCostsHealthWithoutWakingTheSleeper(GameTestHelper helper) {
        ServerPlayer player = patient(helper);
        Diseases.set(player, Health.NONE.with(Disease.COLD,
                new Health.Infection(Disease.COLD, Disease.COLD.incubation() + 10, 90, 90)));
        player.setHealth(10);
        // At its worst, a second on which it costs health.
        Diseases.tick(player, PlayerVitals.defaults(), true, 20);
        helper.assertTrue(player.getHealth() == 9, "A critical illness costs health: " + player.getHealth());
        helper.assertTrue(player.hurtTime == 0, "from within, not as a blow, so a sleeper sleeps on");
        helper.succeed();
    }

    @GameTest
    public void eatingPastFullIsKeptDigestedFirstAndWeighs(GameTestHelper helper) {
        ServerPlayer player = patient(helper);
        de.ipnats.hardwrought.survival.Overeating.clear(player);
        player.removeAttached(de.ipnats.hardwrought.survival.Overeating.STUFFED);
        player.getFoodData().setFoodLevel(18);
        de.ipnats.hardwrought.survival.Overeating.ate(player, 8);
        helper.assertTrue(de.ipnats.hardwrought.survival.Overeating.stuffed(player) == 6,
                "What does not fit in the bar is kept: " + de.ipnats.hardwrought.survival.Overeating.stuffed(player));
        player.getFoodData().setFoodLevel(15);
        de.ipnats.hardwrought.survival.Overeating.tick(player);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 16 && de.ipnats.hardwrought.survival.Overeating.stuffed(player) == 5,
                "and digested into the bar first");

        player.getFoodData().setFoodLevel(20);
        de.ipnats.hardwrought.survival.Overeating.ate(player, 20);
        helper.assertTrue(de.ipnats.hardwrought.survival.Overeating.stuffed(player) == de.ipnats.hardwrought.survival.Overeating.MOST,
                "No more than the bar again fits");
        helper.assertFalse(de.ipnats.hardwrought.survival.Overeating.roomFor(player), "and then nothing more goes in");
        double speed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        de.ipnats.hardwrought.survival.Overeating.tick(player);
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < speed,
                "As full as a body gets is slower");
        helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH) == 0
                        && player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) == 0,
                "cannot jump and cannot climb a step");
        helper.assertTrue(de.ipnats.hardwrought.survival.Overeating.staminaFactor(player) > 1, "and every effort costs more");
        de.ipnats.hardwrought.survival.Overeating.clear(player);
        player.removeAttached(de.ipnats.hardwrought.survival.Overeating.STUFFED);
        helper.succeed();
    }

    @GameTest
    public void aTeaIsDrunkAndWorks(GameTestHelper helper) {
        ServerPlayer player = patient(helper);
        ItemStack tea = new ItemStack(ModItems.WILLOW_TEA);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tea);
        ItemStack left = tea.getItem().finishUsingItem(tea, helper.getLevel(), player);
        // A mock player is creative and keeps the tea; anyone else is left the empty bowl.
        helper.assertTrue(left.is(Items.BOWL) || player.getAbilities().instabuild, "Drunk, the bowl is left");
        helper.assertTrue(player.hasEffect(ModEffects.WILLOW_BARK), "and the bark works");
        helper.assertTrue(Diseases.health(player).infection(Disease.GASTRO) == null, "A tea is boiled, so it is clean");
        helper.succeed();
    }
}
