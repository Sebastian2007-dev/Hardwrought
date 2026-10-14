package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.survival.WaterskinItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Holding a vessel of water over a fire until it boils. Both the bowl and the waterskin are used two
 * ways — held to a fire to boil, held to the mouth to drink — and both take time, so while the use
 * key is held it has to be remembered which of the two is going on. Boiling stops the moment the
 * player looks away from the fire.
 */
public final class Boiling {
    /** How long water takes to come to the boil over an open fire, in ticks: five seconds. */
    public static final int BOIL_TICKS = 100;
    /** How long a drink takes, in ticks, as long as a potion. */
    public static final int DRINK_TICKS = 32;

    /** Who is boiling something just now, rather than drinking it. Both sides keep their own. */
    private static final Map<LivingEntity, Boolean> BOILING = new WeakHashMap<>();

    private Boiling() { }

    public static void start(LivingEntity entity) {
        BOILING.put(entity, true);
    }

    public static void stop(LivingEntity entity) {
        BOILING.remove(entity);
    }

    public static boolean boiling(LivingEntity entity) {
        return BOILING.containsKey(entity);
    }

    /** Whether this stack is the one somebody is boiling: what the client draws its holding with. */
    public static boolean boiling(ItemStack stack) {
        for (LivingEntity entity : BOILING.keySet()) {
            if (entity.isUsingItem() && entity.getUseItem() == stack) return true;
        }
        return false;
    }

    /**
     * One tick of holding something over the fire: it steams and bubbles, and stops when the player is
     * no longer looking at a lit fire.
     */
    public static void tick(Level level, LivingEntity entity, int remaining) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player)) return;
        HitResult hit = player.pick(player.blockInteractionRange(), 0.0f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK
                || !WaterskinItem.isLitFire(level.getBlockState(block.getBlockPos()))) {
            stop(player);
            player.stopUsingItem();
            return;
        }
        if (remaining % 10 == 0) {
            var at = net.minecraft.world.phys.Vec3.atCenterOf(block.getBlockPos());
            server.sendParticles(remaining < BOIL_TICKS / 2 ? ParticleTypes.BUBBLE_POP : ParticleTypes.SMOKE,
                    at.x, at.y + 0.6, at.z, 2, 0.15, 0.05, 0.15, 0.01);
        }
        if (remaining % 25 == 0) {
            level.playSound(null, block.getBlockPos(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.5f, 1.3f);
        }
    }
}
