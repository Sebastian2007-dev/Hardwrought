package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModEffects;
import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The veil: true invisibility. Vanilla's invisibility leaves armour and whatever is held hanging in
 * the air; a veiled body shows nothing at all — every client simply does not draw it — and no
 * monster can set on it or keep a hold on it. What it does not hide is sound: footsteps and the
 * noise of a fight still carry, so a monster may come looking where it heard something.
 *
 * <p>It is a way out, not a way in: the moment its bearer strikes at anyone, it falls away.
 */
public final class Veil {
    /** Whether an entity is veiled, told to every client, which then does not draw it. */
    public static final AttachmentType<Boolean> VEILED = AttachmentRegistry.<Boolean>create(Hardwrought.id("veiled"),
            builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

    /** How far around a veiled body monsters lose their hold on it. */
    private static final double FORGET = 48;

    private static final Set<LivingEntity> VEILED_NOW = Collections.newSetFromMap(new WeakHashMap<>());

    private Veil() { }

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (LivingEntity entity : java.util.List.copyOf(VEILED_NOW)) {
                if (entity.isRemoved() || !entity.isAlive() || !entity.hasEffect(ModEffects.VEILED)) {
                    clear(entity);
                } else if (entity.tickCount % 10 == 0 && entity.level() instanceof ServerLevel level) {
                    forget(level, entity);
                }
            }
        });
        // Striking at anyone, by hand, by arrow or by spell, tears the veil.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, taken, blocked) -> {
            if (source.getEntity() instanceof LivingEntity attacker && attacker != victim && veiled(attacker)) lift(attacker);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> VEILED_NOW.clear());
    }

    /** Whether a body is veiled; on the client, as far as the server has told it. */
    public static boolean veiled(Entity entity) {
        return Boolean.TRUE.equals(entity.getAttached(VEILED));
    }

    /** Veils a body for so many ticks, or for longer if it is veiled longer already. */
    public static void apply(LivingEntity entity, int ticks) {
        boolean fresh = !veiled(entity);
        entity.addEffect(new MobEffectInstance(ModEffects.VEILED, ticks, 0, false, false, true));
        // Vanilla invisibility as well, for everything that asks after it: mobs' sight, and particles.
        entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, false));
        entity.setAttached(VEILED, true);
        VEILED_NOW.add(entity);
        if (entity.level() instanceof ServerLevel level) {
            forget(level, entity);
            if (fresh) {
                Vec3 at = entity.position();
                Fx.play(level, FxEffect.VEIL, at, at, Rune.DARK.color(), 1, entity);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 0.7f);
            }
        }
    }

    /** Tears the veil: its bearer can be seen again at once. */
    public static void lift(LivingEntity entity) {
        if (!veiled(entity)) return;
        entity.removeEffect(ModEffects.VEILED);
        entity.removeEffect(MobEffects.INVISIBILITY);
        clear(entity);
        if (entity.level() instanceof ServerLevel level) {
            Vec3 at = entity.position().add(0, 1, 0);
            Fx.play(level, FxEffect.SPARKLE, at, at, Rune.DARK.color(), 0.8f, null);
        }
    }

    private static void clear(LivingEntity entity) {
        entity.removeAttached(VEILED);
        VEILED_NOW.remove(entity);
    }

    /** Every monster that had set on the veiled body loses it. */
    private static void forget(ServerLevel level, LivingEntity veiled) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(veiled.blockPosition()).inflate(FORGET),
                mob -> mob.getTarget() == veiled)) {
            mob.setTarget(null);
        }
    }
}
