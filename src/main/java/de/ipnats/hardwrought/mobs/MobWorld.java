package de.ipnats.hardwrought.mobs;

import de.ipnats.hardwrought.environment.GasBlock;
import de.ipnats.hardwrought.environment.GasPush;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Mob behaviour that starts in the world rather than in a mob (mob specification §§ 18, 19, 31, 37,
 * 38): disturbed nests, pearls that draw endermites, shulker volleys, the breeze's wind and the
 * vibrations a warden follows.
 */
public final class MobWorld {
    /** § 18: chance, per stone block mined below y 0, of breaking into a silverfish nest; more the deeper. */
    static final double NEST_CHANCE = 0.002, NEST_CHANCE_PER_DEPTH = 0.00006;
    static final int NEST_MIN = 3, NEST_MAX = 6;
    /** § 18: infested stone this close to a block being mined is shaken open by it. */
    static final int VIBRATION_RADIUS = 2;
    static final double INFESTED_BREAK_CHANCE = 0.35;
    /** § 19: how far endermites sense a pearl landing. */
    static final double ENDERMITE_RANGE = 32;
    /** § 31: shulkers this close, on the same target, fire together. */
    static final double VOLLEY_RANGE = 16;
    static final int VOLLEY_COOLDOWN = 30;
    /** § 38: what a wind burst reaches. */
    static final int WIND_RADIUS = 2;

    private static final Map<UUID, Long> volleyReady = new WeakHashMap<>();

    private MobWorld() { }

    public static void initialize() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) mined(server, serverPlayer, pos, state);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ShulkerBullet bullet) volley(level, bullet);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(MobWorld::machinery);
    }

    // ---------------------------------------------------------------- § 18 silverfish

    private static void mined(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (player.isCreative() || player.isSpectator()) return;
        // Mining shakes the rock: infested stone close by gives up what is in it.
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-VIBRATION_RADIUS, -VIBRATION_RADIUS, -VIBRATION_RADIUS),
                pos.offset(VIBRATION_RADIUS, VIBRATION_RADIUS, VIBRATION_RADIUS))) {
            BlockState around = level.getBlockState(near);
            if (around.getBlock() instanceof InfestedBlock infested && level.getRandom().nextDouble() < INFESTED_BREAK_CHANCE) {
                level.setBlockAndUpdate(near, infested.hostStateByInfested(around));
                EntityTypes.SILVERFISH.spawn(level, near.immutable(), EntitySpawnReason.TRIGGERED);
            }
        }
        if (pos.getY() >= 0 || !state.is(BlockTags.BASE_STONE_OVERWORLD)) return;
        double chance = NEST_CHANCE + NEST_CHANCE_PER_DEPTH * -pos.getY();
        if (level.getRandom().nextDouble() >= chance) return;
        int count = NEST_MIN + level.getRandom().nextInt(NEST_MAX - NEST_MIN + 1);
        for (int i = 0; i < count; i++) {
            var silverfish = EntityTypes.SILVERFISH.spawn(level, pos, EntitySpawnReason.TRIGGERED);
            if (silverfish != null) silverfish.setTarget(player);
        }
        level.playSound(null, pos, SoundEvents.SILVERFISH_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.6f);
    }

    // ---------------------------------------------------------------- § 19 endermites

    /** A pearl came down here: endermites around go to where the teleport lands. */
    public static void pearlLanded(ServerLevel level, Vec3 at, Entity owner) {
        if (owner instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        for (Endermite mite : level.getEntitiesOfClass(Endermite.class, new net.minecraft.world.phys.AABB(at, at).inflate(ENDERMITE_RANGE))) {
            Perception.attract(mite, at);
        }
    }

    // ---------------------------------------------------------------- § 31 shulker volleys

    private static void volley(ServerLevel level, ShulkerBullet bullet) {
        if (!(bullet.getOwner() instanceof Shulker owner) || !(owner.getTarget() instanceof LivingEntity target)) return;
        long now = level.getGameTime();
        volleyReady.put(owner.getUUID(), now + VOLLEY_COOLDOWN);
        for (Shulker other : level.getEntitiesOfClass(Shulker.class, owner.getBoundingBox().inflate(VOLLEY_RANGE),
                shulker -> shulker != owner && shulker.getTarget() == target)) {
            if (volleyReady.getOrDefault(other.getUUID(), 0L) > now) continue;
            // Marked before firing, so its own bullet does not start another volley.
            volleyReady.put(other.getUUID(), now + VOLLEY_COOLDOWN);
            level.addFreshEntity(new ShulkerBullet(level, other, target, other.getAttachFace().getAxis()));
        }
    }

    // ---------------------------------------------------------------- § 37 warden

    /** Ticks between the vibrations of a running machine. */
    static final int MACHINE_PULSE_TICKS = 40;
    /** How far from a warden running machinery is felt. */
    static final int MACHINE_RANGE_CHUNKS = 1;

    /**
     * Heavy machinery underground shakes the rock: every running part near a warden sends a vibration
     * out at a steady beat, and the warden comes to see. Only looked for where a warden is.
     */
    private static void machinery(ServerLevel level) {
        if (level.getGameTime() % MACHINE_PULSE_TICKS != 0) return;
        for (var warden : level.getEntities(EntityTypes.WARDEN, warden -> warden.isAlive())) {
            net.minecraft.world.level.ChunkPos centre = warden.chunkPosition();
            for (int dx = -MACHINE_RANGE_CHUNKS; dx <= MACHINE_RANGE_CHUNKS; dx++) {
                for (int dz = -MACHINE_RANGE_CHUNKS; dz <= MACHINE_RANGE_CHUNKS; dz++) {
                    var chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
                    if (chunk == null) continue;
                    for (var entity : chunk.getBlockEntities().values()) {
                        if (entity instanceof de.ipnats.hardwrought.machinery.KineticBlockEntity part && part.kineticSpeed() != 0) {
                            level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.BLOCK_ACTIVATE, part.getBlockPos());
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- § 38 wind

    /**
     * A burst of wind here: weak flames go out, lit candles and campfires are blown out, and gas is
     * driven away from the centre — which may just as well help the player as hurt them.
     */
    public static void wind(ServerLevel level, Vec3 at) {
        BlockPos centre = BlockPos.containing(at);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-WIND_RADIUS, -WIND_RADIUS, -WIND_RADIUS),
                centre.offset(WIND_RADIUS, WIND_RADIUS, WIND_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                level.removeBlock(pos, false);
            } else if ((state.getBlock() instanceof CampfireBlock || state.getBlock() instanceof AbstractCandleBlock)
                    && state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
                level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, false));
            } else if (state.getBlock() instanceof GasBlock) {
                Vec3 away = Vec3.atCenterOf(pos).subtract(at);
                Direction direction = away.lengthSqr() < 1.0e-4 ? Direction.UP
                        : Direction.getApproximateNearest(away.x, away.y, away.z);
                GasPush.push(level, pos.immutable(), direction);
            }
        }
    }
}
