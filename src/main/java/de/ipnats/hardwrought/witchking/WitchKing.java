package de.ipnats.hardwrought.witchking;

import de.ipnats.hardwrought.underworld.GraveUndead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The Witch King, at the bottom of the Underworld. A boss of magic in three phases:
 *
 * <ol>
 *   <li><b>Elusive</b> (full health to two thirds): the size of a man, hovering, keeping his distance. He
 *   circles himself with spectral swords and looses them one after another; whoever comes near, he slips
 *   away from through a rift, and blows of the hand barely touch him. He is meant to be shot down, as
 *   the Wither is.</li>
 *   <li><b>Warded</b>: at two thirds he rises over the middle of his hall, wrapped in a ward nothing gets
 *   through, and raises four soul crystals on their pedestals. He cannot move while they stand; he rains
 *   swords on his enemies from sigils over their heads, sends souls after them, calls his dead up out of
 *   the floor. Every crystal broken cracks the ward; the fourth breaks it.</li>
 *   <li><b>Unbound</b>: he grows to a giant and comes down on whoever is left — great blows, shockwaves
 *   that throw everything away from him, rifts that put him behind them. But now he can be struck.</li>
 * </ol>
 *
 * And twice in every phase after the first he gathers a curse over the whole hall and lets it fall: only a
 * magic shield stands between it and the one it falls on. That is what makes him a boss of magic.
 *
 * <p>The server decides everything; what is seen of it — rifts, sigils, the ward, the swords, the
 * shockwaves — the client draws from his synchronised state and from the events he sends
 * ({@link #CLIENT_EVENTS}).
 */
public class WitchKing extends Monster {
    // Entity events for the client's effects.
    public static final byte EV_BLADES = 100, EV_TELEPORT_OUT = 101, EV_TELEPORT_IN = 102, EV_WAVE_CHARGE = 103, EV_WAVE = 104,
            EV_DOOM_CHARGE = 105, EV_DOOM = 106, EV_PHASE_TWO = 107, EV_PHASE_THREE = 108, EV_CRYSTAL = 109, EV_SUMMON = 110,
            EV_DEATH = 111, EV_SLAM = 112, EV_ENTRANCE = 113, EV_ORBS = 114, EV_RAIN = 115, EV_WARD_HIT = 116;
    /** Set by the client: what it shows for each event. */
    public static BiConsumer<WitchKing, Byte> CLIENT_EVENTS = (king, event) -> { };

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> HOME = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> CRYSTALS = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.INT);
    /** Ticks of the casting pose left: arms raised. */
    private static final EntityDataAccessor<Integer> CAST = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.INT);
    /** Ticks of a gathering curse or shockwave, counting down; 0 when none. */
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.INT);

    public static final float PHASE_TWO_AT = 0.66f;
    public static final double GIANT = 3.5;
    public static final int ENTRANCE_TICKS = 60, GROWTH_TICKS = 60, DEATH_TICKS = 90;
    public static final int DOOM_CHARGE = 80, WAVE_CHARGE = 30;
    static final float DOOM_DAMAGE = 18, WAVE_DAMAGE = 8, SLAM_DAMAGE = 16;
    static final double ARENA = 15, DOOM_REACH = 40, WAVE_REACH = 14;
    /** Where the four crystals stand, from the middle of the hall. */
    public static final BlockPos[] CRYSTAL_SPOTS = {new BlockPos(10, 1, 10), new BlockPos(-10, 1, 10), new BlockPos(10, 1, -10), new BlockPos(-10, 1, -10)};

    private final ServerBossEvent boss = new ServerBossEvent(Mth.createInsecureUUID(random), Component.translatable("entity.hardwrought.witch_king"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final List<HexBlade> held = new ArrayList<>();
    private BlockPos throne;
    private int entrance, growth, idle;
    private int bladeTimer = 60, teleportTimer = 240, teleportCooldown, rainTimer, orbTimer, summonTimer, doomTimer, waveTimer, meleeCooldown;
    private boolean slipAway;
    private char charging;

    public WitchKing(EntityType<? extends WitchKing> type, Level level) {
        super(type, level);
        setNoGravity(true);
        xpReward = 500;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 500.0).add(Attributes.ARMOR, 8.0).add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, SLAM_DAMAGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 1);
        builder.define(HOME, BlockPos.ZERO);
        builder.define(CRYSTALS, 0);
        builder.define(CAST, 0);
        builder.define(CHARGE, 0);
    }

    // ------------------------------------------------------------------------------------ state

    public int phase() {
        return entityData.get(PHASE);
    }

    public BlockPos home() {
        return entityData.get(HOME);
    }

    public int crystals() {
        return entityData.get(CRYSTALS);
    }

    public int casting() {
        return entityData.get(CAST);
    }

    public int charge() {
        return entityData.get(CHARGE);
    }

    public boolean growing() {
        return growth > 0;
    }

    /** Places him in his hall: its middle, and the throne he answers to. */
    public void settle(BlockPos home, BlockPos throne) {
        entityData.set(HOME, home);
        this.throne = throne;
    }

    /** Rising from his throne: some seconds of untouchable entrance. */
    public void enter() {
        entrance = ENTRANCE_TICKS;
        level().broadcastEntityEvent(this, EV_ENTRANCE);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 0.5f);
    }

    private Vec3 homeVec() {
        return Vec3.atBottomCenterOf(home());
    }

    private void cast(int ticks) {
        entityData.set(CAST, Math.max(casting(), ticks));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= EV_BLADES && id <= EV_WARD_HIT) {
            if (level().isClientSide()) CLIENT_EVENTS.accept(this, id);
            return;
        }
        super.handleEntityEvent(id);
    }

    // ------------------------------------------------------------------------------------ thinking

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % 10 == 0) updateBossBar(level);
        boss.setProgress(getHealth() / getMaxHealth());
        if (casting() > 0) entityData.set(CAST, casting() - 1);
        if (teleportCooldown > 0) teleportCooldown--;
        if (meleeCooldown > 0) meleeCooldown--;
        held.removeIf(blade -> !blade.isAlive() || blade.flying());
        if (entrance > 0) {
            entrance--;
            flyToward(homeVec().add(0, 4, 0), 0.08);
            return;
        }
        // What goes on whether anyone is there or not: growing, and the crystals being broken.
        if (growth > 0) {
            grow(level);
            return;
        }
        if (phase() == 2 && watchCrystals(level)) return;
        LivingEntity target = findTarget(level);
        setTarget(target);
        if (target == null) {
            idle(level);
            return;
        }
        idle = 0;
        getLookControl().setLookAt(target, 40, 40);
        setYBodyRot(getYHeadRot());
        holdBlades(target);
        if (charge() > 0) {
            entityData.set(CHARGE, charge() - 1);
            if (charge() == 0) release(level, target);
            hover(target);
            return;
        }
        switch (phase()) {
            case 1 -> elusive(level, target);
            case 2 -> warded(level, target);
            default -> unbound(level, target);
        }
    }

    private LivingEntity findTarget(ServerLevel level) {
        Player nearest = null;
        double best = 48 * 48;
        for (Player player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            double d = player.distanceToSqr(homeVec());
            if (d < best) {
                best = d;
                nearest = player;
            }
        }
        return nearest;
    }

    private void updateBossBar(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(homeVec()) < 56 * 56) boss.addPlayer(player);
            else boss.removePlayer(player);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        boss.removeAllPlayers();
        for (HexBlade blade : held) blade.discard();
        super.remove(reason);
    }

    /** Nobody to fight: back over the middle of his hall, mending; long enough alone, back onto his throne. */
    private void idle(ServerLevel level) {
        flyToward(homeVec().add(0, 4, 0), 0.2);
        if (tickCount % 20 == 0) heal(2);
        if (++idle > 600 && throne != null && level.getBlockEntity(throne) instanceof WitchKingThrone.Entity seat) {
            seat.sleep();
            discard();
        }
    }

    // ------------------------------------------------------------------------------------ moving

    private void flyToward(Vec3 goal, double speed) {
        Vec3 d = goal.subtract(position());
        double length = d.length();
        Vec3 want = length < 0.15 ? Vec3.ZERO : d.scale(Math.min(speed, length * 0.3) / length);
        setDeltaMovement(getDeltaMovement().scale(0.7).add(want.scale(0.3 + 0.7 * speed)));
    }

    private Vec3 inArena(Vec3 at, double y) {
        Vec3 home = homeVec();
        Vec3 flat = new Vec3(at.x - home.x, 0, at.z - home.z);
        if (flat.length() > ARENA) flat = flat.normalize().scale(ARENA);
        return new Vec3(home.x + flat.x, y, home.z + flat.z);
    }

    private void hover(LivingEntity target) {
        double bob = Math.sin(tickCount * 0.07) * 0.4;
        if (phase() == 3) flyToward(inArena(position(), home().getY() + 0.05), 0.2);
        else if (phase() == 2) flyToward(homeVec().add(0, 6 + bob, 0), 0.3);
        else flyToward(new Vec3(getX(), home().getY() + 5 + bob, getZ()), 0.2);
    }

    /** Out through a rift, and in through another where he wants to be. */
    private void teleport(ServerLevel level, Vec3 near, double min, double max, boolean behind) {
        for (int attempt = 0; attempt < 24; attempt++) {
            Vec3 at;
            if (behind && getTarget() != null) {
                Vec3 back = getTarget().getLookAngle().multiply(1, 0, 1).normalize().scale(-(3 + getBbWidth()));
                at = inArena(getTarget().position().add(back), home().getY() + (phase() == 3 ? 0.05 : 3 + random.nextInt(4)));
            } else {
                double angle = random.nextDouble() * Math.PI * 2, distance = Mth.lerp(random.nextDouble(), min, max);
                at = inArena(near.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), home().getY() + 3 + random.nextInt(4));
            }
            AABB box = getDimensions(getPose()).makeBoundingBox(at);
            if (!level.noCollision(this, box)) continue;
            level.broadcastEntityEvent(this, EV_TELEPORT_OUT);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 2.0f, 0.6f);
            teleportTo(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
            level.broadcastEntityEvent(this, EV_TELEPORT_IN);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0f, 0.5f);
            teleportCooldown = 40;
            return;
        }
    }

    // ------------------------------------------------------------------------------------ the phases

    /** Phase 1: keeps away, slips off through rifts, looses swords. */
    private void elusive(ServerLevel level, LivingEntity target) {
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
        double bob = Math.sin(tickCount * 0.07) * 0.6;
        Vec3 perch = inArena(target.position().add(away.normalize().scale(14)), home().getY() + 5 + bob);
        flyToward(perch, 0.32);
        if ((slipAway || distanceTo(target) < 7) && teleportCooldown <= 0) {
            slipAway = false;
            teleport(level, target.position(), 11, 16, false);
        } else if (--teleportTimer <= 0) {
            teleportTimer = 200 + random.nextInt(120);
            teleport(level, target.position(), 11, 16, false);
        }
        if (--bladeTimer <= 0) {
            bladeTimer = 85 + random.nextInt(35);
            summonBlades(level, getHealth() < getMaxHealth() * 0.8f ? 4 : 3);
        }
    }

    private void startPhaseTwo(ServerLevel level) {
        entityData.set(PHASE, 2);
        setHealth(getMaxHealth() * PHASE_TWO_AT);
        for (BlockPos spot : CRYSTAL_SPOTS) {
            BlockPos at = home().offset(spot);
            level.setBlockAndUpdate(at, WitchKingContent.CRYSTAL.defaultBlockState());
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.3);
        }
        entityData.set(CRYSTALS, CRYSTAL_SPOTS.length);
        boss.setColor(BossEvent.BossBarColor.BLUE);
        level.broadcastEntityEvent(this, EV_PHASE_TWO);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 2.0f, 0.6f);
        for (HexBlade blade : held) blade.shatter();
        held.clear();
        rainTimer = 60;
        orbTimer = 120;
        summonTimer = 100;
        doomTimer = 240;
        cast(30);
    }

    /** Phase 2: still over the middle of the hall behind his ward; rains swords, sends souls, raises the dead. */
    /** Counts the crystals still standing; the last one gone, the ward breaks. Returns true when phase 3 has begun. */
    private boolean watchCrystals(ServerLevel level) {
        int standing = 0;
        for (BlockPos spot : CRYSTAL_SPOTS) if (level.getBlockState(home().offset(spot)).is(WitchKingContent.CRYSTAL)) standing++;
        if (standing >= crystals()) return false;
        entityData.set(CRYSTALS, standing);
        level.broadcastEntityEvent(this, EV_CRYSTAL);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 2.5f, 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
        if (standing > 0) return false;
        startPhaseThree(level);
        return true;
    }

    private void warded(ServerLevel level, LivingEntity target) {
        hover(target);
        if (--rainTimer <= 0) {
            rainTimer = 100 + random.nextInt(30);
            swordRain(level, target);
        }
        if (--orbTimer <= 0) {
            orbTimer = 130 + random.nextInt(40);
            souls(level);
        }
        if (--summonTimer <= 0) {
            summonTimer = 280;
            raiseDead(level);
        }
        if (--doomTimer <= 0) {
            doomTimer = 480;
            startCharge(level, 'D');
        }
    }

    private void startPhaseThree(ServerLevel level) {
        entityData.set(PHASE, 3);
        growth = GROWTH_TICKS;
        boss.setColor(BossEvent.BossBarColor.RED);
        level.broadcastEntityEvent(this, EV_PHASE_THREE);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 4.0f, 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 0.4f);
        cast(GROWTH_TICKS);
    }

    /** Growing to a giant, coming down to the floor as he does. */
    private void grow(ServerLevel level) {
        growth--;
        double progress = 1 - growth / (double) GROWTH_TICKS;
        getAttribute(Attributes.SCALE).setBaseValue(Mth.lerp(progress * progress * (3 - 2 * progress), 1.0, GIANT));
        flyToward(new Vec3(getX(), home().getY() + 0.05, getZ()), 0.15);
        if (growth % 10 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 20, getBbWidth(), getBbHeight() * 0.4, getBbWidth(), 0.05);
        if (growth == 0) {
            shockwave(level, 18, 6);
            waveTimer = 140;
            teleportTimer = 220;
            bladeTimer = 100;
            doomTimer = 400;
        }
    }

    /** Phase 3: a giant on the floor, pressing whoever is left; great blows, shockwaves, rifts behind them. */
    private void unbound(ServerLevel level, LivingEntity target) {
        Vec3 goal = inArena(target.position(), home().getY() + 0.05);
        double reach = getBbWidth() / 2 + target.getBbWidth() / 2 + 2.4;
        if (distanceTo(target) > reach * 0.8) flyToward(goal, 0.3);
        else setDeltaMovement(getDeltaMovement().scale(0.5));
        if (distanceTo(target) <= reach && meleeCooldown <= 0) {
            meleeCooldown = 32;
            cast(14);
            level.broadcastEntityEvent(this, EV_SLAM);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 2.0f, 0.6f);
            if (target.hurtServer(level, damageSources().mobAttack(this), SLAM_DAMAGE)) {
                Vec3 push = target.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.6);
                target.push(push.x, 0.5, push.z);
                target.needsSync = true;
            }
        }
        if (--waveTimer <= 0) {
            waveTimer = 150 + random.nextInt(40);
            startCharge(level, 'W');
        }
        if (--teleportTimer <= 0 && teleportCooldown <= 0) {
            teleportTimer = 240 + random.nextInt(80);
            teleport(level, target.position(), 0, 0, true);
        }
        if (--bladeTimer <= 0) {
            bladeTimer = 140 + random.nextInt(40);
            summonBlades(level, 3);
        }
        if (--doomTimer <= 0) {
            doomTimer = 560;
            startCharge(level, 'D');
        }
    }

    // ------------------------------------------------------------------------------------ attacks

    /** Swords appear circling him, then fly one by one. */
    private void summonBlades(ServerLevel level, int count) {
        level.broadcastEntityEvent(this, EV_BLADES);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 0.6f);
        cast(24);
        for (int i = 0; i < count; i++) {
            HexBlade blade = WitchKingContent.HEX_BLADE.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (blade == null) continue;
            blade.setOwner(this);
            blade.setPos(position().add(0, getBbHeight() * 0.6, 0));
            blade.poise = -1;
            level.addFreshEntity(blade);
            held.add(blade);
        }
    }

    /** The held swords circle him, points turned on the target, and go one after another. */
    private void holdBlades(LivingEntity target) {
        int n = held.size();
        for (int i = 0; i < n; i++) {
            HexBlade blade = held.get(i);
            double angle = tickCount * 0.12 + i * Math.PI * 2 / n;
            double r = 1.4 + getBbWidth();
            Vec3 at = position().add(Math.cos(angle) * r, getBbHeight() * 0.65 + Math.sin(tickCount * 0.2 + i) * 0.2, Math.sin(angle) * r);
            blade.setPos(at);
            Vec3 aim = target.getEyePosition().subtract(at);
            blade.point(aim);
            if (blade.tickCount >= 30 + i * 7) blade.loose(aim, 1.5);
        }
    }

    /** Sigils over the target's head, and swords falling out of them. */
    private void swordRain(ServerLevel level, LivingEntity target) {
        level.broadcastEntityEvent(this, EV_RAIN);
        cast(30);
        level.playSound(null, target.getX(), target.getY() + 8, target.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.5f, 0.5f);
        for (int i = 0; i < 6; i++) {
            HexBlade blade = WitchKingContent.HEX_BLADE.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (blade == null) continue;
            blade.setOwner(this);
            Vec3 at = target.position().add(random.nextDouble() * 6 - 3, 8 + random.nextDouble() * 2, random.nextDouble() * 6 - 3);
            blade.setPos(at);
            Vec3 aim = target.position().add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1).subtract(at);
            blade.point(aim);
            blade.poise = 22 + i * 5;
            level.addFreshEntity(blade);
        }
    }

    /** Souls sent after the target. */
    private void souls(ServerLevel level) {
        level.broadcastEntityEvent(this, EV_ORBS);
        cast(20);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
        for (int i = 0; i < 3; i++) {
            SoulOrb orb = WitchKingContent.SOUL_ORB.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (orb == null) continue;
            orb.setOwner(this);
            double angle = i * Math.PI * 2 / 3 + tickCount;
            orb.setPos(position().add(0, getBbHeight() * 0.6, 0));
            orb.setDeltaMovement(Math.cos(angle) * 0.4, 0.25, Math.sin(angle) * 0.4);
            level.addFreshEntity(orb);
        }
    }

    /** His dead, called up out of the floor of the hall. */
    private void raiseDead(ServerLevel level) {
        AABB hall = new AABB(home()).inflate(ARENA + 2, 8, ARENA + 2);
        int alive = level.getEntitiesOfClass(Mob.class, hall, mob -> mob instanceof GraveUndead.GraveWalker || mob instanceof GraveUndead.GraveSkeleton).size();
        if (alive >= 5) return;
        level.broadcastEntityEvent(this, EV_SUMMON);
        cast(30);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.5f, 0.6f);
        for (int i = 0; i < 2 + random.nextInt(2); i++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = 5 + random.nextDouble() * 9;
            Vec3 at = homeVec().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Mob dead = random.nextBoolean() ? GraveUndead.GRAVE_WALKER.create(level, EntitySpawnReason.MOB_SUMMONED)
                    : GraveUndead.GRAVE_SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (dead == null) continue;
            dead.snapTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
            dead.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
            level.addFreshEntity(dead);
            if (dead instanceof GraveUndead.GraveWalker walker) walker.rise();
            if (dead instanceof GraveUndead.GraveSkeleton skeleton) skeleton.rise();
        }
    }

    /** A curse ('D') or a shockwave ('W') gathers; it falls when the charge runs out. */
    private void startCharge(ServerLevel level, char kind) {
        charging = kind;
        int ticks = kind == 'D' ? DOOM_CHARGE : WAVE_CHARGE;
        entityData.set(CHARGE, ticks);
        cast(ticks);
        level.broadcastEntityEvent(this, kind == 'D' ? EV_DOOM_CHARGE : EV_WAVE_CHARGE);
        if (kind == 'D') {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3.0f, 0.5f);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.5f);
        } else {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3.0f, 0.6f);
        }
    }

    private void release(ServerLevel level, LivingEntity target) {
        if (charging == 'D') doom(level);
        else shockwave(level, WAVE_REACH, WAVE_DAMAGE);
        charging = 0;
    }

    /** The curse falls on the whole hall. Only a magic shield stands between it and anyone in it. */
    private void doom(ServerLevel level) {
        level.broadcastEntityEvent(this, EV_DOOM);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 4.0f, 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
        for (Player player : level.players()) {
            if (player.distanceToSqr(homeVec()) > DOOM_REACH * DOOM_REACH || player.isSpectator()) continue;
            player.hurtServer(level, damageSources().indirectMagic(this, this), DOOM_DAMAGE);
        }
    }

    /** Everything near him thrown away from him. */
    private void shockwave(ServerLevel level, double reach, float damage) {
        level.broadcastEntityEvent(this, EV_WAVE);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 0.7f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.6f);
        for (Entity entity : level.getEntities(this, getBoundingBox().inflate(reach), e -> e instanceof LivingEntity || e instanceof HexBlade)) {
            if (entity instanceof Player player && (player.isSpectator() || player.isCreative())) continue;
            Vec3 away = entity.position().subtract(position()).multiply(1, 0, 1);
            double distance = away.length();
            if (distance > reach) continue;
            double strength = 2.6 * (1 - distance / (reach + 1)) + 0.6;
            Vec3 push = (distance < 0.1 ? new Vec3(1, 0, 0) : away.normalize()).scale(strength);
            entity.setDeltaMovement(push.x, 0.7, push.z);
            entity.needsSync = true;
            if (entity instanceof LivingEntity living && !(entity instanceof GraveUndead.GraveWalker) && !(entity instanceof GraveUndead.GraveSkeleton)) {
                living.hurtServer(level, damageSources().indirectMagic(this, this), damage);
            }
        }
    }

    // ------------------------------------------------------------------------------------ being hurt

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (isDeadOrDying() || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)) return false;
        if (source.getEntity() instanceof WitchKing || source.getEntity() instanceof GraveUndead.GraveWalker || source.getEntity() instanceof GraveUndead.GraveSkeleton) return false;
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && (phase() == 2 || growth > 0 || entrance > 0)) {
            // The ward: it rings, and holds.
            if (source.getEntity() instanceof Player) {
                level.broadcastEntityEvent(this, EV_WARD_HIT);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0f, 0.5f);
            }
            return false;
        }
        if (phase() == 1 && !bypass) {
            boolean byHand = source.getDirectEntity() != null && source.getDirectEntity() == source.getEntity();
            if (byHand) {
                // Barely touched, and away.
                damage *= 0.25f;
                slipAway = true;
            }
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && phase() == 1 && getHealth() <= getMaxHealth() * PHASE_TWO_AT && isAlive()) startPhaseTwo(level);
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            level.broadcastEntityEvent(this, EV_DEATH);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 4.0f, 0.5f);
            for (BlockPos spot : CRYSTAL_SPOTS) {
                if (level.getBlockState(home().offset(spot)).is(WitchKingContent.CRYSTAL)) level.removeBlock(home().offset(spot), false);
            }
            if (throne != null && level.getBlockEntity(throne) instanceof WitchKingThrone.Entity seat) seat.defeated();
            boss.setProgress(0);
        }
    }

    /** Dying slowly: rising, light breaking out of him, and then gone in a burst of souls. */
    @Override
    protected void tickDeath() {
        deathTime++;
        setDeltaMovement(0, 0.02, 0);
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        if (level() instanceof ServerLevel level) {
            if (deathTime % 4 == 0) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, getBbWidth() * 0.5, getBbHeight() * 0.4,
                        getBbWidth() * 0.5, 0.04);
                level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.5, getZ(), 3, getBbWidth() * 0.5, getBbHeight() * 0.4,
                        getBbWidth() * 0.5, 0.1);
            }
            if (deathTime == DEATH_TICKS) {
                level.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 200, getBbWidth(), getBbHeight() * 0.5, getBbWidth(), 0.3);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.4f);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0f, 0.4f);
                remove(RemovalReason.KILLED);
            }
        }
    }

    // ------------------------------------------------------------------------------------ the rest

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("phase", phase());
        output.putLong("home", home().asLong());
        output.putInt("crystals", crystals());
        if (throne != null) output.putLong("throne", throne.asLong());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(PHASE, input.getIntOr("phase", 1));
        entityData.set(HOME, BlockPos.of(input.getLongOr("home", blockPosition().asLong())));
        entityData.set(CRYSTALS, input.getIntOr("crystals", 0));
        throne = input.getLong("throne").map(BlockPos::of).orElse(null);
        if (hasCustomName()) boss.setName(getDisplayName());
    }

    // ------------------------------------------------------------------------------------ for tests

    /** Sets how much health he has left, as a share; for tests. */
    public void testHealth(float share) {
        setHealth(getMaxHealth() * share);
    }
}
