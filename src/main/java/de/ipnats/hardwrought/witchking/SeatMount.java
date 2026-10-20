package de.ipnats.hardwrought.witchking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * What a player sits on when they sit on the cold throne or on its seat in the overworld: nothing to see,
 * only a place to ride. Sat on for {@link #SIT_TICKS}, it carries them through to the other one
 * ({@link ThronePassage}); getting up before that is staying.
 */
public class SeatMount extends Entity {
    static final int SIT_TICKS = 40;
    /** How far above the block the mount floats, so that whoever sits on it sits on the cushion. */
    private static final double HEIGHT = 0.2;
    private int sat;

    public SeatMount(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Sits a player down on the throne or seat at a position, facing the given way; false if someone sits there already. */
    static boolean sit(ServerLevel level, BlockPos pos, float yaw, Player player) {
        if (player.isPassenger() || !level.getEntitiesOfClass(SeatMount.class, new AABB(pos)).isEmpty()) return false;
        SeatMount mount = WitchKingContent.SEAT_MOUNT.create(level, EntitySpawnReason.TRIGGERED);
        if (mount == null) return false;
        mount.snapTo(pos.getX() + 0.5, pos.getY() + HEIGHT, pos.getZ() + 0.5, yaw, 0);
        level.addFreshEntity(mount);
        if (!player.startRiding(mount)) {
            mount.discard();
            return false;
        }
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        BlockPos at = blockPosition();
        BlockState state = level.getBlockState(at);
        boolean throne = state.is(WitchKingContent.THRONE) && state.getValue(WitchKingThrone.COLD);
        boolean seat = state.is(WitchKingContent.SEAT);
        if (!(getFirstPassenger() instanceof ServerPlayer player) || !throne && !seat) {
            ejectPassengers();
            discard();
            return;
        }
        sat++;
        // Souls gathering round whoever sits, faster as the passage nears.
        if (sat % Math.max(1, 6 - sat / 8) == 0) {
            double angle = sat * 0.7;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(angle) * 0.8, getY() + 0.3 + sat * 0.03,
                    getZ() + Math.sin(angle) * 0.8, 1, 0, 0, 0, 0.01);
        }
        if (sat == 1) level.playSound(null, at, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.0f, 0.5f);
        if (sat == SIT_TICKS / 2) level.playSound(null, at, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 1.5f, 0.6f);
        if (sat < SIT_TICKS) return;
        player.stopRiding();
        discard();
        if (throne) ThronePassage.up(player, at);
        else ThronePassage.down(player, at);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { }

    @Override
    protected void readAdditionalSaveData(ValueInput input) { }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) { }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
}
