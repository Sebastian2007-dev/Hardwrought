package de.ipnats.hardwrought.end.dragon;

import de.ipnats.hardwrought.end.EndRules;
import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The wards round the End's crystals while its dragon lives (endgame § 4): before a crystal can be
 * struck, its ward has to be broken — {@link #WARD} of harm, more round a raised dragon's — and while the
 * dragon is near it mends, broken or not. A crystal that breaks bursts, and throws whoever is near away.
 */
public final class CrystalWards {
    /** What a ward holds, and round a raised dragon's crystals. */
    public static final float WARD = 40, RISEN_WARD = 60;
    /** A crystal that has not had its ward yet. */
    public static final float UNSET = -1;
    /** How near the dragon must be for a ward to mend, and how fast it does, each second. */
    static final double MENDING_REACH = 48;
    static final float MENDING = 1.5f;

    /** A crystal's ward, kept on it by a mixin and seen by the client. */
    public interface Warded {
        float hardwrought$ward();

        void hardwrought$setWard(float ward);
    }

    private CrystalWards() { }

    static void initialize() { }

    public static float ward(EndCrystal crystal) {
        return ((Warded) crystal).hardwrought$ward();
    }

    private static EnderDragon dragon(ServerLevel level) {
        for (EnderDragon dragon : level.getDragons()) if (dragon.isAlive()) return dragon;
        return null;
    }

    private static float most(ServerLevel level) {
        EnderDragon dragon = dragon(level);
        return dragon != null && VoidDragon.risen(dragon) ? RISEN_WARD : WARD;
    }

    public static void tick(ServerLevel level, EndCrystal crystal) {
        if (!VoidDragon.inEnd(level) || crystal.tickCount % 20 != 0) return;
        Warded warded = (Warded) crystal;
        if (!EndRules.dragonAlive(level)) return;
        float most = most(level);
        if (warded.hardwrought$ward() == UNSET) {
            warded.hardwrought$setWard(most);
            return;
        }
        EnderDragon dragon = dragon(level);
        if (dragon == null || dragon.distanceTo(crystal) > MENDING_REACH) return;
        float ward = warded.hardwrought$ward();
        if (ward < most) warded.hardwrought$setWard(Math.min(most, ward + MENDING));
    }

    /** Harm on a crystal: the ward takes it while it holds. Returns true where the crystal is spared. */
    public static boolean absorb(ServerLevel level, EndCrystal crystal, DamageSource source, float damage) {
        // The fire under it a crystal does not feel anyway; vanilla sees to that, the ward does not.
        if (!VoidDragon.inEnd(level) || source.getEntity() instanceof EnderDragon || source.is(DamageTypes.GENERIC_KILL)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return false;
        Warded warded = (Warded) crystal;
        // A crystal far from everyone may not have had its ward yet: it has it from the first blow.
        if (warded.hardwrought$ward() == UNSET && EndRules.dragonAlive(level)) warded.hardwrought$setWard(most(level));
        float ward = warded.hardwrought$ward();
        Vec3 at = crystal.position().add(0, 1, 0);
        if (ward <= 0) {
            // It goes: and throws everyone near away from it.
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(crystal.blockPosition()).inflate(10))) {
                if (!player.isCreative() && !player.isSpectator()) VoidDragon.hurl(player, at, 2.4, 0.2);
            }
            return false;
        }
        float left = Math.max(0, ward - damage);
        warded.hardwrought$setWard(left);
        if (left <= 0) {
            Fx.play(level, FxEffect.CRYSTAL_WARD_BREAK, at, at, 0, 1, null);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.5f, 0.5f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0f, 1.2f);
        } else {
            Fx.play(level, FxEffect.CRYSTAL_WARD_HIT, at, at, 0, left / most(level), null);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 2.0f, 0.6f);
        }
        return true;
    }
}
