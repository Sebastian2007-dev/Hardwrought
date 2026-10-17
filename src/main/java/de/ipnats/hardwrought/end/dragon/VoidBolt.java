package de.ipnats.hardwrought.end.dragon;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A bolt of void the dragon looses: fast, turning a little after its mark for a moment, and bursting
 * where it strikes — or when it has flown far enough — into a cloud of void that hangs in the air. It
 * can be shot down, which bursts it where it is.
 *
 * <p>Drawn by the FX module ({@code DragonFx}); the entity itself has no model.
 */
public class VoidBolt extends Projectile {
    public static final EntityType<VoidBolt> TYPE = register();
    static final double SPEED = 1.5, TURN = 0.05;
    static final int MAX_AGE = 90, HOMING = 26;
    static final float CLOUD_RADIUS = 3.2f;
    static final int CLOUD_TICKS = 140;
    /** Whom it turns after, if anyone. */
    private Entity mark;

    public VoidBolt(EntityType<? extends VoidBolt> type, Level level) {
        super(type, level);
    }

    private static EntityType<VoidBolt> register() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Hardwrought.id("void_bolt"));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<VoidBolt>of(VoidBolt::new, MobCategory.MISC)
                .sized(0.8f, 0.8f).clientTrackingRange(10).updateInterval(1).noSave().build(key));
    }

    static void initialize() { }

    /** Looses one from a place at a mark, from this dragon. */
    static VoidBolt loose(ServerLevel level, EnderDragon dragon, Vec3 from, Vec3 towards, Entity mark, double speed) {
        VoidBolt bolt = new VoidBolt(TYPE, level);
        bolt.setOwner(dragon);
        bolt.setPos(from);
        bolt.mark = mark;
        bolt.setDeltaMovement(towards.subtract(from).normalize().scale(speed));
        level.addFreshEntity(bolt);
        return bolt;
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
        if (mark != null && mark.isAlive() && tickCount < HOMING) {
            Vec3 want = mark.getBoundingBox().getCenter().subtract(position()).normalize().scale(getDeltaMovement().length());
            setDeltaMovement(getDeltaMovement().scale(1 - TURN).add(want.scale(TURN)));
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        setPos(position().add(getDeltaMovement()));
        if (hit.getType() != HitResult.Type.MISS && isAlive()) hitTargetOrDeflectSelf(hit);
        if (tickCount % 2 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 2, 0.15, 0.15, 0.15, 0.02);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof EnderDragon) && !(entity instanceof EnderDragonPart) && !(entity instanceof VoidBolt);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel level)) return;
        if (getOwner() instanceof EnderDragon dragon && result.getEntity() instanceof LivingEntity victim) {
            if (VoidDragon.voidHarm(level, dragon, this, victim, VoidDragon.BOLT)) VoidDragon.hurl(victim, position(), 1.4, 0.5);
        }
        burst(level);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel level) burst(level);
    }

    /** Bursts into a cloud of void hanging where it was. */
    void burst(ServerLevel level) {
        if (isRemoved()) return;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.7f);
        if (getOwner() instanceof EnderDragon dragon) DragonHazards.cloud(level, dragon, position(), CLOUD_RADIUS, CLOUD_TICKS);
        discard();
    }

    /** Shot down: it bursts where it is. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof EnderDragon) return false;
        burst(level);
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }
}
