package de.ipnats.hardwrought.encounter;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;

/**
 * A bond laid between two players that has to be stretched until it snaps: while they stay within
 * {@link #SNAP} of each other it holds, and when it runs out both of them pay. The answer is for the
 * two to pull apart, quickly, and through whatever the boss is doing between them.
 *
 * <p>The dragon's: a thread of the void between two of its enemies.
 */
public final class Tether implements Hazard {
    public static final int TICKS = 160;
    public static final double SNAP = 18;

    private final ServerPlayer a, b;
    private final LivingEntity source;
    private final BiConsumer<ServerLevel, ServerPlayer> punish;
    private int ticks;
    private boolean snapped, held;

    /** {@code punish} is what happens to each of the two if it holds to the end. */
    public Tether(ServerPlayer a, ServerPlayer b, LivingEntity source, BiConsumer<ServerLevel, ServerPlayer> punish) {
        this.a = a;
        this.b = b;
        this.source = source;
        this.punish = punish;
    }

    @Override
    public boolean tick(ServerLevel level, Encounter encounter) {
        if (!a.isAlive() || !b.isAlive() || a.level() != level || b.level() != level || !source.isAlive()) return true;
        ticks++;
        if (ticks == 1) {
            level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 2.0f, 0.5f);
            level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 2.0f, 0.5f);
            a.sendOverlayMessage(Component.translatable("message.hardwrought.encounter.tether", b.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            b.sendOverlayMessage(Component.translatable("message.hardwrought.encounter.tether", a.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        double apart = a.getEyePosition().distanceTo(b.getEyePosition());
        if (apart >= SNAP) {
            snapped = true;
            level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0f, 0.6f);
            level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0f, 0.6f);
            a.sendOverlayMessage(Component.translatable("message.hardwrought.encounter.tether_snapped").withStyle(ChatFormatting.GRAY));
            b.sendOverlayMessage(Component.translatable("message.hardwrought.encounter.tether_snapped").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (ticks % 3 == 0) draw(level, apart);
        if (ticks < TICKS) return false;
        held = true;
        level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 2.0f, 0.5f);
        punish.accept(level, a);
        punish.accept(level, b);
        return true;
    }

    /** The thread between them: pale while there is time, darker as it runs out. */
    private void draw(ServerLevel level, double apart) {
        Vec3 from = a.position().add(0, a.getBbHeight() * 0.6, 0), to = b.position().add(0, b.getBbHeight() * 0.6, 0);
        int points = (int) Math.min(48, Math.max(4, apart / 0.6));
        boolean late = ticks > TICKS * 2 / 3;
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.lerp(to, i / (double) points);
            level.sendParticles(late ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Whether the two pulled it apart; for tests. */
    public boolean snapped() {
        return snapped;
    }

    /** Whether it held to the end and struck; for tests. */
    public boolean held() {
        return held;
    }
}
