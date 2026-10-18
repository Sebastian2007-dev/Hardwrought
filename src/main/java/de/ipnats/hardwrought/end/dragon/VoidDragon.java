package de.ipnats.hardwrought.end.dragon;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.combat.Rending;
import de.ipnats.hardwrought.core.registry.ModDamageTypes;
import de.ipnats.hardwrought.end.BrokenIsland;
import de.ipnats.hardwrought.mixin.EnderDragonPhaseInvoker;
import de.ipnats.hardwrought.witchking.MantleFlight;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The End's dragon as Hardwrought makes it (endgame § 4): an air fight over the broken island, meant to
 * be the hardest in the game for whoever brings the best there is.
 *
 * <ol>
 *   <li><b>The crystals</b> — while any crystal stands it is shielded: harm reaches it only in part
 *   ({@link #SHIELDED}), and the crystals mend it. The crystals carry wards of their own
 *   ({@link CrystalWards}).</li>
 *   <li><b>The hunt</b> — the crystals gone: it hunts whoever flies.</li>
 *   <li><b>The landing</b> — below {@link #LANDING_AT}: now and then it comes down on the middle of the
 *   island, the window to fight it on foot.</li>
 *   <li><b>The breaking</b> — below {@link #RAGE_AT}: it rages, the island breaks up under it, and the
 *   void pulls at whoever is in the air.</li>
 * </ol>
 *
 * What it does in each is chosen by {@link DragonHunt}, its own AI put in place of vanilla's phases.
 * Raised again it is stronger ({@link #RISEN}): more of it, harder hitting, quicker, and with one move
 * more — and only then does it leave its heart behind.
 *
 * <p>Its harm is made for adamant and runes: bites and dives are blows armour holds only half of, the
 * void it breathes is magic armour does not hold at all; protective runes hold half against either; and
 * whatever it hits is thrown out of its hover.
 */
public final class VoidDragon {
    public static final EnderDragonPhase<DragonHunt> HUNT = EnderDragonPhaseInvoker.hardwrought$create(DragonHunt.class, "HardwroughtHunt");

    public static final double HEALTH = 600, RISEN_HEALTH = 900;
    /** Stages by health share. */
    public static final float LANDING_AT = 0.55f, RAGE_AT = 0.25f;
    /** How much of what strikes it reaches it while a crystal stands. */
    static final float SHIELDED = 0.2f;
    /** The tag a raised dragon carries. */
    public static final String RISEN = "hardwrought.risen";

    static final float BITE = 16, DIVE = 22, BUFFET = 6, GUST = 7, BOLT = 12, CLOUD = 4, STOMP = 15, ROAR = 0;
    /** Armour's share against its blows, runes' share against anything of it. */
    static final float BLOW_ARMOUR = 0.5f, RUNE_SHARE = 0.5f;
    /** How far from the middle of the arena it hunts. */
    static final double REACH = 170;

    public static final int VIOLET = 0xB04CFF, DEEP = 0x2A0838, MAGENTA = 0xFF3CC8, PALE = 0xF2DCFF;

    private VoidDragon() { }

    public static void initialize() {
        de.ipnats.hardwrought.knowledge.LootSources.register(() -> DragonHeart.HEART,
                de.ipnats.hardwrought.knowledge.LootSources.KIND_MOB, () -> net.minecraft.resources.Identifier.withDefaultNamespace("ender_dragon"));
        // Touch HUNT before the first dragon is made, so every phase manager has room for it.
        Hardwrought.LOGGER.debug("Dragon phase {} registered", HUNT.getId());
        VoidBolt.initialize();
        DragonHazards.initialize();
        CrystalWards.initialize();
        DragonHeart.initialize();
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof EnderDragon dragon && entity.level() instanceof ServerLevel level) DragonHeart.died(level, dragon);
        });
    }

    // ------------------------------------------------------------------------------------ state

    public static boolean risen(EnderDragon dragon) {
        return dragon.entityTags().contains(RISEN);
    }

    /** Raised again after a first death: tougher, once, when it first moves. */
    static void rise(EnderDragon dragon) {
        if (risen(dragon)) return;
        dragon.addTag(RISEN);
        dragon.getAttribute(Attributes.MAX_HEALTH).setBaseValue(RISEN_HEALTH);
        dragon.setHealth(dragon.getMaxHealth());
    }

    public static float share(EnderDragon dragon) {
        return dragon.getHealth() / dragon.getMaxHealth();
    }

    /** 1 while crystals stand, 2 for the hunt, 3 once it lands, 4 when it rages. */
    public static int stage(EnderDragon dragon, int crystals) {
        if (crystals > 0) return 1;
        float share = share(dragon);
        if (share < RAGE_AT) return 4;
        return share < LANDING_AT ? 3 : 2;
    }

    /** How much harder it hits than its plain self. */
    static float fury(EnderDragon dragon) {
        return (risen(dragon) ? 1.3f : 1f) * (share(dragon) < RAGE_AT ? 1.15f : 1f);
    }

    /** The middle of the arena: the exit portal's place. */
    static Vec3 middle(EnderDragon dragon) {
        BlockPos origin = dragon.getFightOrigin();
        return new Vec3(origin.getX() + 0.5, BrokenIsland.MIDDLE_TOP + 1, origin.getZ() + 0.5);
    }

    /** Everyone it would fight. */
    static List<ServerPlayer> foes(ServerLevel level, EnderDragon dragon) {
        List<ServerPlayer> foes = new ArrayList<>();
        Vec3 middle = middle(dragon);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            if (player.position().distanceToSqr(middle) < REACH * REACH) foes.add(player);
        }
        return foes;
    }

    // ------------------------------------------------------------------------------------ its harm

    static boolean blow(ServerLevel level, EnderDragon dragon, LivingEntity victim, float amount) {
        return Rending.rend(level, victim, ModDamageTypes.DRAGON_BITE, dragon, dragon, amount * fury(dragon), BLOW_ARMOUR, RUNE_SHARE);
    }

    static boolean voidHarm(ServerLevel level, EnderDragon dragon, Entity direct, LivingEntity victim, float amount) {
        ResourceKey<DamageType> type = ModDamageTypes.VOID;
        return Rending.rend(level, victim, type, direct, dragon, amount * fury(dragon), 0, RUNE_SHARE);
    }

    /** Thrown: away, and down towards the void; and out of any hover on the mantle. */
    static void hurl(Entity victim, Vec3 from, double strength, double down) {
        Vec3 away = victim.position().subtract(from);
        away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
        victim.setDeltaMovement(victim.getDeltaMovement().add(away.x * strength, away.y * strength * 0.5 - down, away.z * strength));
        victim.needsSync = true;
        if (victim instanceof ServerPlayer player) MantleFlight.knockOut(player);
    }

    /** What touches its head or neck: torn. In place of vanilla's ten. */
    public static void bite(ServerLevel level, EnderDragon dragon, List<Entity> touching) {
        if (!(dragon.getPhaseManager().getCurrentPhase() instanceof DragonHunt hunt)) return;
        for (Entity entity : touching) {
            if (!(entity instanceof LivingEntity living) || entity instanceof EnderDragon || !dragon.canAttack(living)) continue;
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            float amount = hunt.diving() ? DIVE : BITE;
            if (blow(level, dragon, living, amount)) hurl(entity, dragon.head.position(), hunt.diving() ? 2.6 : 1.6, 0.4);
        }
    }

    /** What its wings or body brush past: thrown aside, and hurt a little. In place of vanilla's push. */
    public static void buffet(ServerLevel level, EnderDragon dragon, List<Entity> touching) {
        Vec3 body = dragon.position();
        for (Entity entity : touching) {
            if (!(entity instanceof LivingEntity living) || entity instanceof EnderDragon) continue;
            if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            hurl(entity, body, 1.8, 0.3);
            // Hurt at most every half second: the hit-cooldown sees to it.
            blow(level, dragon, living, BUFFET);
        }
    }

    /** Whether this level is the End. */
    static boolean inEnd(Level level) {
        return level.dimension() == Level.END;
    }
}
