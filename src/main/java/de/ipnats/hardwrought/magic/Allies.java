package de.ipnats.hardwrought.magic;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Who a spell meant for friends is meant for. A healing ring should not mend the zombie standing in
 * it, and a shroud should hide its caster's companions, not the skeleton hunting them.
 */
final class Allies {
    private Allies() { }

    /**
     * Whether a living thing is on the caster's side: the caster, their tamed animals, the peaceful
     * creatures of the world, and other players when both are in the same party or stand in the same
     * team. Monsters never are. A stranger is not a friend.
     */
    static boolean ally(LivingEntity caster, LivingEntity target) {
        if (target == caster) return true;
        if (target instanceof Enemy) return false;
        if (caster == null) return true;
        if (target instanceof OwnableEntity owned && owned.getOwner() != null) return ally(caster, owned.getOwner());
        if (target instanceof Player player) {
            if (caster instanceof Player own && de.ipnats.hardwrought.party.Parties.together(own, player)) return true;
            return caster.getTeam() != null && caster.isAlliedTo(target);
        }
        return true;
    }
}
