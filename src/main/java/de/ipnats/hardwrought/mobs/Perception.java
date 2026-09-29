package de.ipnats.hardwrought.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mob specification § 2: mobs perceive the player, they do not simply know where the player is.
 *
 * <p><b>Hearing.</b> What a player does makes noise — mining, building, doors, footsteps, shots,
 * explosions — each carrying as far as {@link #loudness} says. Every whole block in the way muffles
 * it to {@value #WALL_DAMPING} of its reach, so a player digging behind rock is heard by what is
 * close, not by the whole cave. A hostile mob with no target that hears it goes to look
 * ({@link InvestigateNoiseGoal}); what it finds there, its eyes decide. A sneaking player takes careful
 * steps, which vanilla already keeps silent.
 *
 * <p><b>Sight in the dark.</b> How far a mob notices a player scales with the light the player stands
 * in: in full light as vanilla, in darkness at {@value #DARK_VISIBILITY} of that. A player carrying a
 * light is seen as if standing in it.
 */
public final class Perception {
    /** A block in the way keeps this share of a sound's reach. */
    static final double WALL_DAMPING = 0.55;
    /** How much of the vanilla detection range is left in total darkness. */
    static final double DARK_VISIBILITY = 0.3;
    /** How long a heard noise is worth following, in ticks. */
    static final int NOISE_MEMORY_TICKS = 200;
    /** § 2 "movement detection": a sprinting player is noticed from this much farther. */
    static final double SPRINT_VISIBILITY = 1.25;

    /** Where a mob last heard something worth looking into, and when. */
    record Noise(Vec3 at, long heardAt) { }

    private static final Map<Mob, Noise> heard = new WeakHashMap<>();

    private Perception() { }

    /** How far a player-made sound of this kind carries in the open, in blocks; zero for silence. */
    public static double loudness(Holder<GameEvent> event) {
        if (event.is(GameEvent.EXPLODE)) return 48;
        if (event.is(GameEvent.BLOCK_DESTROY)) return 16;
        if (event.is(GameEvent.PROJECTILE_SHOOT) || event.is(GameEvent.PRIME_FUSE)) return 12;
        if (event.is(GameEvent.BLOCK_PLACE) || event.is(GameEvent.BLOCK_OPEN) || event.is(GameEvent.ENTITY_DAMAGE)) return 10;
        // § 7: movement in water carries, and the drowned are listening.
        if (event.is(GameEvent.SPLASH)) return 12;
        if (event.is(GameEvent.STEP) || event.is(GameEvent.HIT_GROUND) || event.is(GameEvent.SWIM)) return 8;
        if (event.is(GameEvent.CONTAINER_OPEN)) return 6;
        return 0;
    }

    /** § 46 "weather": rain drowns out sound, to this share of its reach; a thunderstorm more so. */
    static final double RAIN_MUFFLING = 0.7, THUNDER_MUFFLING = 0.5;

    /** Called for every game event on the server. */
    public static void heard(ServerLevel level, Holder<GameEvent> event, Vec3 at, Entity source) {
        double loudness = loudness(event);
        if (loudness <= 0 || !byPlayer(source) && !event.is(GameEvent.EXPLODE)) return;
        if (level.isRainingAt(BlockPos.containing(at))) loudness *= level.isThundering() ? THUNDER_MUFFLING : RAIN_MUFFLING;
        alert(level, at, loudness);
    }

    /** A sound this loud at this spot: every idle hostile mob that can make it out goes to look. */
    public static void alert(ServerLevel level, Vec3 at, double loudness) {
        AABB reach = new AABB(at, at).inflate(loudness);
        for (Monster mob : level.getEntitiesOfClass(Monster.class, reach, mob -> mob.getTarget() == null && mob.isAlive())) {
            if (mob.distanceToSqr(at) <= Math.pow(reachThrough(level, at, mob.getEyePosition(), loudness), 2)) {
                heard.put(mob, new Noise(at, level.getGameTime()));
            }
        }
    }

    /**
     * Whether a player in survival made this sound. Players in creative or spectator mode are ignored
     * by hostile mobs altogether, as vanilla ignores them as targets: they are heard no more than seen.
     */
    private static boolean byPlayer(Entity source) {
        if (source instanceof Projectile projectile) source = projectile.getOwner();
        return source instanceof Player player && !player.isCreative() && !player.isSpectator();
    }

    /** How far the sound still carries once every block between source and listener has muffled it. */
    public static double reachThrough(ServerLevel level, Vec3 from, Vec3 to, double loudness) {
        double reach = loudness;
        Vec3 step = to.subtract(from);
        int samples = (int) Math.ceil(step.length());
        BlockPos last = null;
        for (int i = 1; i < samples; i++) {
            BlockPos pos = BlockPos.containing(from.add(step.scale(i / (double) samples)));
            if (pos.equals(last)) continue;
            last = pos;
            if (level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) reach *= WALL_DAMPING;
        }
        return reach;
    }

    /** The noise this mob is following, if it is still fresh; forgets it otherwise. */
    public static Vec3 noise(PathfinderMob mob) {
        Noise noise = heard.get(mob);
        if (noise == null) return null;
        if (mob.level().getGameTime() - noise.heardAt() > NOISE_MEMORY_TICKS) {
            heard.remove(mob);
            return null;
        }
        return noise.at();
    }

    /** Sends this mob to look at a spot, as if it had heard something there. */
    public static void attract(Mob mob, Vec3 at) {
        heard.put(mob, new Noise(at, mob.level().getGameTime()));
    }

    public static void forget(PathfinderMob mob) {
        heard.remove(mob);
    }

    /** Factor on the range a hostile mob notices this player from, by the light they stand in. */
    public static double visibility(ServerLevel level, LivingEntity target) {
        if (!(target instanceof Player player)) return 1;
        int light = Math.max(level.getMaxLocalRawBrightness(player.blockPosition()), heldLight(player));
        double seen = DARK_VISIBILITY + (1 - DARK_VISIBILITY) * light / 15.0;
        // Movement catches the eye: a sprinting player is noticed from farther off.
        return player.isSprinting() ? seen * SPRINT_VISIBILITY : seen;
    }

    private static int heldLight(Player player) {
        return Math.max(lightOf(player.getMainHandItem()), lightOf(player.getOffhandItem()));
    }

    private static int lightOf(ItemStack stack) {
        if (stack.getItem() instanceof de.ipnats.hardwrought.environment.SafetyLampItem) return 12;
        return stack.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState().getLightEmission() : 0;
    }
}
