package de.ipnats.hardwrought.combat;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.CombatSnapshotPayload;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import de.ipnats.hardwrought.core.utilities.AttributeModifiers;
import de.ipnats.hardwrought.survival.SurvivalSystem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Milestone-2 combat: damage types, weapon values, armor interaction, blocking, parrying and the
 * stamina those actions cost. Every decision is made on the server; clients receive only the
 * resulting event for display.
 *
 * <p>The system layers on vanilla instead of replacing it. Vanilla still contributes the base
 * weapon damage, the armor points and the front-arc check for a raised shield; Hardwrought decides
 * how the hit splits across slash, pierce and blunt, how the worn material answers that type, and
 * what a block, a parry or a broken guard does.
 */
public final class CombatSystem {
    private static final Identifier REACH_MODIFIER = Hardwrought.id("weapon_reach");
    private static final Identifier ATTACK_SPEED_MODIFIER = Hardwrought.id("weapon_attack_speed");
    private static final Identifier STAGGER_MOVEMENT_MODIFIER = Hardwrought.id("stagger_movement");
    private static final Identifier STAGGER_ATTACK_MODIFIER = Hardwrought.id("stagger_attack");
    /** Below this stamina an attack starts to lose power, down to {@link #EXHAUSTED_ATTACK_POWER}. */
    public static final double TIRED_STAMINA = 35.0;
    public static final double EXHAUSTED_ATTACK_POWER = 0.55;
    private static final double PARRY_STAMINA = 1.5;
    private static final double STAGGER_PENALTY = -0.5;

    private static boolean eventsInitialized;

    private final MinecraftServer server;
    private final SurvivalSystem survival;
    private final SimulationScheduler scheduler;
    private final Map<UUID, Boolean> guarding = new HashMap<>();
    private final Map<UUID, Stagger> staggered = new HashMap<>();

    public CombatSystem(MinecraftServer server, SurvivalSystem survival, SimulationScheduler scheduler) {
        this.server = server;
        this.survival = survival;
        this.scheduler = scheduler;
        scheduler.register("hardwrought:combat_guard", SimulationTier.CRITICAL, this::tickGuardState);
        scheduler.register("hardwrought:combat_stagger", SimulationTier.FAST, this::tickStagger);
        scheduler.register("hardwrought:combat_equipment", SimulationTier.MEDIUM, this::tickEquipment);
    }

    /** A melee swing is refused when the wielder cannot pay the stamina its weapon class demands. */
    public static void initializeEvents() {
        if (eventsInitialized) return;
        eventsInitialized = true;
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                CombatSystem combat = runtimeCombat(serverPlayer);
                if (combat != null) return combat.onPlayerAttack(serverPlayer, hand);
            }
            return InteractionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, taken, blocked) -> {
            if (!blocked && taken > 0 && entity instanceof ServerPlayer player) {
                CombatSystem combat = runtimeCombat(player);
                if (combat != null) combat.reportHit(player, source, taken);
            }
        });
    }

    private static CombatSystem runtimeCombat(ServerPlayer player) {
        var runtime = CoreLifecycle.find(player.level().getServer());
        return runtime == null ? null : runtime.combat();
    }

    public Map<Identifier, WeaponProfile> weaponProfiles() {
        return server.getOrThrow(WeaponProfiles.KEY);
    }

    public Map<Identifier, ArmorProfile> armorProfiles() {
        return server.getOrThrow(ArmorProfiles.KEY);
    }

    public Map<Identifier, ShieldProfile> shieldProfiles() {
        return server.getOrThrow(ShieldProfiles.KEY);
    }

    /** Items without a profile stay usable: their hits count as an improvised blunt impact. */
    public WeaponProfile weaponProfile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return WeaponProfile.IMPROVISED;
        WeaponProfile profile = weaponProfiles().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return profile == null ? WeaponProfile.IMPROVISED : profile;
    }

    /** DamageSource#getWeaponItem is nullable for sources that carry no weapon at all. */
    public static ItemStack weaponItem(DamageSource source) {
        ItemStack stack = source.getWeaponItem();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private boolean isProfiled(ItemStack stack) {
        return !stack.isEmpty() && weaponProfiles().containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Null means the item has no Hardwrought blocking behaviour and keeps the vanilla one. */
    public ShieldProfile shieldProfile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return shieldProfiles().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public ArmorCoverage armorCoverage(LivingEntity entity) {
        return ArmorCoverage.of(entity, armorProfiles());
    }

    // ---------------------------------------------------------------- attacking

    /** A melee swing costs the stamina its weapon class demands; without it the swing is refused. */
    public InteractionResult onPlayerAttack(ServerPlayer player, InteractionHand hand) {
        WeaponProfile profile = weaponProfile(player.getItemInHand(hand));
        return survival.spendStamina(player, profile.staminaCost()) ? InteractionResult.PASS : InteractionResult.FAIL;
    }

    /**
     * Attack power of section 7: a tired fighter still swings, but the hit lands with less of the
     * weapon behind it. Entities without a stamina reserve are unaffected.
     */
    public double attackPower(LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayer player)) return 1.0;
        double stamina = survival.stamina(player);
        if (stamina >= TIRED_STAMINA) return 1.0;
        return EXHAUSTED_ATTACK_POWER + (1.0 - EXHAUSTED_ATTACK_POWER) * (stamina / TIRED_STAMINA);
    }

    // ---------------------------------------------------------------- incoming damage

    /**
     * Damage type and armor material stage. This is a pure calculation: it is also reached for hits
     * vanilla later discards during invulnerability, so it must not change any state.
     */
    public float scaleIncomingDamage(LivingEntity defender, DamageSource source, float amount) {
        if (!(amount > 0) || !Float.isFinite(amount)) return amount;
        ItemStack weaponStack = weaponItem(source);
        WeaponProfile weapon = weaponProfile(weaponStack);
        boolean weaponHit = isProfiled(weaponStack);
        double damage;
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            damage = amount;
        } else if (weaponHit) {
            ArmorCoverage coverage = armorCoverage(defender);
            damage = 0;
            for (CombatDamageType type : CombatDamageType.values()) {
                double share = weapon.damage().share(type);
                if (share <= 0) continue;
                damage += amount * share * (1.0 - coverage.effectiveResistance(type, weapon.armorPenetration()));
            }
        } else {
            CombatDamageType type = CombatDamageType.classify(source);
            double resistance = type.physical()
                    ? armorCoverage(defender).effectiveResistance(type, weapon.armorPenetration()) : 0;
            damage = amount * (1.0 - resistance);
        }
        damage *= attackerFactors(defender, source, weapon);
        // Section 38: a blade forged well, and hardened, hits harder than the same blade made badly.
        damage *= de.ipnats.hardwrought.smithing.ForgeQuality.damageFactor(weaponStack);
        return (float) Math.max(0, damage);
    }

    /** Only a direct melee hit is affected by how the attacker was moving when it landed. */
    private double attackerFactors(LivingEntity defender, DamageSource source, WeaponProfile weapon) {
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker) {
            return 1.0;
        }
        return attackPower(attacker)
                * weapon.controlFactor(attacker.isSprinting(), !attacker.onGround())
                * weapon.closeQuartersFactor(attacker.distanceTo(defender));
    }

    // ---------------------------------------------------------------- blocking and parrying

    /**
     * Replaces the amount vanilla would block. Vanilla has already checked that the guard is up,
     * faces the attack and is not bypassed; a zero means there is nothing to resolve. This runs once
     * per hit that actually reaches the defender, so it may change state.
     */
    public float resolveBlocking(LivingEntity defender, ServerLevel level, DamageSource source,
                                float damage, float vanillaBlocked) {
        if (!(vanillaBlocked > 0) || !(damage > 0)) return vanillaBlocked;
        ItemStack blockingWith = defender.getItemBlockingWith();
        ShieldProfile shield = shieldProfile(blockingWith);
        if (shield == null) return vanillaBlocked;
        BlocksAttacks blocksAttacks = blockingWith.get(DataComponents.BLOCKS_ATTACKS);
        int guardTicks = defender.getTicksUsingItem() - (blocksAttacks == null ? 0 : blocksAttacks.blockDelayTicks());
        WeaponProfile weapon = weaponProfile(weaponItem(source));
        CombatDamageType type = dominantType(source, weapon);

        if (shield.isParry(guardTicks)) {
            spend(defender, PARRY_STAMINA);
            stagger(attackerOf(source), shield.staggerTicks());
            report(defender, CombatEvent.PARRIED, type, damage, shield.parryWindowTicks());
            reportAttacker(source, CombatEvent.PARRY_LANDED, type, damage);
            return (float) (damage * shield.parryFraction());
        }

        double cost = damage * shield.staminaPerDamage();
        boolean overwhelmed = weapon.impact() > shield.guardBreakImpact();
        boolean exhausted = defender instanceof ServerPlayer player && survival.stamina(player) < cost;
        if (overwhelmed || exhausted) {
            if (blocksAttacks != null) {
                blocksAttacks.disable(level, defender, 1.0f + (float) Math.min(3.0, weapon.impact() * 0.2), blockingWith);
            }
            stagger(defender, shield.staggerTicks());
            report(defender, CombatEvent.GUARD_BROKEN, type, damage, shield.parryWindowTicks());
            return 0.0f;
        }
        spend(defender, cost);
        report(defender, CombatEvent.BLOCKED, type, damage, shield.parryWindowTicks());
        return (float) (damage * shield.blockFraction());
    }

    /** Reports the type of a hit that was not blocked, so the HUD can show what got through. */
    public void reportHit(LivingEntity defender, DamageSource source, float damage) {
        if (!(defender instanceof ServerPlayer) || !(damage > 0)) return;
        WeaponProfile weapon = weaponProfile(weaponItem(source));
        report(defender, CombatEvent.HIT, dominantType(source, weapon), damage, 0);
    }

    private CombatDamageType dominantType(DamageSource source, WeaponProfile weapon) {
        return isProfiled(weaponItem(source)) ? weapon.damage().dominantType() : CombatDamageType.classify(source);
    }

    private static LivingEntity attackerOf(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity living) return living;
        return source.getEntity() instanceof LivingEntity living ? living : null;
    }

    private void spend(LivingEntity defender, double amount) {
        if (defender instanceof ServerPlayer player) survival.spendStamina(player, amount);
    }

    // ---------------------------------------------------------------- stagger

    /** A staggered fighter moves and swings slower until the penalty runs out. */
    public void stagger(LivingEntity entity, int ticks) {
        if (entity == null || ticks <= 0) return;
        AttributeModifiers.update(entity.getAttribute(Attributes.MOVEMENT_SPEED), STAGGER_MOVEMENT_MODIFIER,
                STAGGER_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        AttributeModifiers.update(entity.getAttribute(Attributes.ATTACK_SPEED), STAGGER_ATTACK_MODIFIER,
                STAGGER_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        staggered.put(entity.getUUID(), new Stagger(new WeakReference<>(entity), scheduler.ticks() + ticks));
    }

    public boolean isStaggered(LivingEntity entity) {
        Stagger stagger = staggered.get(entity.getUUID());
        return stagger != null && stagger.expiry > scheduler.ticks();
    }

    private void tickStagger() {
        long now = scheduler.ticks();
        staggered.values().removeIf(stagger -> {
            LivingEntity entity = stagger.entity.get();
            if (entity == null) return true;
            if (stagger.expiry > now && entity.isAlive()) return false;
            AttributeModifiers.clear(entity.getAttribute(Attributes.MOVEMENT_SPEED), STAGGER_MOVEMENT_MODIFIER);
            AttributeModifiers.clear(entity.getAttribute(Attributes.ATTACK_SPEED), STAGGER_ATTACK_MODIFIER);
            return true;
        });
    }

    private record Stagger(WeakReference<LivingEntity> entity, long expiry) { }

    // ---------------------------------------------------------------- per-tick upkeep

    private void tickGuardState() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ShieldProfile shield = player.isBlocking() ? shieldProfile(player.getItemBlockingWith()) : null;
            boolean now = shield != null;
            Boolean previous = guarding.put(player.getUUID(), now);
            if (previous != null && previous == now) continue;
            report(player, now ? CombatEvent.GUARD_UP : CombatEvent.GUARD_DOWN, CombatDamageType.BLUNT, 0,
                    now ? shield.parryWindowTicks() : 0);
        }
    }

    private void tickEquipment() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WeaponProfile profile = weaponProfile(player.getMainHandItem());
            AttributeModifiers.update(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), REACH_MODIFIER,
                    profile.reachBonusBlocks(), AttributeModifier.Operation.ADD_VALUE);
            AttributeModifiers.update(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_MODIFIER,
                    profile.attackSpeedFactor() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    public void disconnect(UUID id) {
        guarding.remove(id);
        staggered.remove(id);
    }

    // ---------------------------------------------------------------- reporting

    private void report(LivingEntity defender, CombatEvent event, CombatDamageType type, float damage,
                        int parryWindowTicks) {
        if (defender instanceof ServerPlayer player) send(player, event, type, damage, parryWindowTicks);
    }

    private void reportAttacker(DamageSource source, CombatEvent event, CombatDamageType type, float damage) {
        if (source.getEntity() instanceof ServerPlayer player) send(player, event, type, damage, 0);
    }

    private void send(ServerPlayer player, CombatEvent event, CombatDamageType type, float damage,
                      int parryWindowTicks) {
        if (!ServerPlayNetworking.canSend(player, CombatSnapshotPayload.TYPE)) return;
        ServerPlayNetworking.send(player, new CombatSnapshotPayload(event, type,
                Math.min(100_000f, Math.max(0f, damage)), parryWindowTicks));
    }
}
