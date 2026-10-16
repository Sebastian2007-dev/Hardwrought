package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What fatigue does once it runs high. Below {@link #TIRED} it is only felt in slower recovery and
 * mining (see {@code SurvivalSystem}); above, it is plain to see and to feel, in three stages:
 *
 * <pre>
 *   50  tired       a little slower; now and then the eyes fall shut for a moment
 *   70  exhausted   slower on foot and with a blade; they fall shut often, and for longer
 *   85  overtired   slower still; microsleep — a couple of seconds of black, with no moving —
 *                   and things heard behind that are not there
 * </pre>
 *
 * The stage is an effect ({@link ModEffects#TIRED}, its level the stage) that carries the slowness
 * itself; the blinking, the microsleep's black and the phantom sounds are drawn by the client from it.
 */
public final class Tiredness {
    public static final double TIRED = 50, EXHAUSTED = 70, OVERTIRED = 85;
    /** The chance each second, overtired, of a microsleep. */
    static final double MICROSLEEP_CHANCE = 1.0 / 75.0;
    public static final int MICROSLEEP_TICKS = 40;

    private Tiredness() { }

    /** The stage this fatigue is at: -1 for none, then 0, 1 and 2. */
    public static int stage(double fatigue) {
        return fatigue >= OVERTIRED ? 2 : fatigue >= EXHAUSTED ? 1 : fatigue >= TIRED ? 0 : -1;
    }

    /** Once a second, from the metabolism pass: the stage shown and carried, and perhaps a microsleep. */
    public static void apply(ServerPlayer player, double fatigue) {
        int stage = player.isSleeping() ? -1 : stage(fatigue);
        if (stage < 0) {
            player.removeEffect(ModEffects.TIRED);
            return;
        }
        MobEffectInstance now = player.getEffect(ModEffects.TIRED);
        if (now == null || now.getAmplifier() != stage) player.removeEffect(ModEffects.TIRED);
        player.addEffect(new MobEffectInstance(ModEffects.TIRED, 60, stage, false, false, true));
        if (stage == 2 && !player.hasEffect(ModEffects.MICROSLEEP) && player.getRandom().nextDouble() < MICROSLEEP_CHANCE) {
            player.setSprinting(false);
            player.addEffect(new MobEffectInstance(ModEffects.MICROSLEEP, MICROSLEEP_TICKS, 0, false, false, true));
        }
    }

    /** Tired: slower on foot and with a blade, more so the higher the stage. */
    public static class Tired extends MobEffect {
        public Tired(MobEffectCategory category, int color) {
            super(category, color);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, Hardwrought.id("tired_speed"), -0.04,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.ATTACK_SPEED, Hardwrought.id("tired_attack_speed"), -0.06,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    /** A microsleep: for its moment, the body does nothing at all. */
    public static class Microsleep extends MobEffect {
        public Microsleep(MobEffectCategory category, int color) {
            super(category, color);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, Hardwrought.id("microsleep_speed"), -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.JUMP_STRENGTH, Hardwrought.id("microsleep_jump"), -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }
}
