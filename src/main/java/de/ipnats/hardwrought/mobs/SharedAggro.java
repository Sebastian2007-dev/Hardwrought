package de.ipnats.hardwrought.mobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;

/**
 * Mob specification § 2 "shared aggro", and the organised groups of §§ 24, 29, 30 and 33: a mob that
 * sets on a player tells its own kind.
 *
 * <p>Organised fighters — guardians, piglins, illagers — raise the alarm: the others take the same
 * target at once. Everything else only hears the commotion and comes to look ({@link Perception}),
 * which is how a zombie horde gathers without knowing where the player is. An elder guardian's alarm
 * carries across its whole monument.
 */
public final class SharedAggro {
    static final double RANGE = 12, ORGANISED_RANGE = 24, ELDER_RANGE = 48;

    private SharedAggro() { }

    /** Called when a mob takes a new target it did not have. */
    public static void alert(Mob mob, LivingEntity target) {
        if (!(mob.level() instanceof ServerLevel level) || !(mob instanceof Monster)) return;
        if (!(target instanceof Player player) || player.isCreative() || player.isSpectator()) return;
        boolean organised = organised(mob);
        double range = mob instanceof ElderGuardian ? ELDER_RANGE : organised ? ORGANISED_RANGE : RANGE;
        for (Mob ally : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(range),
                other -> other != mob && other.getTarget() == null && other.isAlive() && kin(mob, other))) {
            if (organised) ally.setTarget(target);
            else if (ally instanceof PathfinderMob pathfinder) Perception.attract(pathfinder, target.position());
        }
    }

    private static boolean organised(Mob mob) {
        return mob instanceof Guardian || mob instanceof AbstractPiglin || mob instanceof Raider;
    }

    /** Whether the other is one of this mob's own: its group, or else its species. */
    private static boolean kin(Mob mob, Mob other) {
        if (mob instanceof Guardian) return other instanceof Guardian;
        if (mob instanceof AbstractPiglin) return other instanceof AbstractPiglin;
        if (mob instanceof Raider || mob instanceof AbstractIllager) return other instanceof Raider;
        return other.getType() == mob.getType();
    }
}
