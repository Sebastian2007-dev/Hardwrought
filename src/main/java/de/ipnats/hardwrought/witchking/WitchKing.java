package de.ipnats.hardwrought.witchking;

import de.ipnats.hardwrought.core.registry.ModDamageTypes;
import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import de.ipnats.hardwrought.underworld.GraveUndead;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The Witch King, at the bottom of the Underworld. A boss of magic in three phases:
 *
 * <ol>
 *   <li><b>Elusive</b> (full health to two thirds): the size of a man, hovering, keeping his distance. He
 *   looses spectral swords at his enemies — one after another, in a fan, or out of a ring closing round
 *   them — and slips away through rifts, out of reach and to their flank. Blows of the hand barely touch
 *   him. He is meant to be shot down, as the Wither is.</li>
 *   <li><b>Warded</b>: at two thirds he rises over the middle of his hall, wrapped in a ward nothing gets
 *   through, and raises four soul crystals on their pedestals. He cannot move while they stand; he rains
 *   swords on his enemies from sigils over their heads, sends souls after them, calls his dead up out of
 *   the floor, aims his lance at them and lets the floor break open along spokes running out from under
 *   him. Every crystal broken cracks the ward, makes him quicker and tears the floor open round the one
 *   who broke it; the fourth breaks the ward.</li>
 *   <li><b>Unbound</b>: he grows to a giant and comes down on whoever is left — blows wound up and
 *   struck in a row, leaps across the hall that land in a shockwave, lines of the floor breaking open
 *   towards them, rifts that put him behind them. But now he can be struck. Brought low, he rages, and
 *   everything comes faster.</li>
 * </ol>
 *
 * And again and again in every phase after the first he gathers a curse over the whole hall and lets it
 * fall: only a magic shield stands between it and the one it falls on. That is what makes him a boss of magic.
 *
 * <p>What he does next is not on a timetable: whenever he is free to, he chooses among what he can do,
 * by how far away his enemy is and how the fight goes, never the same twice in a row. Everything he aims
 * is shown before it strikes — a sigil on the floor, a line of light — long enough to get out of the way.
 *
 * <p>His magic tears at the soul ({@link #rend}): armour does not hold it, and protective runes only half.
 *
 * <p>The server decides everything; what is seen of it — rifts, sigils, the ward, the swords, the
 * shockwaves — the client draws from his synchronised state, from the events he sends
 * ({@link #CLIENT_EVENTS}) and from the FX module's Witch King effects.
 */
public class WitchKing extends Monster {
    // Entity events for the client's effects.
    public static final byte EV_BLADES = 100, EV_TELEPORT_OUT = 101, EV_TELEPORT_IN = 102, EV_WAVE_CHARGE = 103, EV_WAVE = 104,
            EV_DOOM_CHARGE = 105, EV_DOOM = 106, EV_PHASE_TWO = 107, EV_PHASE_THREE = 108, EV_CRYSTAL = 109, EV_SUMMON = 110,
            EV_DEATH = 111, EV_SLAM = 112, EV_ENTRANCE = 113, EV_ORBS = 114, EV_RAIN = 115, EV_WARD_HIT = 116, EV_LEAP = 117,
            EV_LAND = 118, EV_RAGE = 119, EV_SLAM_WINDUP = 120;
    /** Set by the client: what it shows for each event. */
    public static BiConsumer<WitchKing, Byte> CLIENT_EVENTS = (king, event) -> { };

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> HOME = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> CRYSTALS = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.INT);
    /** Ticks of the casting pose left: arms raised. */
    private static final EntityDataAccessor<Integer> CAST = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.INT);
    /** Ticks of a gathering curse or shockwave, counting down; 0 when none. */
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.INT);
    /** Brought low in his last phase: everything comes faster. */
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(WitchKing.class, EntityDataSerializers.BOOLEAN);

    public static final float PHASE_TWO_AT = 0.66f, RAGE_AT = 0.3f;
    public static final double GIANT = 3.5;
    public static final int ENTRANCE_TICKS = 60, GROWTH_TICKS = 60, DEATH_TICKS = 90;
    public static final int DOOM_CHARGE = 80, WAVE_CHARGE = 30;
    /** How long a mark lies on the floor before the floor breaks open under it. */
    public static final int MARK_TICKS = 22;
    public static final int LANCE_CHARGE = 30, LEAP_TICKS = 26, SLAM_WINDUP = 13;
    public static final int VIOLET = 0xB25CFF, SOUL = 0x5CE1FF, BLOOD = 0xFF3C6A;
    static final float DOOM_DAMAGE = 18, WAVE_DAMAGE = 10, SLAM_DAMAGE = 18, ERUPTION_DAMAGE = 9, LANCE_DAMAGE = 14, LAND_DAMAGE = 12;
    /** How much of armour holds against his blows; against his spells none of it does. */
    static final float SLAM_ARMOUR = 0.5f;
    /** How much of protective runes holds against anything of his. */
    static final float RUNE_SHARE = 0.5f;
    static final double ARENA = 15, DOOM_REACH = 40, WAVE_REACH = 14, LAND_REACH = 7, SLAM_REACH = 3.2, LANCE_WIDTH = 1.3;
    /** Where the four crystals stand, from the middle of the hall. */
    public static final BlockPos[] CRYSTAL_SPOTS = {new BlockPos(10, 1, 10), new BlockPos(-10, 1, 10), new BlockPos(10, 1, -10), new BlockPos(-10, 1, -10)};

    /** What he can choose to do when he is free to. */
    private enum Move { VOLLEY, FAN, RING, BLINK, RAIN, SOULS, RAISE, LANCE, SPOKES, LEAP, WAVE, TRAIL, RIFT_STRIKE }

    /** Something he set going that happens some ticks later: a mark turning into an eruption, a lance loosed. */
    private static final class Pending {
        int ticks;
        final Consumer<ServerLevel> action;

        Pending(int ticks, Consumer<ServerLevel> action) {
            this.ticks = ticks;
            this.action = action;
        }
    }

    private final ServerBossEvent boss = new ServerBossEvent(Mth.createInsecureUUID(random), Component.translatable("entity.hardwrought.witch_king"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final List<HexBlade> held = new ArrayList<>();
    private final List<Pending> pending = new ArrayList<>();
    private BlockPos throne;
    private int entrance, growth, idle;
    /** Ticks until he chooses his next move; ticks until the next curse, which comes whatever else he does. */
    private int nextMove = 50, doomTimer, teleportCooldown, meleeCooldown;
    private Move last;
    private boolean slipAway;
    private char charging;
    // A leap under way: from, to, ticks left.
    private Vec3 leapFrom, leapTo;
    private int leap;
    // A blow being wound up: where it lands, ticks left, blows still to come after it.
    private Vec3 slamAt;
    private int slam, combo;

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
        builder.define(ENRAGED, false);
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

    public boolean enraged() {
        return entityData.get(ENRAGED);
    }

    public boolean growing() {
        return growth > 0;
    }

    /** Places him in his hall: its middle, and the throne he answers to. */
    public void settle(BlockPos home, BlockPos throne) {
        entityData.set(HOME, home);
        this.throne = throne;
    }

    /** Rising from his throne: some seconds of untouchable entrance, and his name over the hall. */
    public void enter() {
        entrance = ENTRANCE_TICKS;
        level().broadcastEntityEvent(this, EV_ENTRANCE);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 0.5f);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 0.5f);
        if (level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(homeVec()) > 48 * 48) continue;
                player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 60, 25));
                player.connection.send(new ClientboundSetTitleTextPacket(
                        Component.translatable("entity.hardwrought.witch_king").withStyle(ChatFormatting.DARK_PURPLE)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.translatable("message.hardwrought.witch_king.subtitle").withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    private Vec3 homeVec() {
        return Vec3.atBottomCenterOf(home());
    }

    private double floor() {
        return home().getY() + 0.05;
    }

    private void cast(int ticks) {
        entityData.set(CAST, Math.max(casting(), ticks));
    }

    private void later(int ticks, Consumer<ServerLevel> action) {
        pending.add(new Pending(ticks, action));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= EV_BLADES && id <= EV_SLAM_WINDUP) {
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
        runPending(level);
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
        if (leap > 0) {
            leaping(level);
            return;
        }
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
        if (slam > 0) {
            windingUp(level, target);
            return;
        }
        if (charge() > 0) {
            entityData.set(CHARGE, charge() - 1);
            if (charge() == 0) release(level, target);
            hover(target);
            return;
        }
        if (phase() > 1 && --doomTimer <= 0) {
            doomTimer = (phase() == 2 ? 420 : 520) - (enraged() ? 140 : 0);
            startCharge(level, 'D');
            return;
        }
        switch (phase()) {
            case 1 -> elusive(level, target);
            case 2 -> warded(level, target);
            default -> unbound(level, target);
        }
    }

    private void runPending(ServerLevel level) {
        if (pending.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        pending.removeIf(p -> {
            if (--p.ticks > 0) return false;
            due.add(p);
            return true;
        });
        for (Pending p : due) p.action.accept(level);
    }

    private LivingEntity findTarget(ServerLevel level) {
        Player nearest = null;
        double best = 48 * 48;
        for (Player player : foes(level)) {
            double d = player.distanceToSqr(homeVec());
            if (d < best) {
                best = d;
                nearest = player;
            }
        }
        return nearest;
    }

    /** Everyone in his hall he would fight. */
    private List<Player> foes(ServerLevel level) {
        List<Player> foes = new ArrayList<>();
        for (Player player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            if (player.distanceToSqr(homeVec()) < 48 * 48) foes.add(player);
        }
        return foes;
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
        pending.clear();
        super.remove(reason);
    }

    /** Nobody to fight: back over the middle of his hall, mending; long enough alone, back onto his throne. */
    private void idle(ServerLevel level) {
        flyToward(homeVec().add(0, phase() == 3 ? 0.05 : 4, 0), 0.2);
        slam = 0;
        if (tickCount % 20 == 0) heal(2);
        if (++idle > 600 && throne != null && level.getBlockEntity(throne) instanceof WitchKingThrone.Entity seat) {
            seat.sleep();
            discard();
        }
    }

    /** How much sooner he moves again: quicker as his crystals fall, and quicker still when he rages. */
    private double tempo() {
        double tempo = 1;
        if (phase() == 2) tempo = 0.7 + 0.075 * crystals();
        if (enraged()) tempo *= 0.6;
        return tempo;
    }

    private void rest(int ticks) {
        nextMove = (int) Math.round(ticks * tempo()) + random.nextInt(15);
    }

    /** Picks one of the moves by weight, never the one he made last. */
    private Move choose(Move[] moves, int[] weights) {
        int total = 0;
        for (int i = 0; i < moves.length; i++) if (moves[i] != last) total += weights[i];
        if (total <= 0) return moves[0];
        int roll = random.nextInt(total);
        for (int i = 0; i < moves.length; i++) {
            if (moves[i] == last) continue;
            roll -= weights[i];
            if (roll < 0) return last = moves[i];
        }
        return last = moves[0];
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

    private boolean inHall(Vec3 at) {
        return new Vec3(at.x - homeVec().x, 0, at.z - homeVec().z).length() <= ARENA + 2;
    }

    private void hover(LivingEntity target) {
        double bob = Math.sin(tickCount * 0.07) * 0.4;
        if (phase() == 3) flyToward(inArena(position(), floor()), 0.2);
        else if (phase() == 2) flyToward(homeVec().add(0, 6 + bob, 0), 0.3);
        else flyToward(new Vec3(getX(), home().getY() + 5 + bob, getZ()), 0.2);
    }

    /** Out through a rift, and in through another where he wants to be. */
    private boolean teleport(ServerLevel level, Vec3 near, double min, double max, boolean behind) {
        for (int attempt = 0; attempt < 24; attempt++) {
            Vec3 at;
            if (behind && getTarget() != null) {
                Vec3 back = getTarget().getLookAngle().multiply(1, 0, 1).normalize().scale(-(3 + getBbWidth()));
                at = inArena(getTarget().position().add(back), phase() == 3 ? floor() : home().getY() + 3 + random.nextInt(4));
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
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------ the phases

    /** Phase 1: keeps away, slips off through rifts, looses swords in volleys, fans and rings. */
    private void elusive(ServerLevel level, LivingEntity target) {
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
        double bob = Math.sin(tickCount * 0.07) * 0.6;
        // Circling as he keeps away, so he is never quite where the last arrow went.
        Vec3 side = new Vec3(-away.z, 0, away.x).normalize().scale(Math.sin(tickCount * 0.02) * 6);
        Vec3 perch = inArena(target.position().add(away.normalize().scale(14)).add(side), home().getY() + 5 + bob);
        flyToward(perch, 0.32);
        if (slipAway || distanceTo(target) < 7) {
            if (teleportCooldown <= 0) {
                slipAway = false;
                teleport(level, target.position(), 11, 16, false);
                // Out of reach, and straight back at them.
                if (random.nextInt(3) == 0) later(8, l -> quickBlades(l, 2));
            }
        }
        if (--nextMove > 0) return;
        boolean hurt = getHealth() < getMaxHealth() * 0.85f;
        Move move = choose(new Move[] {Move.VOLLEY, Move.FAN, Move.RING, Move.BLINK}, new int[] {3, 3, hurt ? 3 : 1, 2});
        switch (move) {
            case VOLLEY -> {
                summonBlades(level, hurt ? 4 : 3);
                rest(80);
            }
            case FAN -> {
                fan(level, target, hurt ? 7 : 5);
                rest(65);
            }
            case RING -> {
                ring(level, target, hurt ? 8 : 6);
                rest(80);
            }
            default -> {
                if (teleport(level, target.position(), 7, 10, false)) later(6, l -> quickBlades(l, hurt ? 3 : 2));
                rest(55);
            }
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
        nextMove = 60;
        doomTimer = 260;
        last = null;
        cast(30);
    }

    /** Counts the crystals still standing; the last one gone, the ward breaks. Returns true when phase 3 has begun. */
    private boolean watchCrystals(ServerLevel level) {
        int standing = 0;
        for (BlockPos spot : CRYSTAL_SPOTS) if (level.getBlockState(home().offset(spot)).is(WitchKingContent.CRYSTAL)) standing++;
        if (standing >= crystals()) return false;
        entityData.set(CRYSTALS, standing);
        level.broadcastEntityEvent(this, EV_CRYSTAL);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 2.5f, 0.5f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
        if (standing > 0) {
            // His answer: the floor torn open round the one who broke it.
            for (BlockPos spot : CRYSTAL_SPOTS) {
                BlockPos at = home().offset(spot);
                if (level.getBlockState(at).is(WitchKingContent.CRYSTAL)) continue;
                Player breaker = level.getNearestPlayer(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 6, false);
                if (breaker != null && !breaker.isCreative()) eruptionRing(level, breaker.position(), 3.5, 7, 4);
            }
            return false;
        }
        startPhaseThree(level);
        return true;
    }

    /** Phase 2: still over the middle of the hall behind his ward; everything he has, aimed from there. */
    private void warded(ServerLevel level, LivingEntity target) {
        hover(target);
        if (--nextMove > 0) return;
        boolean late = crystals() <= 2;
        int undead = level.getEntitiesOfClass(Mob.class, new AABB(home()).inflate(ARENA + 2, 8, ARENA + 2),
                mob -> mob instanceof GraveUndead.GraveWalker || mob instanceof GraveUndead.GraveSkeleton).size();
        Move move = choose(new Move[] {Move.RAIN, Move.SOULS, Move.RAISE, Move.LANCE, Move.SPOKES},
                new int[] {3, 2, undead >= 4 ? 0 : 1, 3, 3});
        switch (move) {
            case RAIN -> {
                swordRain(level);
                rest(90);
            }
            case SOULS -> {
                souls(level, late ? 5 : 3);
                rest(80);
            }
            case RAISE -> {
                raiseDead(level);
                rest(70);
            }
            case LANCE -> {
                lance(level, target);
                if (late) later(LANCE_CHARGE + 10, l -> {
                    if (getTarget() != null && isAlive()) lance(l, getTarget());
                });
                rest(late ? 110 : 75);
            }
            default -> {
                double turn = random.nextDouble() * Math.PI;
                spokes(level, late ? 8 : 6, turn);
                if (late) later(28, l -> spokes(l, 8, turn + Math.PI / 8));
                rest(late ? 110 : 90);
            }
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
        flyToward(new Vec3(getX(), floor(), getZ()), 0.15);
        if (growth % 10 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 20, getBbWidth(), getBbHeight() * 0.4, getBbWidth(), 0.05);
        if (growth == 0) {
            shockwave(level, 18, 6);
            nextMove = 50;
            doomTimer = 400;
            last = null;
        }
    }

    /** Phase 3: a giant on the floor, pressing whoever is left; blows in a row, leaps, the floor torn open, rifts behind them. */
    private void unbound(ServerLevel level, LivingEntity target) {
        if (!enraged() && getHealth() < getMaxHealth() * RAGE_AT) {
            rage(level);
            return;
        }
        Vec3 goal = inArena(target.position(), floor());
        double reach = getBbWidth() / 2 + target.getBbWidth() / 2 + 2.4;
        double distance = distanceTo(target);
        double speed = enraged() ? 0.4 : 0.3;
        if (distance > reach * 0.8) flyToward(goal, speed);
        else setDeltaMovement(getDeltaMovement().scale(0.5));
        if (distance <= reach && meleeCooldown <= 0) {
            combo = random.nextInt(enraged() ? 3 : 2);
            windUp(level, target);
            return;
        }
        if (--nextMove > 0) return;
        boolean far = distance > 8;
        Move move = choose(new Move[] {Move.LEAP, Move.WAVE, Move.TRAIL, Move.FAN, Move.RIFT_STRIKE},
                new int[] {far ? 4 : 1, far ? 0 : 3, 3, 1, far ? 2 : 1});
        switch (move) {
            case LEAP -> {
                startLeap(level, target);
                rest(60);
            }
            case WAVE -> {
                startCharge(level, 'W');
                rest(70);
            }
            case TRAIL -> {
                trail(level, target, enraged() ? 5 : 3);
                rest(70);
            }
            case FAN -> {
                fan(level, target, enraged() ? 9 : 6);
                rest(60);
            }
            default -> {
                if (teleportCooldown <= 0 && teleport(level, target.position(), 0, 0, true)) {
                    combo = enraged() ? 1 : 0;
                    windUp(level, target);
                }
                rest(70);
            }
        }
    }

    /** Brought low: a roar that shakes the hall, and from now on everything faster. */
    private void rage(ServerLevel level) {
        entityData.set(ENRAGED, true);
        level.broadcastEntityEvent(this, EV_RAGE);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0f, 0.4f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 4.0f, 0.6f);
        cast(40);
        shockwave(level, 10, 4);
        eruptionRing(level, homeVec(), 9, 12, 10);
        eruptionRing(level, homeVec(), 13, 16, 22);
        nextMove = 40;
        doomTimer = Math.min(doomTimer, 200);
    }

    // ------------------------------------------------------------------------------------ attacks

    /** Swords appear circling him, then fly one by one. */
    private void summonBlades(ServerLevel level, int count) {
        level.broadcastEntityEvent(this, EV_BLADES);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 0.6f);
        cast(24);
        for (int i = 0; i < count; i++) {
            HexBlade blade = blade(level, position().add(0, getBbHeight() * 0.6, 0));
            if (blade != null) held.add(blade);
        }
    }

    private HexBlade blade(ServerLevel level, Vec3 at) {
        HexBlade blade = WitchKingContent.HEX_BLADE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (blade == null) return null;
        blade.setOwner(this);
        blade.setPos(at);
        blade.poise = -1;
        level.addFreshEntity(blade);
        return blade;
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
            // Aimed a little ahead of where they run.
            Vec3 aim = target.getEyePosition().add(target.getDeltaMovement().scale(6)).subtract(at);
            blade.point(aim);
            if (blade.tickCount >= 30 + i * 7) blade.loose(aim, 1.6);
        }
    }

    /** Swords in an arc before him, loosed all at once in a fan: get out of its way sideways. */
    private void fan(ServerLevel level, LivingEntity target, int count) {
        level.broadcastEntityEvent(this, EV_BLADES);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 0.8f);
        cast(24);
        Vec3 origin = position().add(0, getBbHeight() * 0.6, 0);
        Vec3 toward = target.getEyePosition().subtract(origin);
        double yaw = Math.atan2(toward.z, toward.x);
        double spread = Math.toRadians(70);
        for (int i = 0; i < count; i++) {
            double a = yaw + (count == 1 ? 0 : -spread / 2 + spread * i / (count - 1));
            Vec3 at = origin.add(Math.cos(a) * (1.6 + getBbWidth()), 0.3 * Math.sin(i), Math.sin(a) * (1.6 + getBbWidth()));
            HexBlade blade = blade(level, at);
            if (blade == null) continue;
            Vec3 aim = new Vec3(Math.cos(a), toward.y / toward.horizontalDistance(), Math.sin(a));
            blade.point(aim);
            blade.poise = 22;
        }
    }

    /** Swords in a ring round the target, points turned in; they all close at once. Out of the ring, quickly. */
    private void ring(ServerLevel level, LivingEntity target, int count) {
        level.broadcastEntityEvent(this, EV_RAIN);
        cast(30);
        level.playSound(null, target.getX(), target.getY() + 2, target.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 2.5f, 0.6f);
        Vec3 middle = target.position().add(0, 1.0, 0);
        double turn = random.nextDouble() * Math.PI;
        for (int i = 0; i < count; i++) {
            double a = turn + i * Math.PI * 2 / count;
            Vec3 at = middle.add(Math.cos(a) * 5, 0.4 + 0.3 * (i % 2), Math.sin(a) * 5);
            HexBlade blade = blade(level, at);
            if (blade == null) continue;
            blade.point(middle.subtract(at));
            blade.poise = 30;
        }
    }

    /** Two or three swords straight at the target, quickly: what comes right after a rift. */
    private void quickBlades(ServerLevel level, int count) {
        if (!isAlive() || getTarget() == null) return;
        LivingEntity target = getTarget();
        cast(12);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.5f, 1.4f);
        for (int i = 0; i < count; i++) {
            Vec3 at = position().add((i - (count - 1) / 2.0) * 1.2, getBbHeight() * 0.7 + 0.4, 0);
            HexBlade blade = blade(level, at);
            if (blade == null) continue;
            blade.point(target.getEyePosition().subtract(at));
            blade.poise = 6 + i * 4;
        }
    }

    /** Sigils over every enemy's head, and swords falling out of them. */
    private void swordRain(ServerLevel level) {
        level.broadcastEntityEvent(this, EV_RAIN);
        cast(30);
        for (Player foe : foes(level)) {
            level.playSound(null, foe.getX(), foe.getY() + 8, foe.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.5f, 0.5f);
            for (int i = 0; i < 6; i++) {
                Vec3 at = foe.position().add(random.nextDouble() * 6 - 3, 8 + random.nextDouble() * 2, random.nextDouble() * 6 - 3);
                HexBlade blade = blade(level, at);
                if (blade == null) continue;
                Vec3 aim = foe.position().add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1).subtract(at);
                blade.point(aim);
                blade.poise = 22 + i * 5;
            }
        }
    }

    /** Souls sent after the target. */
    private void souls(ServerLevel level, int count) {
        level.broadcastEntityEvent(this, EV_ORBS);
        cast(20);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
        for (int i = 0; i < count; i++) {
            SoulOrb orb = WitchKingContent.SOUL_ORB.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (orb == null) continue;
            orb.setOwner(this);
            double angle = i * Math.PI * 2 / count + tickCount;
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

    /** A line of light drawn from him at where the target stands now; a moment later his lance runs along it. */
    private void lance(ServerLevel level, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.6, 0);
        Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 far = from.add(dir.scale(DOOM_REACH));
        var hit = level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 to = hit.getLocation();
        cast(LANCE_CHARGE + 6);
        Fx.play(level, FxEffect.HEX_LANCE_CHARGE, from, to, VIOLET, 1, null);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.6f);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.5f, 0.8f);
        later(LANCE_CHARGE, l -> {
            if (!isAlive()) return;
            Fx.play(l, FxEffect.HEX_LANCE, from, to, VIOLET, 1, null);
            l.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.5f, 0.6f);
            l.playSound(null, to.x, to.y, to.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 2.0f, 0.7f);
            for (Player foe : foes(l)) {
                Vec3 body = foe.position().add(0, foe.getBbHeight() * 0.5, 0);
                if (distanceToSegment(body, from, to) <= LANCE_WIDTH) rend(l, foe, this, LANCE_DAMAGE, 0);
            }
        });
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / Math.max(1.0e-6, ab.lengthSqr()), 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** The floor breaking open along spokes running out from under him, the far end last: stand between them. */
    private void spokes(ServerLevel level, int count, double turn) {
        if (!isAlive()) return;
        cast(20);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.HOSTILE, 2.5f, 0.5f);
        Vec3 middle = new Vec3(homeVec().x, floor(), homeVec().z);
        for (int i = 0; i < count; i++) {
            double a = turn + i * Math.PI * 2 / count;
            for (double d = 3; d <= ARENA + 1; d += 2.6) {
                eruption(level, middle.add(Math.cos(a) * d, 0, Math.sin(a) * d), 1.4f, (int) (d * 1.3));
            }
        }
    }

    /** Lines of the floor breaking open from his feet towards the target, one after the other, faster than a walk. */
    private void trail(ServerLevel level, LivingEntity target, int lines) {
        cast(20);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2.5f, 0.5f);
        Vec3 feet = new Vec3(getX(), floor(), getZ());
        Vec3 to = target.position().subtract(feet);
        double yaw = Math.atan2(to.z, to.x);
        for (int line = 0; line < lines; line++) {
            double a = yaw + Math.toRadians(18) * (line - (lines - 1) / 2.0);
            for (double d = getBbWidth() / 2 + 1.5; d <= 26; d += 2.2) {
                Vec3 at = feet.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                if (!inHall(at)) break;
                eruption(level, at, 1.3f, (int) d);
            }
        }
    }

    /** A ring of eruptions round a place, then its middle. */
    private void eruptionRing(ServerLevel level, Vec3 around, double radius, int count, int delay) {
        Vec3 middle = new Vec3(around.x, floor(), around.z);
        double turn = random.nextDouble() * Math.PI;
        for (int i = 0; i < count; i++) {
            double a = turn + i * Math.PI * 2 / count;
            Vec3 at = middle.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            if (inHall(at)) eruption(level, at, 1.5f, delay);
        }
    }

    /** A mark on the floor now; after {@link #MARK_TICKS} the floor breaks open under it, throwing up whoever stands there. */
    private void eruption(ServerLevel level, Vec3 at, float radius, int delay) {
        later(Math.max(1, delay), l -> {
            if (!isAlive()) return;
            mark(l, at, radius, MARK_TICKS, VIOLET);
            later(MARK_TICKS, l2 -> erupt(l2, at, radius));
        });
    }

    private void mark(ServerLevel level, Vec3 at, float radius, int ticks, int color) {
        Fx.play(level, FxEffect.HEX_MARK, at, at.add(0, ticks, 0), color, radius, null);
    }

    private void erupt(ServerLevel level, Vec3 at, float radius) {
        if (!isAlive()) return;
        Fx.play(level, FxEffect.HEX_ERUPTION, at, at, VIOLET, radius, null);
        if (random.nextInt(3) == 0) level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.2f, 0.6f);
        else level.playSound(null, at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5f, 0.5f);
        for (Player foe : foes(level)) {
            double flat = Math.hypot(foe.getX() - at.x, foe.getZ() - at.z);
            double up = foe.getY() - at.y;
            if (flat > radius + foe.getBbWidth() / 2 || up < -1 || up > 2.5) continue;
            if (rend(level, foe, this, ERUPTION_DAMAGE, 0)) {
                foe.setDeltaMovement(foe.getDeltaMovement().add(0, 0.7, 0));
                foe.needsSync = true;
            }
        }
    }

    /** Up into the air and down on the target, a mark under where he will land. */
    private void startLeap(ServerLevel level, LivingEntity target) {
        leapFrom = position();
        leapTo = inArena(target.position().add(target.getDeltaMovement().scale(8)), floor());
        leap = LEAP_TICKS;
        cast(LEAP_TICKS);
        level.broadcastEntityEvent(this, EV_LEAP);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.6f);
        mark(level, leapTo, (float) LAND_REACH, LEAP_TICKS, BLOOD);
    }

    private void leaping(ServerLevel level) {
        leap--;
        double t = 1 - leap / (double) LEAP_TICKS;
        double eased = t * t * (3 - 2 * t);
        Vec3 want = leapFrom.lerp(leapTo, eased).add(0, 7 * 4 * t * (1 - t), 0);
        setDeltaMovement(want.subtract(position()));
        if (leap > 0) return;
        setDeltaMovement(Vec3.ZERO);
        level.broadcastEntityEvent(this, EV_LAND);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 4.0f, 0.5f);
        shockwave(level, LAND_REACH, LAND_DAMAGE);
        if (enraged()) eruptionRing(level, position(), LAND_REACH + 3, 10, 4);
        meleeCooldown = 12;
    }

    /** A blow wound up: a mark where it will land, and then it lands there, whoever is still in it. */
    private void windUp(ServerLevel level, LivingEntity target) {
        Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 0.01 ? getLookAngle().multiply(1, 0, 1).normalize() : dir.normalize();
        slamAt = new Vec3(getX(), floor(), getZ()).add(dir.scale(getBbWidth() / 2 + SLAM_REACH * 0.7));
        slam = enraged() ? SLAM_WINDUP - 3 : SLAM_WINDUP;
        cast(slam + 6);
        level.broadcastEntityEvent(this, EV_SLAM_WINDUP);
        mark(level, slamAt, (float) SLAM_REACH, slam, BLOOD);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0f, 0.4f);
    }

    private void windingUp(ServerLevel level, LivingEntity target) {
        setDeltaMovement(getDeltaMovement().scale(0.4));
        if (--slam > 0) return;
        level.broadcastEntityEvent(this, EV_SLAM);
        level.playSound(null, slamAt.x, slamAt.y, slamAt.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 2.5f, 0.6f);
        level.playSound(null, slamAt.x, slamAt.y, slamAt.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.0f, 0.7f);
        for (Player foe : foes(level)) {
            if (Math.hypot(foe.getX() - slamAt.x, foe.getZ() - slamAt.z) > SLAM_REACH + foe.getBbWidth() / 2 || foe.getY() - slamAt.y > 3) continue;
            if (rend(level, foe, this, SLAM_DAMAGE, SLAM_ARMOUR)) {
                Vec3 push = foe.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.6);
                foe.push(push.x, 0.5, push.z);
                foe.needsSync = true;
            }
        }
        if (combo > 0) {
            combo--;
            later(enraged() ? 3 : 6, l -> {
                if (isAlive() && getTarget() != null && slam == 0 && leap == 0) windUp(l, getTarget());
            });
            meleeCooldown = 10;
        } else {
            meleeCooldown = enraged() ? 20 : 34;
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
            rend(level, player, this, DOOM_DAMAGE, 0);
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
                rend(level, living, this, damage, 0);
            }
        }
    }

    // ------------------------------------------------------------------------------------ his magic

    /**
     * Harm of his on someone. It tears at the soul: armour counts only for the given share of itself (none
     * against his spells, half against his blows), and protective runes for half of what they would against
     * other magic. A magic shield still stops it, as it stops anything from outside.
     */
    public static boolean rend(ServerLevel level, LivingEntity victim, Entity direct, float amount, float armour) {
        Entity king = direct instanceof WitchKing ? direct : direct instanceof net.minecraft.world.entity.projectile.Projectile p ? p.getOwner() : null;
        return de.ipnats.hardwrought.combat.Rending.rend(level, victim, ModDamageTypes.HEX, direct, king, amount, armour, RUNE_SHARE);
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
            pending.clear();
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
        output.putBoolean("enraged", enraged());
        if (throne != null) output.putLong("throne", throne.asLong());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(PHASE, input.getIntOr("phase", 1));
        entityData.set(HOME, BlockPos.of(input.getLongOr("home", blockPosition().asLong())));
        entityData.set(CRYSTALS, input.getIntOr("crystals", 0));
        entityData.set(ENRAGED, input.getBooleanOr("enraged", false));
        throne = input.getLong("throne").map(BlockPos::of).orElse(null);
        if (hasCustomName()) boss.setName(getDisplayName());
    }

    // ------------------------------------------------------------------------------------ for tests

    /** Sets how much health he has left, as a share; for tests. */
    public void testHealth(float share) {
        setHealth(getMaxHealth() * share);
    }
}
