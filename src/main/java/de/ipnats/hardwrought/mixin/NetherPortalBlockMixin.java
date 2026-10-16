package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.nether.NetherCrossing;
import de.ipnats.hardwrought.nether.PortalStability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unstable nether portals ({@link NetherCrossing}): they open no portal on the other side, strip the
 * traveller of what they carry, and show it — sparks and smoke where a stable portal only swirls.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    @Inject(method = "getPortalDestination", at = @At("RETURN"), cancellable = true)
    private void hardwrought$cross(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos,
                                   CallbackInfoReturnable<TeleportTransition> callback) {
        TeleportTransition to = callback.getReturnValue();
        if (to != null && entity instanceof ServerPlayer player) {
            callback.setReturnValue(NetherCrossing.crossing(currentLevel, player, portalEntryPos, to));
        }
    }

    @Inject(method = "getExitPortal", at = @At("HEAD"), cancellable = true)
    private void hardwrought$noExitPortal(ServerLevel newLevel, Entity entity, BlockPos portalEntryPos,
                                          BlockPos approximateExitPos, boolean toNether, WorldBorder worldBorder,
                                          CallbackInfoReturnable<TeleportTransition> callback) {
        if (PortalStability.isStable(entity.level(), portalEntryPos)) return;
        // A portal already waiting on the other side is used as ever; only none is made.
        if (newLevel.getPortalForcer().findClosestPortalPosition(approximateExitPos, toNether, worldBorder).isPresent()) return;
        callback.setReturnValue(NetherCrossing.unstableLanding(newLevel, entity, approximateExitPos));
    }

    @Inject(method = "animateTick", at = @At("TAIL"))
    private void hardwrought$unstableLook(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (PortalStability.isStable(level, pos)) return;
        double x = pos.getX() + random.nextDouble(), y = pos.getY() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.03, 0);
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4);
        }
        if (random.nextInt(25) == 0) level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.01, 0);
        if (random.nextInt(120) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.15f, 0.5f + random.nextFloat() * 0.3f, false);
        }
    }
}
