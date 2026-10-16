package de.ipnats.hardwrought.witchking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A soul the Witch King sends after someone: slow, turning after them as they run, bursting cold on what
 * it reaches — magic harm and limbs gone heavy. It can be outrun for a while, and struck out of the air.
 */
public class SoulOrb extends Projectile {
    static final float DAMAGE = 5.0f;
    static final double SPEED = 0.32;
    static final int MAX_AGE = 180;

    public SoulOrb(EntityType<? extends SoulOrb> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (tickCount > MAX_AGE) {
            burst(level);
            return;
        }
        // Turns after the king's target, never sharply.
        if (getOwner() instanceof WitchKing king && king.getTarget() != null) {
            Vec3 want = king.getTarget().getEyePosition().subtract(position()).normalize().scale(SPEED);
            setDeltaMovement(getDeltaMovement().scale(0.92).add(want.scale(0.08)));
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        setPos(position().add(getDeltaMovement()));
        if (hit.getType() != HitResult.Type.MISS && isAlive()) hitTargetOrDeflectSelf(hit);
        if (tickCount % 3 == 0) level.sendParticles(ParticleTypes.SOUL, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.01);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof WitchKing) && !(entity instanceof SoulOrb) && !(entity instanceof HexBlade);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel level)) return;
        Entity owner = getOwner();
        DamageSource source = damageSources().indirectMagic(this, owner instanceof LivingEntity living ? living : this);
        if (result.getEntity().hurtServer(level, source, DAMAGE) && result.getEntity() instanceof LivingEntity victim) {
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this);
        }
        burst(level);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel level) burst(level);
    }

    void burst(ServerLevel level) {
        level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.06);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.2f, 0.6f);
        discard();
    }

    /** Struck out of the air. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof WitchKing) return false;
        burst(level);
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }
}
