package de.ipnats.hardwrought.mobs;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;

/**
 * Small goals that only watch their mob and react — they take no control of its movement, so they
 * run beside whatever else it is doing.
 */
public final class WatchGoals {
    private WatchGoals() { }

    /** § 22: a magma cube leaves the ground it lands on hot; see {@link MobHeat}. */
    public static class HotLanding extends Goal {
        private final MagmaCube cube;
        private boolean wasOnGround = true;

        public HotLanding(MagmaCube cube) {
            this.cube = cube;
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            boolean onGround = cube.onGround();
            if (onGround && !wasOnGround && cube.level() instanceof ServerLevel level) {
                MobHeat.landed(level, cube.blockPosition().below());
            }
            wasOnGround = onGround;
        }
    }

    /**
     * § 39.2: a creaking is more dangerous where it is hard to see. In the dark it moves
     * {@value #DARK_SPEED} faster; in light, as vanilla.
     */
    public static class DarkSpeed extends Goal {
        static final Identifier MODIFIER = Hardwrought.id("creaking_dark_speed");
        static final double DARK_SPEED = 0.25;
        static final int DARK_LIGHT = 5;

        private final Mob mob;

        public DarkSpeed(Mob mob) {
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return mob.getRandom().nextInt(10) == 0;
        }

        @Override
        public void start() {
            AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed == null) return;
            boolean dark = mob.level().getMaxLocalRawBrightness(mob.blockPosition()) < DARK_LIGHT;
            if (dark && !speed.hasModifier(MODIFIER)) {
                speed.addTransientModifier(new AttributeModifier(MODIFIER, DARK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else if (!dark) {
                speed.removeModifier(MODIFIER);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
