package de.ipnats.hardwrought.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.combat.ArmorCoverage;
import de.ipnats.hardwrought.combat.ArmorProfile;
import de.ipnats.hardwrought.combat.CombatDamageType;
import de.ipnats.hardwrought.combat.CombatEvent;
import de.ipnats.hardwrought.combat.DamageSplit;
import de.ipnats.hardwrought.combat.ShieldProfile;
import de.ipnats.hardwrought.combat.WeaponProfile;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.CombatSnapshotPayload;
import de.ipnats.hardwrought.core.registry.ItemWeightDefinition;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class CombatGameTests {
    private static final Identifier IRON_SWORD = Identifier.withDefaultNamespace("iron_sword");
    private static final Identifier MACE = Identifier.withDefaultNamespace("mace");
    private static final Identifier AXE = Identifier.withDefaultNamespace("iron_axe");
    private static final Identifier SHIELD = Identifier.withDefaultNamespace("shield");

    @GameTest
    public void damageSplitNormalizesAndValidates(GameTestHelper helper) {
        var split = new DamageSplit(65, 30, 5);
        helper.assertTrue(Math.abs(split.slash() - 0.65) < 1.0E-9, "Shares are normalized to a fraction of one hit");
        helper.assertTrue(Math.abs(split.share(CombatDamageType.SLASH) + split.share(CombatDamageType.PIERCE)
                + split.share(CombatDamageType.BLUNT) - 1.0) < 1.0E-9, "Physical shares add up to the whole hit");
        helper.assertTrue(split.share(CombatDamageType.FIRE) == 0, "Only physical types are part of the split");
        helper.assertTrue(split.dominantType() == CombatDamageType.SLASH, "The largest share names the hit");
        helper.assertTrue(new DamageSplit(0, 0, 1).dominantType() == CombatDamageType.BLUNT, "Blunt-only weapons resolve");
        expectFailure(() -> new DamageSplit(0, 0, 0));
        expectFailure(() -> new DamageSplit(-1, 1, 0));
        expectFailure(() -> new DamageSplit(Double.NaN, 1, 0));
        helper.assertTrue(DamageSplit.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"slash\":2,\"blunt\":2}")).getOrThrow().slash() == 0.5,
                "A profile may be written with weights instead of fractions");
        helper.succeed();
    }

    @GameTest
    public void combatDamageTypeCodecAndClassification(GameTestHelper helper) {
        helper.assertTrue(CombatDamageType.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"pierce\"")).getOrThrow() == CombatDamageType.PIERCE, "Names round trip");
        helper.assertTrue(CombatDamageType.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"kinetic\"")).error().isPresent(), "Unknown damage types are rejected");
        helper.assertTrue(CombatDamageType.SLASH.physical() && !CombatDamageType.MAGIC.physical(),
                "Only slash, pierce and blunt meet the armor material");
        var sources = helper.getLevel().damageSources();
        helper.assertTrue(CombatDamageType.classify(sources.inFire()) == CombatDamageType.FIRE, "Fire is classified");
        helper.assertTrue(CombatDamageType.classify(sources.freeze()) == CombatDamageType.COLD, "Freezing is cold");
        helper.assertTrue(CombatDamageType.classify(sources.fall()) == CombatDamageType.BLUNT,
                "Unclassified impacts count as blunt instead of inventing a type");
        helper.assertTrue(CombatDamageType.classify(sources.magic()) == CombatDamageType.MAGIC, "Magic is classified");

        // Damage that takes the breath away is not a blunt impact.
        helper.assertTrue(CombatDamageType.classify(sources.drown()) == CombatDamageType.SUFFOCATION,
                "Drowning is suffocation");
        helper.assertTrue(CombatDamageType.classify(sources.inWall()) == CombatDamageType.SUFFOCATION,
                "Being crushed inside a block is suffocation");
        helper.assertTrue(CombatDamageType.classify(hardwroughtSource(helper, "bad_air"))
                        == CombatDamageType.SUFFOCATION,
                "Spent air is reported as suffocation, not as a blunt hit");
        helper.assertTrue(CombatDamageType.classify(hardwroughtSource(helper, "smoke"))
                        == CombatDamageType.SUFFOCATION,
                "Smoke inhalation is reported as suffocation");
        helper.assertFalse(CombatDamageType.SUFFOCATION.physical(),
                "Suffocation never meets the armor material, so no armor reduces it");
        helper.assertTrue(CombatDamageType.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("\"suffocation\"")).getOrThrow() == CombatDamageType.SUFFOCATION,
                "The new type round trips like every other");
        helper.succeed();
    }

    private static DamageSource hardwroughtSource(GameTestHelper helper, String path) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                de.ipnats.hardwrought.Hardwrought.id(path));
        return new DamageSource(helper.getLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(key));
    }

    @GameTest
    public void weaponProfileCodecRejectsInvalidValues(GameTestHelper helper) {
        var profile = WeaponProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"items":["minecraft:iron_sword"],"damage":{"slash":0.7,"pierce":0.3},
                 "stamina_cost":0.9,"armor_penetration":0.15,"impact":2.5,"handling":1.6,
                 "reach_bonus_blocks":0.3,"attack_speed_factor":1.0}
                """)).getOrThrow();
        helper.assertTrue(profile.items().equals(List.of(IRON_SWORD)), "A profile claims the items it lists");
        var encoded = WeaponProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow();
        helper.assertTrue(WeaponProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().equals(profile),
                "Weapon values survive serialization");
        expectFailure(() -> profile.items().add(MACE));
        String[] invalid = {
                "{\"items\":[\"minecraft:iron_sword\"],\"damage\":{\"slash\":1},\"armor_penetration\":1.5}",
                "{\"items\":[\"minecraft:iron_sword\"],\"damage\":{\"slash\":1},\"handling\":0.1}",
                "{\"items\":[\"minecraft:iron_sword\"],\"damage\":{}}",
                "{\"damage\":{\"slash\":1}}"
        };
        for (String json : invalid) {
            helper.assertTrue(WeaponProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(),
                    "Invalid weapon values must fail the reload: " + json);
        }
        helper.assertTrue(WeaponProfile.IMPROVISED.damage().dominantType() == CombatDamageType.BLUNT,
                "Items without a profile stay usable as an improvised blunt impact");
        helper.succeed();
    }

    @GameTest
    public void weaponControlAndMinimumRange(GameTestHelper helper) {
        var spear = WeaponProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"items":["minecraft:trident"],"damage":{"pierce":1},"handling":1.1,"minimum_reach_blocks":2.0}
                """)).getOrThrow();
        helper.assertTrue(spear.closeQuartersFactor(3.0) == 1.0, "Outside the minimum range a spear is unaffected");
        helper.assertTrue(spear.closeQuartersFactor(0.5) < 0.8, "Inside the minimum range a spear loses power");
        helper.assertTrue(spear.closeQuartersFactor(0.0) >= 0.40, "The penalty stays bounded");
        helper.assertTrue(WeaponProfile.IMPROVISED.closeQuartersFactor(0.1) == 1.0,
                "Weapons without a minimum range never suffer the penalty");

        var handy = new WeaponProfile(List.of(), new DamageSplit(1, 0, 0), 1, 0, 1, 2.0, 0, 1, 0);
        var clumsy = new WeaponProfile(List.of(), new DamageSplit(1, 0, 0), 1, 0, 1, 0.5, 0, 1, 0);
        helper.assertTrue(handy.controlFactor(false, false) == 1.0, "A grounded, walking attacker keeps full control");
        helper.assertTrue(handy.controlFactor(true, false) > clumsy.controlFactor(true, false),
                "High handling loses less of a sprinting swing");
        helper.assertTrue(clumsy.controlFactor(false, true) >= 0.35, "The control penalty stays bounded");
        helper.succeed();
    }

    @GameTest
    public void armorMaterialAnswersDamageType(GameTestHelper helper) {
        var plate = new ArmorCoverage(0.70, 0.50, 0.30, 0.88, 0.80);
        helper.assertTrue(plate.resistance(CombatDamageType.SLASH) > plate.resistance(CombatDamageType.BLUNT),
                "Plate answers a cut better than an impact");
        helper.assertTrue(plate.resistance(CombatDamageType.FIRE) == 0,
                "Armor material has no defined answer to non-physical damage yet");
        helper.assertTrue(Math.abs(plate.effectiveResistance(CombatDamageType.SLASH, 0.5) - 0.35) < 1.0E-9,
                "Armor penetration removes part of the material advantage");
        helper.assertTrue(plate.effectiveResistance(CombatDamageType.SLASH, 1.0) == 0,
                "Full penetration removes the material advantage entirely");
        helper.assertTrue(plate.effectiveResistance(CombatDamageType.SLASH, -5) == 0.70,
                "An out-of-range penetration is clamped, not trusted");
        helper.assertTrue(ArmorCoverage.NONE.resistance(CombatDamageType.SLASH) == 0, "Unarmored means no resistance");
        expectFailure(() -> new ArmorCoverage(Double.NaN, 0, 0, 0, 0));
        var profile = ArmorProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"items":["minecraft:iron_chestplate"],"slash_resistance":0.7,"pierce_resistance":0.5,
                 "blunt_resistance":0.3,"stamina_drain":0.22,"insulation":0.2}
                """)).getOrThrow();
        helper.assertTrue(profile.insulation().orElseThrow() == 0.2, "Armor may define its own insulation");
        helper.assertTrue(ArmorProfile.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"items\":[\"minecraft:iron_helmet\"],\"slash_resistance\":1.2}"))
                .error().isPresent(), "Armor cannot claim more than the allowed resistance");
        helper.succeed();
    }

    @GameTest
    public void wornArmorProducesCoverage(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        var combat = CoreLifecycle.require(helper.getLevel().getServer()).combat();
        helper.assertTrue(combat.armorCoverage(zombie).resistance(CombatDamageType.SLASH) == 0,
                "An unarmored defender has no material resistance");
        equipPlate(zombie);
        var coverage = combat.armorCoverage(zombie);
        helper.assertTrue(Math.abs(coverage.resistance(CombatDamageType.SLASH) - 0.70) < 1.0E-6,
                "A full set covers the whole body with its material resistance");
        helper.assertTrue(coverage.resistance(CombatDamageType.SLASH) > coverage.resistance(CombatDamageType.BLUNT),
                "Plate answers a cut better than an impact");
        helper.assertTrue(coverage.staminaDrain() > 0, "Heavy armor costs stamina while moving");

        zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        helper.assertTrue(combat.armorCoverage(zombie).resistance(CombatDamageType.SLASH) < 0.70,
                "An uncovered slot lowers the average resistance");
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        helper.assertTrue(combat.armorCoverage(zombie).insulation() > 0,
                "A worn piece without a profile still insulates by the documented default");
        zombie.discard();
        helper.succeed();
    }

    @GameTest
    public void bundledProfilesDescribeTheWeaponClasses(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        var sword = runtime.weaponProfiles().get(IRON_SWORD);
        var mace = runtime.weaponProfiles().get(MACE);
        var axe = runtime.weaponProfiles().get(AXE);
        var shield = runtime.shieldProfiles().get(SHIELD);
        helper.assertTrue(sword != null && mace != null && axe != null && shield != null,
                "Bundled combat profiles are loaded through the datapack listeners");
        helper.assertTrue(sword.damage().dominantType() == CombatDamageType.SLASH, "A sword cuts");
        helper.assertTrue(mace.damage().dominantType() == CombatDamageType.BLUNT, "A mace crushes");
        helper.assertTrue(mace.armorPenetration() > sword.armorPenetration(),
                "Section 30: the mace is the answer to an armored target");
        helper.assertTrue(axe.impact() > shield.guardBreakImpact(),
                "Section 30: an axe is useful against shields, so its impact breaks a round shield guard");
        helper.assertTrue(sword.impact() < shield.guardBreakImpact(), "A sword does not break a guard by itself");
        helper.assertTrue(runtime.armorProfiles().get(Identifier.withDefaultNamespace("iron_chestplate")) != null,
                "Bundled armor profiles are loaded");
        expectFailure(() -> runtime.weaponProfiles().clear());
        helper.assertTrue(runtime.itemWeights().get(Identifier.withDefaultNamespace("iron_chestplate")) == 9.0,
                "Grouped item masses are loaded per item");
        helper.assertTrue(runtime.itemWeights().get(de.ipnats.hardwrought.Hardwrought.id("filled_waterskin")) == 1.2,
                "The single-item mass format stays valid");
        helper.succeed();
    }

    @GameTest
    public void itemWeightFormatsAndBounds(GameTestHelper helper) {
        var single = ItemWeightDefinition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"item\":\"minecraft:iron_helmet\",\"kilograms\":2.5}")).getOrThrow();
        helper.assertTrue(single.item().equals(Identifier.withDefaultNamespace("iron_helmet"))
                && single.kilograms() == 2.5, "The single-item format is unchanged");
        var group = ItemWeightDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"weights":{"minecraft:iron_helmet":2.5,"minecraft:iron_chestplate":9.0}}
                """)).getOrThrow();
        helper.assertTrue(group.weights().size() == 2, "A group file may carry several different masses");
        expectFailure(group::kilograms);
        helper.assertTrue(ItemWeightDefinition.CODEC.parse(JsonOps.INSTANCE,
                        ItemWeightDefinition.CODEC.encodeStart(JsonOps.INSTANCE, group).getOrThrow())
                .getOrThrow().equals(group), "Grouped masses survive serialization");
        helper.assertTrue(ItemWeightDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"weights\":{\"minecraft:iron_helmet\":-1}}")).error().isPresent(),
                "Negative masses are rejected");
        expectFailure(() -> new ItemWeightDefinition(java.util.Map.of()));
        helper.succeed();
    }

    @GameTest
    public void shieldProfileSeparatesParryFromBlock(GameTestHelper helper) {
        var shield = ShieldProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"items":["minecraft:shield"],"block_fraction":0.65,"parry_fraction":0.95,
                 "parry_window_ticks":6,"stamina_per_damage":1.2,"guard_break_impact":6.0,"stagger_ticks":30}
                """)).getOrThrow();
        helper.assertTrue(shield.isParry(0) && shield.isParry(6), "The parry window is open from the first active tick");
        helper.assertFalse(shield.isParry(7), "A later hit is an ordinary block, not a parry");
        helper.assertTrue(shield.parryFraction() > shield.blockFraction(),
                "Section 34: a parry prevents most damage, a block only reduces it");
        helper.assertTrue(ShieldProfile.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"items\":[\"minecraft:shield\"],\"parry_window_ticks\":400}"))
                .error().isPresent(), "An unbounded parry window is rejected");
        helper.succeed();
    }

    @GameTest
    public void armorMaterialChangesWhichWeaponHurts(GameTestHelper helper) {
        var level = helper.getLevel();
        var combat = CoreLifecycle.require(level.getServer()).combat();
        Zombie attacker = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        attacker.setOnGround(true);
        attacker.setSprinting(false);
        Zombie defender = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        equipPlate(defender);

        float bare = combat.scaleIncomingDamage(attacker, sourceWith(level.damageSources(), attacker, Items.IRON_SWORD), 10.0f);
        float cut = combat.scaleIncomingDamage(defender, sourceWith(level.damageSources(), attacker, Items.IRON_SWORD), 10.0f);
        float crush = combat.scaleIncomingDamage(defender, sourceWith(level.damageSources(), attacker, Items.MACE), 10.0f);
        helper.assertTrue(cut < bare, "Plate reduces a cut that an unarmored defender takes in full");
        helper.assertTrue(crush > cut, "Section 36: plate answers a cut far better than a crushing blow");
        helper.assertTrue(crush <= 10.0f && cut > 0, "The armor stage stays inside the incoming amount");

        float bypassing = combat.scaleIncomingDamage(defender, level.damageSources().fellOutOfWorld(), 10.0f);
        helper.assertTrue(bypassing == 10.0f, "Damage that bypasses armor is not reduced by the material stage");
        attacker.discard();
        defender.discard();
        helper.succeed();
    }

    @GameTest
    public void mixinAppliesTheArmorStageToRealDamage(GameTestHelper helper) {
        var level = helper.getLevel();
        Zombie attacker = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        attacker.setOnGround(true);
        Zombie cutTarget = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        Zombie crushTarget = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        equipPlate(cutTarget);
        equipPlate(crushTarget);
        float startHealth = cutTarget.getHealth();

        cutTarget.hurtServer(level, sourceWith(level.damageSources(), attacker, Items.IRON_SWORD), 12.0f);
        crushTarget.hurtServer(level, sourceWith(level.damageSources(), attacker, Items.MACE), 12.0f);
        float cutTaken = startHealth - cutTarget.getHealth();
        float crushTaken = startHealth - crushTarget.getHealth();
        helper.assertTrue(cutTaken > 0 && crushTaken > 0, "Both hits still reach the defender");
        helper.assertTrue(crushTaken > cutTaken,
                "The damage-type stage must reach real damage, not only the calculation");
        attacker.discard();
        cutTarget.discard();
        crushTarget.discard();
        helper.succeed();
    }

    @GameTest(maxTicks = 60)
    public void raisedShieldResolvesParryBlockAndGuardBreak(GameTestHelper helper) {
        var level = helper.getLevel();
        var combat = CoreLifecycle.require(level.getServer()).combat();
        Zombie attacker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        attacker.setOnGround(true);
        Zombie defender = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        defender.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        defender.startUsingItem(InteractionHand.OFF_HAND);

        helper.runAfterDelay(7, () -> {
            helper.assertTrue(defender.isBlocking(), "The guard is up once the vanilla block delay has passed");
            float parried = combat.resolveBlocking(defender, level,
                    sourceWith(level.damageSources(), attacker, Items.IRON_SWORD), 10.0f, 10.0f);
            helper.assertTrue(parried > 9.0f, "A hit inside the window is parried and prevents most damage");
            helper.assertTrue(combat.isStaggered(attacker), "Section 34: a parry staggers the attacker");
        });
        helper.runAfterDelay(25, () -> {
            float blocked = combat.resolveBlocking(defender, level,
                    sourceWith(level.damageSources(), attacker, Items.IRON_SWORD), 10.0f, 10.0f);
            helper.assertTrue(Math.abs(blocked - 6.5f) < 1.0E-4,
                    "After the window the same hit is an ordinary block, not a parry");
            float broken = combat.resolveBlocking(defender, level,
                    sourceWith(level.damageSources(), attacker, Items.IRON_AXE), 10.0f, 10.0f);
            helper.assertTrue(broken == 0.0f, "An axe breaks the guard, so nothing is blocked");
            helper.assertFalse(defender.isBlocking(), "A broken guard is disabled");
            attacker.discard();
            defender.discard();
            helper.succeed();
        });
    }

    @GameTest
    public void unprofiledShieldKeepsVanillaBlocking(GameTestHelper helper) {
        var level = helper.getLevel();
        var combat = CoreLifecycle.require(level.getServer()).combat();
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        float vanillaBlocked = combat.resolveBlocking(zombie, level,
                level.damageSources().generic(), 10.0f, 4.0f);
        helper.assertTrue(vanillaBlocked == 4.0f,
                "Without a raised, profiled shield the vanilla blocking result is left untouched");
        helper.assertTrue(combat.resolveBlocking(zombie, level, level.damageSources().generic(), 10.0f, 0.0f) == 0.0f,
                "Nothing to resolve when vanilla blocked nothing");
        zombie.discard();
        helper.succeed();
    }

    @GameTest
    public void combatProtocolRoundTrip(GameTestHelper helper) {
        var payload = new CombatSnapshotPayload(CombatEvent.PARRIED, CombatDamageType.SLASH, 6.5f, 6);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CombatSnapshotPayload.CODEC.encode(buffer, payload);
            helper.assertTrue(payload.equals(CombatSnapshotPayload.CODEC.decode(buffer)),
                    "Combat feedback must round trip without client-side reconstruction");
            buffer.clear();
            buffer.writeByte(99);
            buffer.writeByte(0);
            buffer.writeFloat(1);
            buffer.writeVarInt(0);
            expectFailure(() -> CombatSnapshotPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
        expectFailure(() -> new CombatSnapshotPayload(CombatEvent.HIT, CombatDamageType.BLUNT, Float.NaN, 0));
        expectFailure(() -> new CombatSnapshotPayload(CombatEvent.HIT, CombatDamageType.BLUNT, 1, 999));
        expectFailure(() -> CombatEvent.byOrdinal(-1));
        helper.succeed();
    }

    @GameTest
    public void combatJobsAreRegisteredAndBounded(GameTestHelper helper) {
        var scheduler = CoreLifecycle.require(helper.getLevel().getServer()).scheduler();
        var ids = scheduler.profiles().stream().map(profile -> profile.id()).toList();
        helper.assertTrue(ids.contains("hardwrought:combat_guard") && ids.contains("hardwrought:combat_stagger")
                        && ids.contains("hardwrought:combat_equipment"),
                "Combat upkeep runs as registered simulation jobs, not as ad-hoc ticking");
        helper.assertTrue(scheduler.profiles().stream()
                        .filter(profile -> profile.id().startsWith("hardwrought:combat"))
                        .noneMatch(profile -> profile.disabled()),
                "No combat job has failed and been disabled");
        helper.succeed();
    }

    @GameTest
    public void staggerExpiresAndStaysBounded(GameTestHelper helper) {
        var combat = CoreLifecycle.require(helper.getLevel().getServer()).combat();
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO.above());
        double normalSpeed = zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        combat.stagger(zombie, 30);
        helper.assertTrue(combat.isStaggered(zombie), "A parried attacker is staggered");
        helper.assertTrue(zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                < normalSpeed, "A stagger slows the entity down");
        combat.stagger(zombie, 0);
        helper.assertTrue(combat.isStaggered(zombie), "A zero-length stagger never replaces a running one");
        zombie.discard();
        helper.succeed();
    }

    private static DamageSource sourceWith(net.minecraft.world.damagesource.DamageSources sources,
                                           LivingEntity attacker, Item weapon) {
        attacker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        return sources.mobAttack(attacker);
    }

    private static void equipPlate(LivingEntity entity) {
        entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        entity.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        entity.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected operation to be rejected");
    }
}
