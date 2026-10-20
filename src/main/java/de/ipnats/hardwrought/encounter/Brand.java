package de.ipnats.hardwrought.encounter;

import de.ipnats.hardwrought.combat.Rending;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * A brand burned onto one player that bursts out of them when it is done: the one who carries it is
 * only singed, but whoever stands near them is torn. The answer is for the bearer to walk away from
 * their friends, out of the middle of a fight, in a hall that is breaking open under them.
 *
 * <p>The Witch King's: a sigil of his on one of his enemies.
 */
public final class Brand implements Hazard {
    public static final int TICKS = 100;
    public static final double RADIUS = 5;
    /** What the bearer takes, against what everyone near them takes. */
    public static final float ON_BEARER = 3;

    private final ServerPlayer bearer;
    private final LivingEntity source;
    private final ResourceKey<DamageType> type;
    private final float damage;
    private final Consumer<Vec3> burst;
    private int ticks, caught = -1;

    /** {@code burst} draws the burst where it happens; it is given the bearer's feet. */
    public Brand(ServerPlayer bearer, LivingEntity source, ResourceKey<DamageType> type, float damage, Consumer<Vec3> burst) {
        this.bearer = bearer;
        this.source = source;
        this.type = type;
        this.damage = damage;
        this.burst = burst;
    }

    @Override
    public boolean tick(ServerLevel level, Encounter encounter) {
        if (!bearer.isAlive() || bearer.level() != level || !source.isAlive()) return true;
        ticks++;
        if (ticks == 1) {
            level.playSound(null, bearer.getX(), bearer.getY(), bearer.getZ(), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.HOSTILE, 2.0f, 0.5f);
            bearer.addEffect(new MobEffectInstance(MobEffects.GLOWING, TICKS, 0, false, false));
            for (ServerPlayer player : encounter.active()) {
                player.sendOverlayMessage(player == bearer
                        ? Component.translatable("message.hardwrought.encounter.brand_you").withStyle(ChatFormatting.LIGHT_PURPLE)
                        : Component.translatable("message.hardwrought.encounter.brand", bearer.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        if (ticks % 4 == 0) {
            Hazard.ring(level, ParticleTypes.WITCH, bearer.position(), RADIUS, 28);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, bearer.getX(), bearer.getY() + 1, bearer.getZ(), 4, 0.3, 0.5, 0.3, 0.02);
        }
        if (ticks < TICKS) return false;
        burst.accept(bearer.position());
        level.playSound(null, bearer.getX(), bearer.getY(), bearer.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2.5f, 0.5f);
        Rending.rend(level, bearer, type, source, source, ON_BEARER, 0, 0.5f);
        caught = 0;
        for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, bearer.getBoundingBox().inflate(RADIUS + 1, 3, RADIUS + 1))) {
            if (other == bearer || !Encounter.counts(other)) continue;
            if (Math.hypot(other.getX() - bearer.getX(), other.getZ() - bearer.getZ()) > RADIUS) continue;
            caught++;
            if (Rending.rend(level, other, type, source, source, damage, 0, 0.5f)) {
                other.setDeltaMovement(other.getDeltaMovement().add(0, 0.6, 0));
                other.needsSync = true;
            }
        }
        return true;
    }

    public ServerPlayer bearer() {
        return bearer;
    }

    /** How many others it caught when it burst; -1 before that. For tests. */
    public int caught() {
        return caught;
    }
}
