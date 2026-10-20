package de.ipnats.hardwrought.end.dragon;

import de.ipnats.hardwrought.encounter.Encounter;
import de.ipnats.hardwrought.encounter.Encounters;
import de.ipnats.hardwrought.encounter.Tether;
import de.ipnats.hardwrought.end.BrokenIsland;
import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The dragon's own AI, in place of every phase vanilla gives it but dying (see {@link VoidDragon}).
 * Vanilla still flies it — towards {@link #getFlyTargetLocation()}, at {@link #getFlySpeed()} — and this
 * decides where, and what it does on the way.
 *
 * <p>Between attacks it circles the arena. Then it chooses what to do next by weight, from what the
 * stage of the fight and the distance to its prey allow, never the same twice in a row:
 *
 * <ul>
 *   <li><b>Bolts</b> — passing its prey at a distance, it looses bolts of void that burst into clouds.</li>
 *   <li><b>Gust</b> — it comes up close, holds, and beats its wings: a cone of wind that throws anyone in
 *   it far away and down.</li>
 *   <li><b>Dive</b> — it climbs above its prey, marks where it will strike, and falls on that place.</li>
 *   <li><b>Clouds</b> — it flies through the air near its prey, breathing clouds of void that close off
 *   the way.</li>
 *   <li><b>Summon</b> — it roars, and endermen come out on the island near its prey.</li>
 *   <li><b>Landing</b> — from {@link VoidDragon#LANDING_AT}: it comes down on the middle of the island,
 *   stamps the ground until it shakes loose at its edges, and sweeps its breath over it; struck hard
 *   enough, or after a while, it rises again.</li>
 *   <li><b>Skyfall</b> — raised again only: marks round everyone it fights, and bolts falling out of the
 *   sky on them.</li>
 * </ul>
 *
 * Raging, the island breaks up piece by piece and the void pulls at whoever is in the air.
 */
public class DragonHunt extends AbstractDragonPhaseInstance {
    private enum Move { PATROL, BOLTS, GUST, DIVE, CLOUDS, SUMMON, LAND, SKYFALL }

    static final float PATROL_SPEED = 0.75f, CHASE_SPEED = 1.05f, DIVE_SPEED = 3.2f;
    static final double PATROL_RADIUS = 58, GUST_REACH = 28, GUST_CONE = Math.toRadians(38), STOMP_REACH = 11;
    static final int GUST_CHARGE = 18, LOCK_TICKS = 24, LAND_TICKS = 260, MARK_TICKS = 22;
    static final int MOST_ENDERMEN = 6;

    private final RandomSource random = RandomSource.create();
    private boolean begun, raged;
    private Move move = Move.PATROL, last;
    private int moveTicks, rest = 60;
    private Vec3 target;
    private float speed = PATROL_SPEED, turn = 1.4f;
    private ServerPlayer prey;
    private double circle;
    private int crystals;
    // Bolts.
    private int boltsLeft, boltCooldown;
    // Gust.
    private int gustCharge;
    // Dive.
    private int dive;
    private Vec3 lockAt, diveDir;
    private int lockTicks;
    private boolean wake;
    // Clouds.
    private int cloudsLeft, cloudCooldown;
    // Landing.
    private boolean landed;
    private Vec3 landing;
    private int landTicks, stompTimer, sweepTimer, landCooldown = 400;
    private float landedDamage;
    private final List<Vec3> sweep = new ArrayList<>();
    // Raging, and the rest.
    private int crumbleTimer = 100, pullTimer = 80, summonCooldown = 300;
    private final Set<Integer> crumbled = new HashSet<>();
    /** Its fight, and who is in it. */
    private Encounter fight;
    /** How long it waits, deserted, before it is whole again: five minutes, time to come back from a death and a long way. */
    static final int RESET_TICKS = 6000;
    /** Ticks between its threads of the void, in company. */
    static final int TETHER_EVERY = 520;

    public DragonHunt(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public EnderDragonPhase<? extends DragonPhaseInstance> getPhase() {
        return VoidDragon.HUNT;
    }

    @Override
    public boolean isSitting() {
        return landed;
    }

    @Override
    public Vec3 getFlyTargetLocation() {
        return landed ? null : target;
    }

    @Override
    public float getFlySpeed() {
        return speed;
    }

    @Override
    public float getTurnSpeed() {
        float base = super.getTurnSpeed();
        return base * turn;
    }

    /** Whether it is falling on something this moment, for what its head does to what it meets. */
    boolean diving() {
        return move == Move.DIVE && dive == 3;
    }

    @Override
    public float onHurt(DamageSource source, float amount) {
        if (crystals > 0) {
            Vec3 at = dragon.position().add(0, 2, 0);
            if (dragon.level() instanceof ServerLevel level) Fx.play(level, FxEffect.DRAGON_SHIELDED, at, at, 0, 1, null);
            return amount * VoidDragon.SHIELDED;
        }
        if (landed) landedDamage += amount;
        return amount;
    }

    // ------------------------------------------------------------------------------------ thinking

    @Override
    public void doServerTick(ServerLevel level) {
        if (!begun) {
            begun = true;
            circle = random.nextDouble() * Math.PI * 2;
            var fight = dragon.getDragonFight();
            if (fight != null && fight.hasPreviouslyKilledDragon()) VoidDragon.rise(dragon);
        }
        fight = Encounters.of(dragon, () -> VoidDragon.middle(dragon), VoidDragon.REACH, RESET_TICKS);
        crystals = crystals(level);
        int stage = VoidDragon.stage(dragon, crystals);
        // In company, from the hunt on: a thread of the void between two of them, to be pulled apart.
        if (stage >= 2 && fight.crowded() && fight.active().size() >= 2 && !fight.running(Tether.class) && fight.ready("tether", TETHER_EVERY)) {
            tether(level);
        }
        if (landCooldown > 0) landCooldown--;
        if (summonCooldown > 0) summonCooldown--;
        prey = choosePrey(level);
        if (stage == 4) rage(level);
        if (landed) {
            landedTick(level, stage);
            return;
        }
        if (prey == null && move != Move.PATROL) begin(Move.PATROL, 60);
        moveTicks++;
        boolean done = switch (move) {
            case PATROL -> patrol();
            case BOLTS -> bolts(level, stage);
            case GUST -> gust(level);
            case DIVE -> dive(level, stage);
            case CLOUDS -> clouds(level);
            case SUMMON -> summon(level);
            case LAND -> approachLanding(level, stage);
            case SKYFALL -> skyfall(level);
        };
        if (!done) return;
        if (move != Move.PATROL) {
            begin(Move.PATROL, rest(stage));
        } else if (prey != null) {
            Move next = choose(stage);
            begin(next, 0);
        } else {
            begin(Move.PATROL, 60);
        }
    }

    private void begin(Move next, int patrolTicks) {
        if (next != Move.PATROL) last = next;
        move = next;
        moveTicks = 0;
        rest = patrolTicks;
        turn = 1.4f;
        switch (next) {
            case BOLTS -> {
                boltsLeft = (VoidDragon.share(dragon) < VoidDragon.RAGE_AT ? 6 : 4) + (VoidDragon.risen(dragon) ? 2 : 0);
                boltCooldown = 0;
            }
            case GUST -> gustCharge = 0;
            case DIVE -> {
                dive = 1;
                wake = false;
            }
            case CLOUDS -> {
                cloudsLeft = 5;
                cloudCooldown = 0;
            }
            default -> { }
        }
    }

    /** How long it circles between attacks: shorter as the fight goes on, and raised again. */
    private int rest(int stage) {
        double tempo = switch (stage) {
            case 1 -> 1.0;
            case 2 -> 0.85;
            case 3 -> 0.75;
            default -> 0.55;
        };
        if (VoidDragon.risen(dragon)) tempo *= 0.8;
        return (int) Math.round((50 + random.nextInt(40)) * tempo);
    }

    private Move choose(int stage) {
        boolean near = prey != null && dragon.distanceTo(prey) < 45;
        boolean risen = VoidDragon.risen(dragon);
        Move[] moves = Move.values();
        int[] weights = new int[moves.length];
        weights[Move.BOLTS.ordinal()] = 3;
        weights[Move.GUST.ordinal()] = near ? 3 : 1;
        weights[Move.DIVE.ordinal()] = stage == 1 ? 1 : stage == 4 ? 4 : 3;
        weights[Move.CLOUDS.ordinal()] = stage >= 2 ? 2 : 0;
        weights[Move.SUMMON.ordinal()] = stage >= 2 && summonCooldown <= 0 ? 1 : 0;
        weights[Move.LAND.ordinal()] = stage >= 3 && landCooldown <= 0 ? 4 : 0;
        weights[Move.SKYFALL.ordinal()] = risen && stage >= 2 ? 2 : 0;
        int total = 0;
        for (Move m : moves) if (m != last && m != Move.PATROL) total += weights[m.ordinal()];
        if (total <= 0) return Move.BOLTS;
        int roll = random.nextInt(total);
        for (Move m : moves) {
            if (m == last || m == Move.PATROL) continue;
            roll -= weights[m.ordinal()];
            if (roll < 0) return m;
        }
        return Move.BOLTS;
    }

    private ServerPlayer choosePrey(ServerLevel level) {
        List<ServerPlayer> foes = VoidDragon.foes(level, dragon);
        // With more of them it turns from one to another more often.
        int keep = 400 / (fight == null ? 1 : fight.fighters());
        if (prey != null && foes.contains(prey) && random.nextInt(keep) != 0) return prey;
        ServerPlayer best = null;
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer foe : foes) {
            double d = foe.distanceToSqr(dragon);
            if (d < nearest) {
                nearest = d;
                best = foe;
            }
        }
        return best;
    }

    private int crystals(ServerLevel level) {
        var fight = dragon.getDragonFight();
        if (fight != null) return fight.aliveCrystals();
        return level.getEntitiesOfClass(EndCrystal.class, new AABB(dragon.getFightOrigin()).inflate(100, 120, 100)).size();
    }

    private Vec3 middle() {
        return VoidDragon.middle(dragon);
    }

    private void fly(Vec3 to, float how) {
        double y = Mth.clamp(to.y, 30, 150);
        target = new Vec3(to.x, y, to.z);
        speed = how;
    }

    private Vec3 head() {
        return dragon.head.position();
    }

    // ------------------------------------------------------------------------------------ moves

    /** Circling the arena, rising and falling. Done when it has rested long enough. */
    private boolean patrol() {
        circle += 0.012;
        Vec3 m = middle();
        double ahead = circle + 0.35;
        fly(new Vec3(m.x + Math.cos(ahead) * PATROL_RADIUS, m.y + 20 + 12 * Math.sin(circle * 2), m.z + Math.sin(ahead) * PATROL_RADIUS), PATROL_SPEED);
        return moveTicks >= rest;
    }

    /** Passing its prey at a distance, loosing bolts as it goes. */
    private boolean bolts(ServerLevel level, int stage) {
        Vec3 p = prey.getEyePosition();
        Vec3 toward = p.subtract(dragon.position()).multiply(1, 0, 1);
        Vec3 side = toward.lengthSqr() < 1 ? new Vec3(1, 0, 0) : new Vec3(-toward.z, 0, toward.x).normalize();
        fly(p.add(side.scale(24)).add(0, 12, 0), CHASE_SPEED);
        if (--boltCooldown <= 0 && dragon.distanceTo(prey) < 70 && head().distanceToSqr(p) < dragon.position().distanceToSqr(p)) {
            // In company every other bolt goes to someone else.
            ServerPlayer aim = prey;
            if (fight != null && fight.crowded() && boltsLeft % 2 == 0) {
                ServerPlayer other = fight.another(random, prey);
                if (other != null) aim = other;
            }
            Vec3 lead = aim.getEyePosition().add(aim.getDeltaMovement().scale(8));
            VoidBolt.loose(level, dragon, head(), lead, aim, VoidBolt.SPEED * (VoidDragon.risen(dragon) ? 1.2 : 1));
            level.playSound(null, head().x, head().y, head().z, SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 3.0f, 0.7f);
            boltCooldown = stage == 4 ? 6 : 9;
            boltsLeft--;
        }
        return boltsLeft <= 0 || moveTicks > 180;
    }

    /** Up close, holding, and then a beat of its wings that throws everyone before it away. */
    private boolean gust(ServerLevel level) {
        Vec3 p = prey.position();
        if (gustCharge == 0) {
            Vec3 back = dragon.position().subtract(p).multiply(1, 0, 1);
            back = back.lengthSqr() < 1 ? new Vec3(1, 0, 0) : back.normalize();
            fly(p.add(back.scale(16)).add(0, 4, 0), CHASE_SPEED);
            if (head().distanceTo(prey.getEyePosition()) < GUST_REACH - 4) {
                gustCharge = GUST_CHARGE;
                Fx.play(level, FxEffect.DRAGON_GUST_CHARGE, head(), head().add(0, GUST_CHARGE, 0), 0, 1, dragon);
                level.playSound(null, head().x, head().y, head().z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0f, 0.8f);
            }
            return moveTicks > 200;
        }
        // Holding where it is while it draws breath under its wings.
        fly(dragon.position().add(prey.position().subtract(dragon.position()).normalize().scale(1.5)), 0.15f);
        if (--gustCharge > 0) return false;
        Vec3 from = head();
        Vec3 dir = prey.getEyePosition().subtract(from).normalize();
        for (ServerPlayer foe : VoidDragon.foes(level, dragon)) {
            Vec3 to = foe.getBoundingBox().getCenter().subtract(from);
            if (to.length() > GUST_REACH || Math.acos(Mth.clamp(to.normalize().dot(dir), -1, 1)) > GUST_CONE) continue;
            VoidDragon.voidHarm(level, dragon, dragon, foe, VoidDragon.GUST);
            VoidDragon.hurl(foe, from, 3.2, 0.7);
        }
        Fx.play(level, FxEffect.DRAGON_GUST, from, from.add(dir.scale(GUST_REACH)), 0, 1, null);
        for (int i = 0; i < 3; i++) level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 5.0f, 0.5f + i * 0.1f);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
        return true;
    }

    /** Above its prey, a mark where it will strike, and then the fall. */
    private boolean dive(ServerLevel level, int stage) {
        switch (dive) {
            case 1 -> {
                Vec3 above = prey.position().add(0, 30, 0);
                fly(above, CHASE_SPEED * 1.15f);
                if (dragon.position().distanceTo(above) < 14 || moveTicks > 140) {
                    dive = 2;
                    lockTicks = stage == 4 || VoidDragon.risen(dragon) ? 16 : LOCK_TICKS;
                    lockAt = prey.position().add(0, 1, 0).add(prey.getDeltaMovement().scale(6));
                    Fx.play(level, FxEffect.DRAGON_LOCK, lockAt, lockAt.add(0, lockTicks, 0), 0, 5, null);
                    level.playSound(null, lockAt.x, lockAt.y, lockAt.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0f, 1.2f);
                }
                return false;
            }
            case 2 -> {
                turn = 3f;
                fly(dragon.position().add(lockAt.subtract(dragon.position()).normalize().scale(2)), 0.25f);
                if (--lockTicks > 0) return false;
                dive = 3;
                diveDir = lockAt.subtract(head()).normalize();
                level.playSound(null, head().x, head().y, head().z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0f, 0.5f);
                return false;
            }
            default -> {
                turn = 3f;
                fly(lockAt.add(diveDir.scale(30)), DIVE_SPEED);
                if (!wake && head().distanceTo(lockAt) < 6) {
                    // Where it struck, the air itself is torn: whoever is near is thrown.
                    wake = true;
                    Fx.play(level, FxEffect.DRAGON_STOMP, lockAt, lockAt, 0, 6, null);
                    for (ServerPlayer foe : level.getEntitiesOfClass(ServerPlayer.class, AABB.ofSize(lockAt, 12, 12, 12))) {
                        if (foe.isCreative() || foe.isSpectator() || foe.position().distanceTo(lockAt) > 6) continue;
                        if (VoidDragon.voidHarm(level, dragon, dragon, foe, VoidDragon.GUST)) VoidDragon.hurl(foe, lockAt, 2.0, 0.8);
                    }
                }
                boolean passed = dragon.position().subtract(lockAt).dot(diveDir) > 14;
                if (!passed && moveTicks < 260) return false;
                // Raging or raised again, it may climb straight into another.
                if ((stage == 4 || VoidDragon.risen(dragon)) && random.nextInt(5) < 2) {
                    dive = 1;
                    wake = false;
                    moveTicks = 0;
                    return false;
                }
                return true;
            }
        }
    }

    /** Through the air near its prey, breathing clouds that close off the way. */
    private boolean clouds(ServerLevel level) {
        Vec3 p = prey.getEyePosition();
        Vec3 beyond = p.add(p.subtract(dragon.position()).multiply(1, 0, 1).normalize().scale(22));
        fly(new Vec3(beyond.x, p.y + 3, beyond.z), CHASE_SPEED);
        if (--cloudCooldown <= 0 && dragon.distanceTo(prey) < 45) {
            Vec3 at = head().add(head().subtract(dragon.position()).normalize().scale(4));
            DragonHazards.cloud(level, dragon, at, 3.5f, 160);
            cloudCooldown = 9;
            cloudsLeft--;
        }
        return cloudsLeft <= 0 || moveTicks > 160;
    }

    /** A roar, and endermen on the island near its prey. */
    private boolean summon(ServerLevel level) {
        fly(dragon.position().add(0, 2, 0), 0.2f);
        if (moveTicks == 1) {
            Fx.play(level, FxEffect.DRAGON_ROAR, head(), head(), 0, 1, dragon);
            level.playSound(null, head().x, head().y, head().z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0f, 0.6f);
            int there = level.getEntitiesOfClass(Enderman.class, new AABB(dragon.getFightOrigin()).inflate(140, 120, 140)).size();
            List<BrokenIsland.Fragment> near = new ArrayList<>();
            for (BrokenIsland.Fragment f : BrokenIsland.fragments(level.getSeed(), net.minecraft.world.level.levelgen.feature.EndSpikeFeature.getSpikesForLevel(level))) {
                if (f.radius() >= 3 && Math.hypot(f.x() - prey.getX(), f.z() - prey.getZ()) < 45) near.add(f);
            }
            int extra = fight == null ? 0 : fight.fighters() - 1;
            for (int i = 0; i < 3 + extra && there < MOST_ENDERMEN + 2 * extra && !near.isEmpty(); i++) {
                BrokenIsland.Fragment f = near.get(random.nextInt(near.size()));
                double a = random.nextDouble() * Math.PI * 2, d = random.nextDouble() * f.radius() * 0.5;
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(f.x() + Math.cos(a) * d, 0, f.z() + Math.sin(a) * d));
                if (at.getY() <= level.getMinY() + 1) continue;
                Enderman enderman = EntityTypes.ENDERMAN.spawn(level, at, EntitySpawnReason.REINFORCEMENT);
                if (enderman == null) continue;
                enderman.setTarget(prey);
                there++;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 40, 0.4, 1, 0.4, 0.2);
            }
            summonCooldown = 600;
        }
        return moveTicks > 30;
    }

    /** Raised again: marks round everyone it fights, and bolts falling out of the sky on them. */
    private boolean skyfall(ServerLevel level) {
        patrolNearPrey();
        if (moveTicks == 1) {
            level.playSound(null, head().x, head().y, head().z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0f, 0.4f);
            int foes = 0, most = 3 + (fight == null ? 0 : fight.fighters() - 1);
            for (ServerPlayer foe : VoidDragon.foes(level, dragon)) {
                if (foes++ >= most) break;
                for (int i = 0; i < 6; i++) {
                    double a = random.nextDouble() * Math.PI * 2, d = 1 + random.nextDouble() * 6;
                    Vec3 spot = foe.position().add(Math.cos(a) * d, 0.5 + random.nextDouble() * 2, Math.sin(a) * d);
                    int delay = MARK_TICKS + i * 3;
                    Fx.play(level, FxEffect.DRAGON_LOCK, spot, spot.add(0, delay, 0), 0, 2, null);
                    DragonHazards.later(level, delay, l -> {
                        if (dragon.isAlive()) VoidBolt.loose(l, dragon, spot.add(0, 32, 0), spot, null, 2.4);
                    });
                }
            }
        }
        return moveTicks > MARK_TICKS + 30;
    }

    private void patrolNearPrey() {
        circle += 0.02;
        Vec3 p = prey.position();
        fly(new Vec3(p.x + Math.cos(circle) * 30, p.y + 22, p.z + Math.sin(circle) * 30), PATROL_SPEED);
    }

    // ------------------------------------------------------------------------------------ the landing

    private boolean approachLanding(ServerLevel level, int stage) {
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dragon.getFightOrigin());
        landing = Vec3.atBottomCenterOf(ground);
        double flat = Math.hypot(dragon.getX() - landing.x, dragon.getZ() - landing.z);
        fly(flat > 10 ? landing.add(0, 16, 0) : landing, flat > 10 ? CHASE_SPEED : 0.6f);
        if (dragon.position().distanceTo(landing) < 3) {
            land(level, stage);
            return false;
        }
        return moveTicks > 320;
    }

    private void land(ServerLevel level, int stage) {
        landed = true;
        dragon.setDeltaMovement(Vec3.ZERO);
        landTicks = stage == 4 ? 200 : LAND_TICKS;
        stompTimer = 50;
        sweepTimer = 110;
        landedDamage = 0;
        Fx.play(level, FxEffect.DRAGON_LAND, landing, landing, 0, 12, null);
        level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 5.0f, 0.4f);
        level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0f, 0.6f);
        quake(level, 9, VoidDragon.STOMP * 0.5f);
    }

    private void landedTick(ServerLevel level, int stage) {
        dragon.setDeltaMovement(dragon.getDeltaMovement().scale(0.5));
        if (prey != null) {
            // Its head turned on its prey.
            double dx = prey.getX() - dragon.getX(), dz = prey.getZ() - dragon.getZ();
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) + 90;
            dragon.setYRot(Mth.approachDegrees(dragon.getYRot(), want, 4));
        }
        if (--stompTimer == MARK_TICKS) {
            Fx.play(level, FxEffect.HEX_MARK, landing, landing.add(0, MARK_TICKS, 0), VoidDragon.MAGENTA, (float) STOMP_REACH, null);
        } else if (stompTimer <= 0) {
            quake(level, STOMP_REACH, VoidDragon.STOMP);
            stompTimer = stage == 4 ? 70 : 95;
        }
        if (--sweepTimer == MARK_TICKS) {
            // Where its breath will lie: an arc before it, towards its prey.
            sweep.clear();
            Vec3 toward = prey != null ? prey.position().subtract(landing).multiply(1, 0, 1) : new Vec3(1, 0, 0);
            double a0 = Math.atan2(toward.z, toward.x);
            for (int i = 0; i < 7; i++) {
                double a = a0 + Math.toRadians(-54 + i * 18);
                Vec3 spot = landing.add(Math.cos(a) * 8, 1.2, Math.sin(a) * 8);
                sweep.add(spot);
                Fx.play(level, FxEffect.HEX_MARK, spot.add(0, -1.15, 0), spot.add(0, -1.15 + MARK_TICKS, 0), VoidDragon.VIOLET, 2.6f, null);
            }
        } else if (sweepTimer <= 0) {
            for (Vec3 spot : sweep) DragonHazards.cloud(level, dragon, spot, 2.8f, 110);
            level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0f, 1.0f);
            sweepTimer = 150;
        }
        if (--landTicks > 0 && landedDamage < dragon.getMaxHealth() * 0.15f) return;
        // Up again.
        landed = false;
        landCooldown = stage == 4 ? 360 : 520;
        Fx.play(level, FxEffect.DRAGON_ROAR, head(), head(), 0, 1, dragon);
        quake(level, 8, 0);
        level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 6.0f, 0.6f);
        fly(landing.add(0, 30, 0), CHASE_SPEED);
        begin(Move.PATROL, 30);
    }

    /** The ground shaken under it: whoever stands near is thrown up and hurt, and the edges come loose. */
    private void quake(ServerLevel level, double reach, float damage) {
        Fx.play(level, FxEffect.DRAGON_STOMP, landing, landing, 0, (float) reach, null);
        level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
        for (ServerPlayer foe : level.getEntitiesOfClass(ServerPlayer.class, AABB.ofSize(landing, reach * 2, 8, reach * 2))) {
            if (foe.isCreative() || foe.isSpectator()) continue;
            if (Math.hypot(foe.getX() - landing.x, foe.getZ() - landing.z) > reach || foe.getY() - landing.y > 4) continue;
            if (damage > 0) VoidDragon.blow(level, dragon, foe, damage);
            VoidDragon.hurl(foe, landing, 1.4, -0.9);
        }
        BrokenIsland.Fragment middle = BrokenIsland.fragments(level.getSeed(),
                net.minecraft.world.level.levelgen.feature.EndSpikeFeature.getSpikesForLevel(level)).getFirst();
        if (damage > 0) DragonHazards.shakeLoose(level, middle, 14, random);
    }

    // ------------------------------------------------------------------------------------ raging

    private void rage(ServerLevel level) {
        if (!raged) {
            raged = true;
            Fx.play(level, FxEffect.DRAGON_ROAR, head(), head(), 0, 3, dragon);
            level.playSound(null, head().x, head().y, head().z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0f, 0.4f);
            for (ServerPlayer foe : VoidDragon.foes(level, dragon)) {
                foe.sendOverlayMessage(Component.translatable("message.hardwrought.dragon.rage").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        if (--crumbleTimer <= 0) {
            crumbleTimer = VoidDragon.risen(dragon) ? 180 : 230;
            List<BrokenIsland.Fragment> fragments = BrokenIsland.fragments(level.getSeed(),
                    net.minecraft.world.level.levelgen.feature.EndSpikeFeature.getSpikesForLevel(level));
            int pick = -1;
            double best = Double.MAX_VALUE;
            for (int i = 1; i < fragments.size(); i++) {
                BrokenIsland.Fragment f = fragments.get(i);
                if (crumbled.contains(i) || f.radius() < 3) continue;
                double d = prey == null ? random.nextDouble() * 100 : Math.hypot(f.x() - prey.getX(), f.z() - prey.getZ()) + random.nextDouble() * 30;
                if (d < best) {
                    best = d;
                    pick = i;
                }
            }
            if (pick > 0) {
                crumbled.add(pick);
                DragonHazards.crumble(level, fragments.get(pick), 0);
            }
            DragonHazards.shakeLoose(level, fragments.getFirst(), 10, random);
        }
        if (--pullTimer <= 0) {
            pullTimer = 70;
            for (ServerPlayer foe : VoidDragon.foes(level, dragon)) {
                if (foe.onGround()) continue;
                Fx.play(level, FxEffect.VOID_PULL, foe.position(), foe.position(), 0, 1, foe);
                foe.setDeltaMovement(foe.getDeltaMovement().add(0, -0.7, 0));
                foe.needsSync = true;
            }
        }
    }

    /** Two of them bound by a thread of the void; if it holds, both are torn and thrown down. */
    private void tether(ServerLevel level) {
        ServerPlayer a = fight.another(random, null);
        ServerPlayer b = fight.another(random, a);
        if (a == null || b == null || a == b) return;
        fight.add(new Tether(a, b, dragon, (l, foe) -> {
            Fx.play(l, FxEffect.VOID_PULL, foe.position(), foe.position(), 0, 1, foe);
            if (VoidDragon.voidHarm(l, dragon, dragon, foe, VoidDragon.BOLT)) VoidDragon.hurl(foe, foe.position().add(0, 2, 0), 0.4, 1.0);
        }));
    }

    // ------------------------------------------------------------------------------------ for tests

    /** Whether it is on the ground in the middle of the island; for tests. */
    public boolean landedForTesting() {
        return landed;
    }

    /** Makes it do this next, by name (bolts, gust, dive, clouds, summon, land, skyfall); for tests. */
    public void testMove(String name) {
        begin(Move.valueOf(name.toUpperCase(java.util.Locale.ROOT)), 0);
        landCooldown = 0;
    }

    /** Sets it down at once; for tests. */
    public void landForTesting(ServerLevel level) {
        landing = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dragon.getFightOrigin()));
        dragon.snapTo(landing.x, landing.y, landing.z, dragon.getYRot(), 0);
        land(level, VoidDragon.stage(dragon, crystals(level)));
    }
}
