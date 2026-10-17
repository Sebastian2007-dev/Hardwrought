package de.ipnats.hardwrought.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModEffects;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Adrenaline: in a fight the body pays for it later.
 *
 * <p>Every blow taken or struck in a fight raises it. While it runs high, part of what the fight costs
 * is not felt yet — up to {@link #STAMINA_MASK} of the stamina, and up to {@link #DAMAGE_MASK} of the
 * damage, which comes straight back as health — and goes on account instead. Once the fight has been
 * over for {@link #COMBAT_TICKS}, the account is paid: the stamina drains away and the health goes, over
 * a few seconds, from within. It never takes a player below {@link #FLOOR_HEALTH} — the crash knocks a
 * player down, it does not kill them — and it never saves one either: a blow that kills, kills.
 *
 * <p>Hunger is not felt in a fight either: while adrenaline shows, an empty stomach neither hurts nor
 * stops a player from running, and up to {@link #HUNGER_MASK} of what effort costs the stomach is not
 * spent at all. Unlike stamina and damage, this is not owed afterwards.
 */
public final class Adrenaline {
    /** How long after the last blow a fight is over, in ticks. */
    public static final int COMBAT_TICKS = 8 * 20;
    /** At full adrenaline, the share of stamina and of damage not yet felt. */
    public static final double STAMINA_MASK = 0.5;
    public static final double DAMAGE_MASK = 0.4;
    /** At full adrenaline, the share of hunger not spent at all; while it shows, at least a third of that. */
    public static final double HUNGER_MASK = 0.6;
    /** What a blow taken and a blow struck add to it, 0 to 1. */
    static final double TAKEN_RISE = 0.25, STRUCK_RISE = 0.15;
    /** How fast it ebbs a second, in a fight and after. */
    static final double FIGHT_EBB = 0.02, CRASH_EBB = 0.25;
    /** The share of what is owed paid each second after the fight, and at least this much. */
    static final double PAY_SHARE = 0.3, PAY_LEAST = 1.0;
    /** The crash leaves a player at least half a heart. */
    public static final float FLOOR_HEALTH = 1.0f;

    /** Where a player stands: how high it runs, when they last fought, and what is owed. */
    public record Rush(double level, long lastFight, double staminaOwed, double damageOwed) {
        public static final Codec<Rush> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("level").forGetter(Rush::level),
                Codec.LONG.fieldOf("last_fight").forGetter(Rush::lastFight),
                Codec.DOUBLE.fieldOf("stamina_owed").forGetter(Rush::staminaOwed),
                Codec.DOUBLE.fieldOf("damage_owed").forGetter(Rush::damageOwed)
        ).apply(i, Rush::new));

        static final Rush NONE = new Rush(0, Long.MIN_VALUE / 2, 0, 0);

        boolean idle() {
            return level <= 0 && staminaOwed <= 0 && damageOwed <= 0;
        }
    }

    /** Kept with the player, so a fight is not walked away from by leaving the game. */
    public static final AttachmentType<Rush> RUSH = AttachmentRegistry.create(Hardwrought.id("adrenaline"),
            builder -> builder.persistent(Rush.CODEC));

    private Adrenaline() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(Adrenaline::tick);
        // Hunger is not felt in a fight: an empty stomach does not hurt while it runs.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player && source.is(DamageTypes.STARVE) && player.hasEffect(ModEffects.ADRENALINE)));
    }

    /** Hunger spent: in a fight, part of it is simply not spent. {@code exhaustion} is positive. */
    public static float maskHunger(ServerPlayer player, float exhaustion) {
        if (exhaustion <= 0 || !player.hasEffect(ModEffects.ADRENALINE)) return exhaustion;
        return (float) (exhaustion * (1 - HUNGER_MASK * Math.max(of(player).level(), 0.34)));
    }

    static Rush of(ServerPlayer player) {
        return player.getAttachedOrElse(RUSH, Rush.NONE);
    }

    /** How high it runs, 0 to 1. */
    public static double level(ServerPlayer player) {
        return of(player).level();
    }

    /** Whether this player is in a fight right now. */
    public static boolean fighting(ServerPlayer player) {
        return player.level().getGameTime() - of(player).lastFight() < COMBAT_TICKS;
    }

    /** A blow struck in a fight. */
    public static void struck(ServerPlayer player) {
        rise(player, STRUCK_RISE);
    }

    /**
     * A blow taken in a fight, after it has landed: it rises, and if the player lived, part of the damage
     * comes back as health, owed for later.
     */
    public static void taken(ServerPlayer player, float damage) {
        rise(player, TAKEN_RISE);
        if (!player.isAlive() || damage <= 0) return;
        Rush rush = of(player);
        float masked = (float) Math.min(damage * DAMAGE_MASK * rush.level(), player.getMaxHealth() - player.getHealth());
        if (masked <= 0) return;
        player.setHealth(player.getHealth() + masked);
        player.setAttached(RUSH, new Rush(rush.level(), rush.lastFight(), rush.staminaOwed(), rush.damageOwed() + masked));
    }

    private static void rise(ServerPlayer player, double by) {
        Rush rush = of(player);
        player.setAttached(RUSH, new Rush(Math.min(1.0, rush.level() + by), player.level().getGameTime(),
                rush.staminaOwed(), rush.damageOwed()));
    }

    /** Stamina spent: what is felt now, the rest owed. {@code cost} is positive. */
    public static double maskStamina(ServerPlayer player, double cost) {
        if (cost <= 0) return cost;
        Rush rush = of(player);
        if (rush.level() <= 0 || !fighting(player)) return cost;
        double masked = cost * STAMINA_MASK * rush.level();
        player.setAttached(RUSH, new Rush(rush.level(), rush.lastFight(), rush.staminaOwed() + masked, rush.damageOwed()));
        return cost - masked;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.hasAttached(RUSH)) continue;
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                player.removeAttached(RUSH);
                continue;
            }
            settle(player);
        }
    }

    /** One second: in a fight it shows; after one, it ebbs and the account is paid. */
    static void settle(ServerPlayer player) {
        Rush rush = of(player);
        if (fighting(player)) {
            double level = Math.max(0, rush.level() - FIGHT_EBB);
            player.setAttached(RUSH, new Rush(level, rush.lastFight(), rush.staminaOwed(), rush.damageOwed()));
            player.addEffect(new MobEffectInstance(ModEffects.ADRENALINE, 50, band(level), false, false, true));
            return;
        }
        player.removeEffect(ModEffects.ADRENALINE);
        double staminaPaid = Math.min(rush.staminaOwed(), Math.max(PAY_LEAST, rush.staminaOwed() * PAY_SHARE));
        double damagePaid = Math.min(rush.damageOwed(), Math.max(PAY_LEAST, rush.damageOwed() * PAY_SHARE));
        if (staminaPaid > 0) {
            var runtime = CoreLifecycle.find(player.level().getServer());
            if (runtime != null) runtime.survival().drainStamina(player, staminaPaid);
        }
        if (damagePaid > 0) {
            // From within, not as a blow: it does not wake or knock back, and it stops at the floor.
            player.setHealth(Math.max(Math.min(FLOOR_HEALTH, player.getHealth()), player.getHealth() - (float) damagePaid));
        }
        Rush next = new Rush(Math.max(0, rush.level() - CRASH_EBB), rush.lastFight(),
                rush.staminaOwed() - staminaPaid, rush.damageOwed() - damagePaid);
        if (staminaPaid > 0 || damagePaid > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.ADRENALINE_CRASH, 50, 0, false, false, true));
        }
        if (next.idle()) player.removeAttached(RUSH);
        else player.setAttached(RUSH, next);
    }

    /** One second of it, for tests. */
    public static void settleForTesting(ServerPlayer player) {
        settle(player);
    }

    /** Ends the fight at once, for tests: as if the last blow were long ago. */
    public static void endFightForTesting(ServerPlayer player) {
        Rush rush = of(player);
        player.setAttached(RUSH, new Rush(rush.level(), Rush.NONE.lastFight(), rush.staminaOwed(), rush.damageOwed()));
    }

    /** The level shown: I, II or III. */
    static int band(double level) {
        return level >= 0.67 ? 2 : level >= 0.34 ? 1 : 0;
    }
}
