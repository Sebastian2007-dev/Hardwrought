package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.mobs.Perception;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * An electric bell: a relay strikes it as long as it has a redstone signal — from a motion lamp, a
 * pressure plate, a tripwire — and current. It wakes everyone sleeping within {@link #WAKES} blocks.
 *
 * <p>It is loud, and everything that hears it hears it: idle monsters within its reach come to see what
 * rang ({@link Perception#alert}). A bell warns; it does not protect.
 */
public class AlarmBellBlockEntity extends ApplianceBlockEntity {
    public static final double WAKES = 32.0;
    public static final double LOUDNESS = 32.0;

    private boolean rang;

    public AlarmBellBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.ALARM_BELL_ENTITY, pos, state);
    }

    /** Whether it rang in its last second. */
    public boolean rang() {
        return rang;
    }

    @Override
    protected boolean wants() {
        return level != null && level.hasNeighborSignal(worldPosition);
    }

    @Override
    protected void work(ServerLevel level, double power) {
        rang = power > 0.0;
        if (!rang) return;
        float volume = (float) (1.5 + 2.0 * Math.min(1.0, power));
        level.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, volume, 1.4f);
        level.playSound(null, worldPosition, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, volume * 0.5f, 1.2f);
        Vec3 at = Vec3.atCenterOf(worldPosition);
        for (ServerPlayer player : level.players()) {
            if (player.isSleeping() && player.position().distanceTo(at) <= WAKES) {
                player.stopSleepInBed(true, true);
                player.sendOverlayMessage(Component.translatable("message.hardwrought.alarm_bell.woken"));
            }
        }
        Perception.alert(level, at, LOUDNESS * Math.min(1.0, power));
    }

    @Override
    protected Component state() {
        return Component.translatable(rang ? "message.hardwrought.meter.bell.ringing" : "message.hardwrought.meter.bell.quiet");
    }
}
