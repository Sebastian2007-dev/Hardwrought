package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fanning gas along by hand: a sneaking swing at a gas block pushes what is in it one block the way
 * the player is looking. It goes only where there is room — into air, or into a gas block that is not
 * full — and what does not fit stays behind. The gas then does what gas does from there.
 *
 * <p>Gas has no outline to aim at, so the client finds the block along the line of sight and asks;
 * the server checks the reach and the gas itself before anything moves.
 */
public final class GasPush {
    /** Ticks between two pushes by the same player, so a held button is a fan, not a pump. */
    private static final int COOLDOWN = 4;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private GasPush() { }

    /** Pushes the gas at this place one block on. Returns how many units moved. */
    public static int push(ServerPlayer player, BlockPos pos, Direction direction) {
        if (player.isSpectator()) return 0;
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN) return 0;
        double reach = player.blockInteractionRange() + 1.0;
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) return 0;
        if (!level.isLoaded(pos) || !Gases.isGas(level.getBlockState(pos))) return 0;
        LAST.put(player.getUUID(), now);
        return push(level, pos, direction);
    }

    /** Pushes the gas at this place one block on, whoever does it. Returns how many units moved. */
    public static int push(ServerLevel level, BlockPos pos, Direction direction) {
        BlockPos to = pos.relative(direction);
        int moved = 0;
        for (Gas gas : Gas.values()) {
            int units = Gases.units(level.getBlockState(pos), gas);
            if (units > 0) moved += GasBlock.move(level, pos, to, gas, units, false);
        }
        if (moved > 0) {
            level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        return moved;
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
