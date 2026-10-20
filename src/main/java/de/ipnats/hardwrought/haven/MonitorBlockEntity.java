package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.core.registry.ModEffects;
import de.ipnats.hardwrought.survival.Ultra;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A bedside monitor: a circuit that watches whoever lies in the bed beside it, and makes that bed a
 * sickbed. Asleep in it, a player heals {@link #HEAL_PER_SECOND} health a second on top of anything
 * else, and is cared for as if {@link #CARE} times as well against what ails them (see
 * {@code Diseases.care}). In Ultra, where nothing heals by itself, the sickbed heals only a wound kept
 * dressed — under a bandage or a sterile dressing.
 */
public class MonitorBlockEntity extends ApplianceBlockEntity {
    public static final float HEAL_PER_SECOND = 0.5f;
    public static final double CARE = 2.5;
    /** How near the bed has to be. */
    public static final int BESIDE = 2;
    private static final long TENDED_TICKS = 40;

    /** Who lies in a sickbed, until when. */
    private static final Map<UUID, Long> TENDED = new WeakHashMap<>();

    private int patients;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.MONITOR_ENTITY, pos, state);
    }

    /** Whether this player lies in a sickbed now. */
    public static boolean tended(ServerPlayer player) {
        Long until = TENDED.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    @Override
    protected void work(ServerLevel level, double power) {
        patients = 0;
        if (power <= 0.0) return;
        boolean ultra = Ultra.active(level.getServer());
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(BESIDE + 1))) {
            if (!player.isSleeping()) continue;
            BlockPos bed = player.getSleepingPos().orElse(null);
            if (bed == null || !(level.getBlockState(bed).getBlock() instanceof AbstractBedBlock) || bed.distManhattan(worldPosition) > BESIDE + 1) continue;
            patients++;
            TENDED.put(player.getUUID(), level.getGameTime() + TENDED_TICKS);
            if (ultra && !player.hasEffect(ModEffects.BANDAGED)) continue;
            if (player.getHealth() < player.getMaxHealth()) player.heal(HEAL_PER_SECOND * (float) Math.min(1.0, power));
        }
    }

    @Override
    protected Component state() {
        return Component.translatable("message.hardwrought.meter.monitor", patients);
    }
}
