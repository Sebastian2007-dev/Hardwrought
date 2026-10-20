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

import java.util.List;

/**
 * A burden laid on one player that falls on everyone standing with them when it comes down, shared
 * out evenly: alone, they take all of it; with three friends at their side, a quarter each. The
 * answer is to gather round whoever carries it, the opposite of what a boss otherwise teaches, which
 * is to spread out.
 *
 * <p>The Wither's (see {@code WitherPowers}): its souls bear down on one of its enemies.
 */
public final class SharedBurden implements Hazard {
    public static final int TICKS = 120;
    public static final double RADIUS = 4;

    private final ServerPlayer bearer;
    private final LivingEntity source;
    private final ResourceKey<DamageType> type;
    private final float total;
    private int ticks, shared;

    public SharedBurden(ServerPlayer bearer, LivingEntity source, ResourceKey<DamageType> type, float total) {
        this.bearer = bearer;
        this.source = source;
        this.type = type;
        this.total = total;
    }

    @Override
    public boolean tick(ServerLevel level, Encounter encounter) {
        if (!bearer.isAlive() || bearer.level() != level || !source.isAlive()) return true;
        ticks++;
        if (ticks == 1) {
            level.playSound(null, bearer.getX(), bearer.getY(), bearer.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2.0f, 0.5f);
            bearer.addEffect(new MobEffectInstance(MobEffects.GLOWING, TICKS, 0, false, false));
            for (ServerPlayer player : encounter.active()) {
                player.sendOverlayMessage(player == bearer
                        ? Component.translatable("message.hardwrought.encounter.burden_you").withStyle(ChatFormatting.DARK_AQUA)
                        : Component.translatable("message.hardwrought.encounter.burden", bearer.getDisplayName()).withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        if (ticks % 4 == 0) {
            Hazard.ring(level, ParticleTypes.SOUL_FIRE_FLAME, bearer.position(), RADIUS, 24);
            level.sendParticles(ParticleTypes.SOUL, bearer.getX(), bearer.getY() + 2.4, bearer.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
        }
        if (ticks < TICKS) return false;
        List<ServerPlayer> sharers = new java.util.ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class,
                bearer.getBoundingBox().inflate(RADIUS + 1, 3, RADIUS + 1),
                player -> Encounter.counts(player) && Math.hypot(player.getX() - bearer.getX(), player.getZ() - bearer.getZ()) <= RADIUS));
        if (!sharers.contains(bearer)) sharers.add(bearer);
        shared = sharers.size();
        float each = total / shared;
        level.sendParticles(ParticleTypes.SCULK_SOUL, bearer.getX(), bearer.getY() + 1, bearer.getZ(), 60, RADIUS / 2, 0.6, RADIUS / 2, 0.05);
        level.playSound(null, bearer.getX(), bearer.getY(), bearer.getZ(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.5f, 0.6f);
        for (ServerPlayer sharer : sharers) {
            Rending.rend(level, sharer, type, source, source, each, 0, 0.5f);
            // Carried alone, it lingers.
            if (shared == 1) sharer.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
        }
        return true;
    }

    public ServerPlayer bearer() {
        return bearer;
    }

    /** How many stood together under it when it came down; 0 before that. For tests. */
    public int shared() {
        return shared;
    }
}
