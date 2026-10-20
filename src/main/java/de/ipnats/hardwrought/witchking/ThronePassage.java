package de.ipnats.hardwrought.witchking;

import de.ipnats.hardwrought.underworld.Underworld;
import de.ipnats.hardwrought.underworld.UnderworldGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

import java.util.Set;

/**
 * The way the beaten Witch King leaves open: his cold throne and its seat in the overworld, each leading to
 * the other. Sitting on the throne carries one up to the seat; the first time (or when its seat is gone)
 * the seat is set down where the player sleeps, or at the world's spawn. Sitting on the seat carries one
 * down to the foot of the throne.
 */
public final class ThronePassage {
    private ThronePassage() { }

    /** From the cold throne up to its seat, setting one down by the player's home if it has none standing. */
    static void up(ServerPlayer player, BlockPos throne) {
        MinecraftServer server = player.level().getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null || !(player.level().getBlockEntity(throne) instanceof WitchKingThrone.Entity origin)) return;
        BlockPos seat = origin.seat();
        if (seat == null || !bound(overworld, seat, throne)) {
            seat = setDown(overworld, home(player, overworld), throne);
            origin.link(seat);
        }
        Direction facing = overworld.getBlockState(seat).getValue(WitchKingSeat.FACING);
        BlockPos landing = UnderworldGate.nearest(overworld, seat.relative(facing), 4);
        if (landing == null) landing = UnderworldGate.ground(overworld, seat.relative(facing), 8);
        travel(player, overworld, landing, facing.toYRot());
        overworld.playSound(null, landing, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    /** From a seat down to the foot of the throne it is bound to; the throne then leads back up to this seat. */
    static void down(ServerPlayer player, BlockPos seat) {
        MinecraftServer server = player.level().getServer();
        ServerLevel underworld = server.getLevel(Underworld.LEVEL);
        BlockPos throne = player.level().getBlockEntity(seat) instanceof WitchKingSeat.Entity bound ? bound.throne() : null;
        if (throne == null) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.witch_king_seat.unbound"));
            return;
        }
        if (underworld == null || !(load(underworld, throne).getBlockEntity(throne) instanceof WitchKingThrone.Entity origin)
                || !underworld.getBlockState(throne).getValue(WitchKingThrone.COLD)) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.witch_king_seat.silent"));
            return;
        }
        origin.link(seat);
        // The throne faces south, down its hall.
        BlockPos landing = UnderworldGate.nearest(underworld, throne.south(), 4);
        if (landing == null) landing = UnderworldGate.ground(underworld, throne.south(2), 8);
        travel(player, underworld, landing, Direction.SOUTH.toYRot());
        underworld.playSound(null, throne, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.5f, 0.5f);
    }

    /** A seat has been set down somewhere: its throne now leads up to it. */
    static void link(MinecraftServer server, BlockPos throne, BlockPos seat) {
        ServerLevel underworld = server.getLevel(Underworld.LEVEL);
        if (underworld != null && load(underworld, throne).getBlockEntity(throne) instanceof WitchKingThrone.Entity origin) origin.link(seat);
    }

    private static boolean bound(ServerLevel overworld, BlockPos seat, BlockPos throne) {
        return load(overworld, seat).getBlockEntity(seat) instanceof WitchKingSeat.Entity there && throne.equals(there.throne());
    }

    /** Where the player sleeps if that is in the overworld, else the world's spawn. */
    private static BlockPos home(ServerPlayer player, ServerLevel overworld) {
        ServerPlayer.RespawnConfig config = player.getRespawnConfig();
        if (config != null && config.respawnData().dimension() == Level.OVERWORLD) return config.respawnData().pos();
        return overworld.getRespawnData().pos();
    }

    private static BlockPos setDown(ServerLevel overworld, BlockPos home, BlockPos throne) {
        BlockPos spot = UnderworldGate.nearest(overworld, home, 6);
        if (spot == null) spot = UnderworldGate.ground(overworld, home, 12);
        overworld.setBlockAndUpdate(spot, WitchKingContent.SEAT.defaultBlockState());
        if (overworld.getBlockEntity(spot) instanceof WitchKingSeat.Entity seat) seat.bind(throne);
        overworld.sendParticles(ParticleTypes.SOUL, spot.getX() + 0.5, spot.getY() + 0.8, spot.getZ() + 0.5, 30, 0.4, 0.5, 0.4, 0.05);
        return spot;
    }

    private static void travel(ServerPlayer player, ServerLevel to, BlockPos landing, float yaw) {
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
        player.teleportTo(to, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, Set.of(), yaw, 0, true);
        to.sendParticles(ParticleTypes.SOUL, landing.getX() + 0.5, landing.getY() + 1, landing.getZ() + 0.5, 40, 0.5, 1.2, 0.5, 0.08);
        to.playSound(null, landing, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
    }

    /** The level, with the chunk at a position loaded. */
    private static ServerLevel load(ServerLevel level, BlockPos pos) {
        level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        return level;
    }
}
