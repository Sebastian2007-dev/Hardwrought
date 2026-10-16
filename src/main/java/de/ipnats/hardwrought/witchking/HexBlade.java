package de.ipnats.hardwrought.witchking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One of the Witch King's spectral swords. It hangs in the air first — circling him, or poised above its
 * victim, its point turned on them — and then it flies, straight and fast, and whatever it strikes takes
 * magic harm. A magic shield stops it like any other projectile. It shatters on stone.
 *
 * <p>Drawn on the client by the FX module as a sword of light ({@code hex_blade.fsh}); the entity itself
 * has no model.
 */
public class HexBlade extends Projectile {
    public static final int HANGING = 0, FLYING = 1;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(HexBlade.class, EntityDataSerializers.INT);
    static final float DAMAGE = 7.0f;
    static final int MAX_AGE = 240;
    /** Ticks of hanging before it flies, when it is not held by the king. */
    int poise = -1;

    public HexBlade(EntityType<? extends HexBlade> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, HANGING);
    }

    public boolean flying() {
        return entityData.get(STATE) == FLYING;
    }

    /** Turns its point along a direction; how it is drawn while it hangs. */
    public void point(Vec3 direction) {
        Vec3 d = direction.normalize();
        setYRot((float) (Mth.atan2(d.x, d.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    /** Where its point is turned. */
    public Vec3 pointing() {
        float yaw = getYRot() * Mth.DEG_TO_RAD, pitch = getXRot() * Mth.DEG_TO_RAD;
        return new Vec3(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
    }

    /** Lets it go, along the way it points, at this speed. */
    public void loose(Vec3 direction, double speed) {
        point(direction);
        setDeltaMovement(direction.normalize().scale(speed));
        entityData.set(STATE, FLYING);
        noPhysics = false;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 0.9f, 1.6f + random.nextFloat() * 0.3f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > MAX_AGE) {
            shatter();
            return;
        }
        if (!flying()) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide() && poise > 0 && --poise == 0) loose(pointing(), 1.9);
            return;
        }
        HitResult hit = level().isClientSide() ? null : ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        Vec3 movement = getDeltaMovement();
        setPos(position().add(movement));
        if (hit != null && isAlive() && hit.getType() != HitResult.Type.MISS) hitTargetOrDeflectSelf(hit);
        if (level().isClientSide() && random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof WitchKing) && !(entity instanceof HexBlade);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel level)) return;
        Entity owner = getOwner();
        DamageSource source = damageSources().indirectMagic(this, owner instanceof LivingEntity living ? living : this);
        result.getEntity().hurtServer(level, source, DAMAGE);
        shatter();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        shatter();
    }

    /** Breaks into light and is gone. */
    void shatter() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 10, 0.15, 0.15, 0.15, 0.08);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 14, 0.2, 0.2, 0.2, 0.2);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.0f, 0.7f + random.nextFloat() * 0.3f);
        }
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
    }
}
