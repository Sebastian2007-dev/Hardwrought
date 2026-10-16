package de.ipnats.hardwrought.wither;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModEffects;
import de.ipnats.hardwrought.health.Ailments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What the Wither can do beyond vanilla (see docs/endgame.md; its adds and its damage to buildings are
 * in {@code Bosses}):
 *
 * <ul>
 *   <li><b>It teleports</b> — now and then, to come at its target from somewhere else, and at once when it
 *       is walled in by what it cannot break. A Wither trapped under bedrock is not a Wither beaten.
 *   <li><b>Soul storm</b>: below a quarter of its health, everyone near it is drained (the
 *       {@linkplain ModEffects#DRAIN drain}), worse the longer they stay.
 *   <li><b>Its death</b> is a last great blast, and then its souls: they come back out of the air from all
 *       around and gather into the nether star, which hangs where it died. Wither essence falls with it.
 * </ul>
 */
public final class WitherPowers {
    /** Ticks between teleports, at least and at most, while it has someone to come for. */
    static final int TELEPORT_MIN = 200, TELEPORT_MAX = 400;
    /** Below this share of its health, the soul storm. */
    static final float STORM_BELOW = 0.25f;
    static final double STORM_RADIUS = 16;
    /** Drain each second in the storm adds, in ticks. */
    static final int STORM_DRAIN = 60;
    /** How long the souls take to gather into the star. */
    static final int GATHER_TICKS = 80;

    public static final Item ESSENCE = item("wither_essence");

    private record Pending(long at, Runnable action) { }

    private static final List<Pending> PENDING = new ArrayList<>();

    private WitherPowers() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(WitherPowers::runPending);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    /** One tick of a Wither's own powers; called from its server tick once it is awake. */
    public static void tick(WitherBoss wither, ServerLevel level) {
        if (wither.getInvulnerableTicks() > 0 || !wither.isAlive()) return;
        if (wither.tickCount % 20 == 0) {
            if (trapped(wither, level)) teleport(wither, level, wither.getTarget());
            if (wither.getHealth() <= wither.getMaxHealth() * STORM_BELOW) storm(wither, level);
        }
        LivingEntity target = wither.getTarget();
        if (target != null && wither.tickCount % 20 == 5) {
            RandomSource random = wither.getRandom();
            int every = TELEPORT_MIN + random.nextInt(TELEPORT_MAX - TELEPORT_MIN);
            if (random.nextInt(every / 20) == 0) teleport(wither, level, target);
        }
    }

    /**
     * Walled in by what it cannot break: something it cannot destroy over its head and on most sides
     * of it. It does not wait to be beaten there.
     */
    public static boolean trapped(WitherBoss wither, ServerLevel level) {
        BlockPos head = BlockPos.containing(wither.getX(), wither.getBoundingBox().maxY + 0.5, wither.getZ());
        if (!unbreakable(level, head)) return false;
        BlockPos middle = BlockPos.containing(wither.getX(), wither.getY() + 1.5, wither.getZ());
        int walls = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (unbreakable(level, middle.relative(side, 2)) || unbreakable(level, middle.relative(side))) walls++;
        }
        return walls >= 3;
    }

    private static boolean unbreakable(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return !state.isAir() && (state.is(BlockTags.WITHER_IMMUNE) || state.getDestroySpeed(level, pos) < 0);
    }

    /** Somewhere free near its target — or near itself, with no target — and it is there. */
    static boolean teleport(WitherBoss wither, ServerLevel level, LivingEntity target) {
        RandomSource random = wither.getRandom();
        Vec3 around = target != null ? target.position() : wither.position();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 6 + random.nextDouble() * 8;
            Vec3 to = around.add(Math.cos(angle) * distance, 3 + random.nextInt(6), Math.sin(angle) * distance);
            AABB box = wither.getBoundingBox().move(to.subtract(wither.position()));
            if (!level.noCollision(wither, box) || level.containsAnyLiquid(box)) continue;
            Vec3 from = wither.position();
            souls(level, from.add(0, 1.5, 0), 40);
            wither.teleportTo(to.x, to.y, to.z);
            souls(level, to.add(0, 1.5, 0), 40);
            level.playSound(null, BlockPos.containing(from), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.4f);
            level.playSound(null, BlockPos.containing(to), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.4f);
            return true;
        }
        return false;
    }

    /** Its souls tear at everyone near it. */
    public static void storm(WitherBoss wither, ServerLevel level) {
        Vec3 at = wither.position().add(0, 1.5, 0);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 30, STORM_RADIUS / 3, 2, STORM_RADIUS / 3, 0.02);
        if (wither.getRandom().nextInt(4) == 0) {
            level.playSound(null, wither.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, wither.getBoundingBox().inflate(STORM_RADIUS))) {
            // Creative players carry it harmlessly: the survival metabolism that reads it leaves them out.
            Ailments.worsen(player, ModEffects.DRAIN, STORM_DRAIN);
        }
    }

    /**
     * It is dead: the last blast, then its souls come back from all around and gather into the star.
     * Called instead of vanilla dropping the star at once.
     */
    public static void died(WitherBoss wither, ServerLevel level) {
        Vec3 center = wither.position().add(0, 1.8, 0);
        level.explode(wither, center.x, center.y, center.z, 6.0f, false, Level.ExplosionInteraction.MOB);
        level.playSound(null, BlockPos.containing(center), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0f, 0.4f);
        MinecraftServer server = level.getServer();
        long now = server.getTickCount();
        RandomSource random = level.getRandom();
        for (int tick = 4; tick < GATHER_TICKS; tick += 2) {
            final int t = tick;
            PENDING.add(new Pending(now + tick, () -> {
                // Drawn in from a shrinking shell, faster and thicker as the star takes shape.
                double radius = 10 - 7.0 * t / GATHER_TICKS;
                for (int i = 0; i < 4 + t / 10; i++) {
                    double yaw = random.nextDouble() * Math.PI * 2, pitch = (random.nextDouble() - 0.5) * Math.PI;
                    Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
                    Vec3 from = center.add(dir.scale(radius));
                    // With a count of none, the offset is the direction it flies and the last figure its speed.
                    level.sendParticles(ParticleTypes.SOUL, from.x, from.y, from.z, 0, -dir.x, -dir.y, -dir.z, 0.25 + radius * 0.02);
                }
                level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, t / 16, 0.1, 0.1, 0.1, 0.0);
                if (t % 16 == 0) {
                    level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE,
                            1.5f, 0.6f + t / (float) GATHER_TICKS);
                }
            }));
        }
        PENDING.add(new Pending(now + GATHER_TICKS, () -> {
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 60, 0.1, 0.1, 0.1, 0.25);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z, 40, 0.4, 0.4, 0.4, 0.05);
            level.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 1.2f);
            ItemEntity star = new ItemEntity(level, center.x, center.y, center.z, new ItemStack(Items.NETHER_STAR), 0, 0, 0);
            star.setNoGravity(true);
            star.setUnlimitedLifetime();
            level.addFreshEntity(star);
            int essence = 3 + random.nextInt(3);
            ItemEntity drop = new ItemEntity(level, center.x, center.y - 0.5, center.z, new ItemStack(ESSENCE, essence));
            drop.setExtendedLifetime();
            level.addFreshEntity(drop);
        }));
    }

    /** A burst of souls, where it leaves and where it arrives. */
    private static void souls(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, count, 0.8, 1.2, 0.8, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, count / 2, 0.8, 1.2, 0.8, 0.02);
    }

    private static void runPending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.at() <= server.getTickCount()) {
                due.add(pending.action());
                it.remove();
            }
        }
        due.forEach(Runnable::run);
    }

    private static Item item(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }
}
