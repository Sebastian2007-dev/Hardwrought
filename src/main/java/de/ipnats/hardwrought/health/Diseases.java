package de.ipnats.hardwrought.health;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModEffects;
import de.ipnats.hardwrought.survival.PlayerVitals;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Being ill: catching something, how it runs, what it does to the body, and what helps.
 *
 * <p>A disease is caught — from bad water or bad meat, from being cold and wet too long, from a burn
 * or a wound — and nothing is felt while it takes hold. Then it grows, fast at first and slower as
 * the body fights it, and a body that is looked after throws it off by itself: sleep, rest, water,
 * food and warmth all help, and a tea made for it helps most. Neglected, it goes on growing into its
 * worse form, and at its worst it can kill.
 *
 * <p>Whatever it is, a sick body is hungrier and wearier than a well one, recovers its breath more
 * slowly, and no amount of lying in bed makes it restless.
 *
 * <p>What a player has is kept on the player ({@link #HEALTH}), so it ends with them at death, and it
 * goes to their own client to be shown. A disease is only named there once the player has been
 * through it; before that, only what it does is described.
 */
public final class Diseases {
    /** Load at which a disease is at its height and takes its worse form. */
    public static final double SEVERE = 50;
    /** Load at which a disease can kill. */
    public static final double CRITICAL = 80;
    /** How far below its peak a disease has to fall to count as on the way out. */
    public static final double TURNING = 5;
    static final double MOST = 100;
    /** How long a disease, once thrown off, cannot be caught again, in seconds. */
    public static final int IMMUNE_SECONDS = 600;

    /** Hungrier while ill: the body burns more fighting it. */
    public static final double SICK_HUNGER = 1.5;
    /** Wearier while ill. */
    public static final double SICK_FATIGUE = 1.8;
    /** Breath comes back slower while ill. */
    public static final double SICK_RECOVERY = 0.7;

    /** Seconds of being cold, or cold and wet, before a cold is caught. */
    static final int CHILL_TO_CATCH = 120;
    /** How often a critical disease costs health, in seconds, and one at its height a wound's. */
    static final int CRITICAL_HURT_SECONDS = 20;
    static final int WOUND_HURT_SECONDS = 30;
    /** A wound infection short of critical does not take the last of a player's health. */
    static final float WOUND_FLOOR = 6.0f;

    public static final AttachmentType<Health> HEALTH = AttachmentRegistry.create(Hardwrought.id("health"), builder ->
            builder.persistent(Health.CODEC).initializer(() -> Health.NONE)
                    .syncWith(Health.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    /** How long each player has been chilled, in seconds. Not saved: a cold is caught in one stretch. */
    private static final Map<UUID, Integer> CHILL = new HashMap<>();
    /** The last stage each disease was told at, so each change is told once. */
    private static final Map<UUID, Map<Disease, Disease.Stage>> TOLD = new HashMap<>();

    /** Chance of a stomach bug from eating these, raw or spoiled. */
    private static final Map<Identifier, Double> BAD_FOOD = Map.of(
            Identifier.withDefaultNamespace("chicken"), 0.30,
            Identifier.withDefaultNamespace("rotten_flesh"), 0.50,
            Identifier.withDefaultNamespace("beef"), 0.10,
            Identifier.withDefaultNamespace("porkchop"), 0.10,
            Identifier.withDefaultNamespace("mutton"), 0.10,
            Identifier.withDefaultNamespace("rabbit"), 0.10,
            Identifier.withDefaultNamespace("cod"), 0.05,
            Identifier.withDefaultNamespace("salmon"), 0.05,
            Identifier.withDefaultNamespace("spider_eye"), 0.20,
            Identifier.withDefaultNamespace("poisonous_potato"), 0.30);

    /** Bread found in an Underworld ruin: hard, a little mouldy. */
    private static final Double STALE_BREAD = 0.25;

    private Diseases() { }

    /** Touching the class registers the attachments. */
    public static void initialize() {
        Appearance.SHOWN.identifier();
    }

    /** How long a new or reborn body is proof against everything, in ticks: forty minutes. */
    public static final int IMMUNITY_TICKS = 40 * 60 * 20;

    /**
     * Forty minutes in which nothing is caught and no wound turns, for a player who has just arrived or
     * just come back from death — time to find clean water and lay in bandages before any of it counts.
     * Not in Ultra, where the world gives no grace at all.
     */
    public static void grantImmunity(ServerPlayer player) {
        if (de.ipnats.hardwrought.survival.Ultra.active(player.level().getServer())) return;
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                de.ipnats.hardwrought.core.registry.ModEffects.IMMUNITY, IMMUNITY_TICKS, 0, false, false, true));
    }

    public static Health health(Player player) {
        Health health = player.getAttached(HEALTH);
        return health == null ? Health.NONE : health;
    }

    public static void set(Player player, Health health) {
        if (health.infections().isEmpty() && health.immunity().isEmpty() && health.known().isEmpty()) {
            player.removeAttached(HEALTH);
        } else if (!health.equals(player.getAttached(HEALTH))) {
            player.setAttached(HEALTH, health);
        }
    }

    public static boolean sick(Player player) {
        return health(player).sick();
    }

    // ---------------------------------------------------------------- catching

    /**
     * Catches a disease, or makes one already there worse by this much. A body that has just thrown it
     * off is proof against it for a while. Whether a player can fall ill at all — not in creative, not as
     * a spectator — is for the caller, which already knows.
     */
    public static void infect(ServerPlayer player, Disease disease, double load) {
        // In a fight the blood is up: nothing takes hold while adrenaline runs (see Adrenaline). A wound
        // still open afterwards can still turn.
        if (player.hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.ADRENALINE)) return;
        // A fresh start is not undone by the first bad drink: nothing takes hold, no wound turns.
        if (player.hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.IMMUNITY)) return;
        Health health = health(player);
        if (health.immune(disease)) return;
        Health.Infection present = health.infection(disease);
        Health.Infection next = present == null ? new Health.Infection(disease, 0, load, load)
                : new Health.Infection(disease, present.age(), Math.min(MOST, present.load() + load),
                Math.max(present.peak(), Math.min(MOST, present.load() + load)));
        set(player, health.with(disease, next));
    }

    /** Something eaten: raw meat and spoiled food can upset the stomach. */
    public static void ate(ServerPlayer player, ItemStack stack) {
        Double chance = stack.is(de.ipnats.hardwrought.underworld.UnderworldRuins.STALE_BREAD) ? STALE_BREAD
                : BAD_FOOD.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (chance != null && player.getRandom().nextDouble() < chance) infect(player, Disease.GASTRO, 10);
    }

    /** Bad water drunk: what used to be a spell of thirst is a stomach bug now. */
    public static void drankBad(ServerPlayer player, double load) {
        infect(player, Disease.GASTRO, load);
    }

    /**
     * Treatment that takes some of a disease away at once — a wound cleaned with spirit or antiseptic.
     * Nothing is taken from a disease not yet felt.
     */
    public static void treat(ServerPlayer player, Disease disease, double load) {
        Health health = health(player);
        Health.Infection present = health.infection(disease);
        if (present == null) return;
        set(player, health.with(disease, new Health.Infection(disease, present.age(),
                Math.max(0, present.load() - load), present.peak())));
    }

    /** A burn or a wound that has gone bad. */
    public static void wounded(ServerPlayer player, double load) {
        infect(player, Disease.WOUND, load);
    }

    // ---------------------------------------------------------------- what being ill does

    /** How much hungrier a player is for being ill. */
    public static double hungerFactor(Player player) {
        return sick(player) ? SICK_HUNGER : 1.0;
    }

    /** How much wearier. */
    public static double fatigueFactor(Player player) {
        return sick(player) ? SICK_FATIGUE : 1.0;
    }

    /** How much slower their breath comes back. */
    public static double recoveryFactor(Player player) {
        return sick(player) ? SICK_RECOVERY : 1.0;
    }

    /** Whether lying in bed too long makes this player restless. Not while ill: they need the rest. */
    public static boolean restless(Player player) {
        return !sick(player);
    }

    /** How many degrees of fever the body is driven towards, from whatever it is fighting. */
    public static double fever(Player player) {
        double fever = 0;
        for (Health.Infection infection : health(player).infections()) {
            if (!infection.felt() || infection.disease() == Disease.GASTRO) continue;
            fever = Math.max(fever, 0.8 + 2.2 * Math.min(MOST, infection.load()) / MOST);
        }
        // Willow bark brings a fever down; the pills made of it, further.
        var willow = player.getEffect(ModEffects.WILLOW_BARK);
        return willow == null ? fever : fever * (willow.getAmplifier() >= 1 ? 0.2 : 0.4);
    }

    // ---------------------------------------------------------------- the course of it

    /**
     * One second of being ill, or of catching it: called from the survival metabolism with the body as
     * it is. Grows or throws off every disease, does what each does, and tells the player what changed.
     */
    public static void tick(ServerPlayer player, PlayerVitals vitals, boolean resting, long second) {
        chill(player, vitals);
        Health health = health(player).immunityPassed();
        for (Health.Infection infection : health.infections()) {
            Health.Infection next = advance(player, vitals, resting, infection);
            if (next.age() >= next.disease().incubation() && next.load() <= 0) {
                health = health.recovered(next.disease(), IMMUNE_SECONDS);
                told(player, next.disease(), null);
                continue;
            }
            health = health.with(next.disease(), next);
            symptoms(player, next, second);
            told(player, next.disease(), next.stage());
        }
        set(player, health);
        Appearance.update(player);
    }

    private static Health.Infection advance(ServerPlayer player, PlayerVitals vitals, boolean resting,
                                            Health.Infection infection) {
        Disease disease = infection.disease();
        int age = infection.age() + 1;
        if (age < disease.incubation()) return new Health.Infection(disease, age, infection.load(), infection.peak());
        // The body's fight: the disease grows less and less as it goes on.
        double since = age - disease.incubation();
        double growth = disease.growth() * Math.max(0, 1 - since / disease.course()) + neglect(player, vitals, disease);
        double load = Math.clamp(infection.load() + growth - disease.recovery() * care(player, vitals, resting, disease), 0, MOST);
        return new Health.Infection(disease, age, load, Math.max(infection.peak(), load));
    }

    /** How well the body is looked after: sleep and rest, water, food, warmth, and the right tea. */
    static double care(ServerPlayer player, PlayerVitals vitals, boolean resting, Disease disease) {
        double care = 1.0;
        if (player.isSleeping()) care *= 3.0;
        else if (resting) care *= 1.3;
        if (vitals.hydration() < 30) care *= 0.6;
        if (player.getFoodData().getFoodLevel() < 6) care *= 0.6;
        // Cold or overheated beyond what the fever itself accounts for.
        if (Math.abs(vitals.bodyTemperature() - 37.0 - fever(player)) > 1.5) care *= 0.5;
        if (disease == Disease.GASTRO && player.hasEffect(ModEffects.CHAMOMILE)) care *= 3.0;
        var willow = player.getEffect(ModEffects.WILLOW_BARK);
        if (disease != Disease.GASTRO && willow != null) care *= willow.getAmplifier() >= 1 ? 2.5 : 2.0;
        if (player.hasEffect(ModEffects.ANTIBIOTIC)) care *= disease == Disease.GASTRO ? 2.0 : 4.0;
        return care;
    }

    /** What makes a disease worse besides itself: a cold caught keeps growing in the cold and wet. */
    private static double neglect(ServerPlayer player, PlayerVitals vitals, Disease disease) {
        if (disease == Disease.COLD && (vitals.bodyTemperature() < 35.5 || vitals.wetness() > 0.6)) return 0.15;
        if (disease == Disease.GASTRO && vitals.hydration() < 20) return 0.10;
        return 0;
    }

    /** Long enough cold, or cold and wet, and a cold is caught. */
    private static void chill(ServerPlayer player, PlayerVitals vitals) {
        boolean chilled = vitals.bodyTemperature() < 35.5 || vitals.wetness() > 0.6 && vitals.bodyTemperature() < 36.5;
        int seconds = CHILL.getOrDefault(player.getUUID(), 0);
        seconds = chilled ? seconds + 1 : Math.max(0, seconds - 2);
        if (seconds >= CHILL_TO_CATCH) {
            infect(player, Disease.COLD, 5);
            seconds = 0;
        }
        if (seconds == 0) CHILL.remove(player.getUUID());
        else CHILL.put(player.getUUID(), seconds);
    }

    /** What each disease does while it is felt. */
    private static void symptoms(ServerPlayer player, Health.Infection infection, long second) {
        if (!infection.felt()) return;
        var random = player.getRandom();
        switch (infection.disease()) {
            case GASTRO -> {
                // It costs water: the thirst a bad drink used to give, for as long as the bug lasts.
                player.addEffect(new MobEffectInstance(ModEffects.THIRST, 40, infection.load() >= SEVERE ? 1 : 0,
                        false, false, true));
                if (infection.load() >= 40 && random.nextDouble() < 1.0 / 30) {
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, false, false, true));
                }
            }
            case COLD -> {
                if (random.nextDouble() < (infection.load() >= SEVERE ? 1.0 / 12 : 1.0 / 25)) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PANDA_SNEEZE,
                            SoundSource.PLAYERS, 0.6f, 0.6f);
                    var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.find(player.level().getServer());
                    if (runtime != null) runtime.survival().afflict(player, 0, 0, -6);
                }
            }
            case WOUND -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false, true));
                if (infection.load() >= 60 && infection.load() < CRITICAL && second % WOUND_HURT_SECONDS == 0
                        && player.getHealth() > WOUND_FLOOR) {
                    ail(player, 1.0f);
                }
            }
        }
        if (infection.load() >= CRITICAL && second % CRITICAL_HURT_SECONDS == 0) ail(player, 1.0f);
    }

    /** Dying of an illness, as the death message has it. */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> ILLNESS =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, Hardwrought.id("illness"));

    /**
     * Health an illness costs. Taken from within, not dealt as a blow: it does not wake a sleeper, who is
     * where a sick body should be. Only the last of it is dealt as damage, so the death is a death.
     */
    static void ail(ServerPlayer player, float amount) {
        if (player.getHealth() > amount) {
            player.setHealth(player.getHealth() - amount);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT,
                    SoundSource.PLAYERS, 0.6f, 0.8f);
            return;
        }
        var level = player.level();
        player.hurtServer(level, new net.minecraft.world.damagesource.DamageSource(
                level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(ILLNESS)),
                amount);
    }

    /** Tells the player once when a disease takes hold, worsens, turns, or is gone. */
    private static void told(ServerPlayer player, Disease disease, Disease.Stage stage) {
        Map<Disease, Disease.Stage> told = TOLD.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        Disease.Stage was = stage == null ? told.remove(disease) : told.put(disease, stage);
        if (was == stage) return;
        boolean known = health(player).known().contains(disease);
        Component name = Component.translatable(disease.translationKey(stage == null ? Disease.Stage.SYMPTOMS : stage));
        Component message = stage == null
                ? Component.translatable("message.hardwrought.disease.recovered")
                : switch (stage) {
                    case INCUBATING -> null;
                    case SYMPTOMS -> was == null || was == Disease.Stage.INCUBATING
                            ? known ? Component.translatable("message.hardwrought.disease.caught", name)
                                    : Component.translatable("message.hardwrought.disease.unwell." + disease.serializedName())
                            : null;
                    case SEVERE -> Component.translatable("message.hardwrought.disease.worse");
                    case CRITICAL -> Component.translatable("message.hardwrought.disease.critical");
                    case RECOVERING -> was == Disease.Stage.RECOVERING ? null : Component.translatable("message.hardwrought.disease.better");
                };
        if (message != null) {
            player.sendSystemMessage(message.copy().withStyle(stage == Disease.Stage.CRITICAL ? ChatFormatting.RED
                    : stage == null || stage == Disease.Stage.RECOVERING ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }
    }

    /** Forgets what is only held while a player is about. */
    public static void forget(UUID player) {
        CHILL.remove(player);
        TOLD.remove(player);
    }
}
