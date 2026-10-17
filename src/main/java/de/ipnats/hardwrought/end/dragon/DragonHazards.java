package de.ipnats.hardwrought.end.dragon;

import de.ipnats.hardwrought.end.BrokenIsland;
import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the dragon leaves hanging in the air and the ground falling away: clouds of void that harm
 * whoever flies into them, things it set going that happen a moment later, and pieces of the island
 * breaking up. All of it lives on the server, and is seen through the FX module.
 */
public final class DragonHazards {
    /** How often a cloud bites whoever is in it. */
    static final int CLOUD_EVERY = 10;
    /** Blocks taken from a crumbling fragment each tick, and how many of them are seen to fall rather than burst. */
    static final int CRUMBLE_PER_TICK = 24, FALLING_ONE_IN = 4;

    private record Cloud(ServerLevel level, EnderDragon dragon, Vec3 at, float radius, long until) { }

    private static final class Task {
        final ServerLevel level;
        int ticks;
        final Consumer<ServerLevel> action;

        Task(ServerLevel level, int ticks, Consumer<ServerLevel> action) {
            this.level = level;
            this.ticks = ticks;
            this.action = action;
        }
    }

    private static final class Crumble {
        final ServerLevel level;
        final List<BlockPos> blocks;
        int next;

        Crumble(ServerLevel level, List<BlockPos> blocks) {
            this.level = level;
            this.blocks = blocks;
        }
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Crumble> CRUMBLES = new ArrayList<>();

    private DragonHazards() { }

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(DragonHazards::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CLOUDS.clear();
            TASKS.clear();
            CRUMBLES.clear();
        });
    }

    /** Something to happen this many ticks from now. */
    public static void later(ServerLevel level, int ticks, Consumer<ServerLevel> action) {
        TASKS.add(new Task(level, Math.max(1, ticks), action));
    }

    /** A cloud of void hanging in the air, for this many ticks. */
    static void cloud(ServerLevel level, EnderDragon dragon, Vec3 at, float radius, int ticks) {
        CLOUDS.add(new Cloud(level, dragon, at, radius, level.getGameTime() + ticks));
        Fx.play(level, FxEffect.VOID_CLOUD, at, at.add(0, ticks, 0), 0, radius, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    /** A piece of the island breaking up and falling into the void, from its edges in. */
    static void crumble(ServerLevel level, BrokenIsland.Fragment fragment, double keepInside) {
        List<BlockPos> blocks = new ArrayList<>();
        double reach = fragment.radius() * 1.3;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(fragment.x() - reach); x <= fragment.x() + reach; x++) {
            for (int z = (int) Math.floor(fragment.z() - reach); z <= fragment.z() + reach; z++) {
                double d = Math.hypot(x + 0.5 - fragment.x(), z + 0.5 - fragment.z());
                if (d < keepInside) continue;
                for (int y = (int) (fragment.top() - fragment.depth() - 3); y <= fragment.top() + 2; y++) {
                    pos.set(x, y, z);
                    if (breakable(level.getBlockState(pos))) blocks.add(pos.immutable());
                }
            }
        }
        if (blocks.isEmpty()) return;
        // From the rim inwards, and within a ring from the bottom up, so it is seen to come apart.
        blocks.sort(Comparator.comparingDouble((BlockPos b) -> -Math.round(Math.hypot(b.getX() + 0.5 - fragment.x(), b.getZ() + 0.5 - fragment.z())))
                .thenComparingInt(BlockPos::getY));
        CRUMBLES.add(new Crumble(level, blocks));
        Vec3 at = new Vec3(fragment.x(), fragment.top(), fragment.z());
        Fx.play(level, FxEffect.FRAGMENT_CRUMBLE, at, at.add(0, blocks.size() / CRUMBLE_PER_TICK + 10, 0), 0, (float) fragment.radius(), null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0f, 0.4f);
    }

    /** A few blocks off the rim of a fragment: a stamp shaking its edges loose. */
    static void shakeLoose(ServerLevel level, BrokenIsland.Fragment fragment, int count, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0, tries = 0; i < count && tries < count * 20; tries++) {
            double angle = random.nextDouble() * Math.PI * 2, d = fragment.radius() * (0.82 + random.nextDouble() * 0.4);
            int x = (int) Math.floor(fragment.x() + Math.cos(angle) * d), z = (int) Math.floor(fragment.z() + Math.sin(angle) * d);
            for (int y = fragment.top() + 1; y > fragment.top() - 6; y--) {
                pos.set(x, y, z);
                if (!breakable(level.getBlockState(pos))) continue;
                fall(level, pos.immutable(), true);
                i++;
                break;
            }
        }
    }

    private static boolean breakable(BlockState state) {
        return state.is(Blocks.END_STONE) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN);
    }

    private static void fall(ServerLevel level, BlockPos pos, boolean seen) {
        BlockState state = level.getBlockState(pos);
        if (seen) {
            FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
            falling.dropItem = false;
            falling.disableDrop();
        } else {
            level.removeBlock(pos, false);
            if (level.getRandom().nextInt(6) == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        6, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        if (!TASKS.isEmpty()) {
            List<Task> due = new ArrayList<>();
            TASKS.removeIf(task -> {
                if (--task.ticks > 0) return false;
                due.add(task);
                return true;
            });
            for (Task task : due) task.action.accept(task.level);
        }
        CLOUDS.removeIf(cloud -> cloud.level().getGameTime() >= cloud.until() || cloud.dragon().isRemoved());
        for (Cloud cloud : CLOUDS) {
            if (cloud.level().getGameTime() % CLOUD_EVERY != 0) continue;
            AABB box = AABB.ofSize(cloud.at(), cloud.radius() * 2, cloud.radius() * 2, cloud.radius() * 2);
            for (ServerPlayer player : cloud.level().getEntitiesOfClass(ServerPlayer.class, box)) {
                if (player.isCreative() || player.isSpectator()) continue;
                if (player.getBoundingBox().getCenter().distanceTo(cloud.at()) > cloud.radius() + 0.4) continue;
                VoidDragon.voidHarm(cloud.level(), cloud.dragon(), cloud.dragon(), player, VoidDragon.CLOUD);
            }
        }
        CRUMBLES.removeIf(crumble -> {
            for (int i = 0; i < CRUMBLE_PER_TICK && crumble.next < crumble.blocks.size(); i++, crumble.next++) {
                BlockPos pos = crumble.blocks.get(crumble.next);
                if (!breakable(crumble.level.getBlockState(pos))) continue;
                fall(crumble.level, pos, crumble.level.getRandom().nextInt(FALLING_ONE_IN) == 0);
            }
            return crumble.next >= crumble.blocks.size();
        });
    }
}
