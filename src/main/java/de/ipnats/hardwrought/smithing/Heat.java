package de.ipnats.hardwrought.smithing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * How hot a piece of metal was, and when.
 *
 * <p>The temperature is not ticked. A stack that cooled by a few degrees every tick would be a
 * changed stack every tick — resent to the client, re-saved, never stacking with anything — for the
 * sake of a number nobody is looking at. Instead the stack remembers the last temperature it was
 * given and the game time it was given it, and the current temperature is worked out whenever
 * somebody asks, by Newton's law of cooling towards the air around it.
 *
 * <p>Cooling is calibrated so that iron taken out of the fire at forging heat stays workable for
 * a little under half a minute, and is safe to touch after about two minutes.
 *
 * @param celsius the temperature at {@code since}
 * @param since   the game time the temperature was set
 */
public record Heat(float celsius, long since) {
    /** What everything cools towards. */
    public static final double AMBIENT = 20.0;
    /** Newton's constant per tick, for a small piece of metal in still air. */
    public static final double COOLING_PER_TICK = 0.0008;
    /** Below this a piece counts as cold again, and the component is dropped. */
    public static final double COLD_BELOW = 50.0;
    /** Above this bare skin gets burnt. */
    public static final double BURNS_ABOVE = 60.0;

    public static final Codec<Heat> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("celsius").forGetter(Heat::celsius),
            Codec.LONG.fieldOf("since").forGetter(Heat::since)
    ).apply(instance, Heat::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Heat> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Heat::celsius, ByteBufCodecs.VAR_LONG, Heat::since, Heat::new);

    /** The temperature at this game time. */
    public double at(long now) {
        long elapsed = Math.max(0, now - since);
        return AMBIENT + (celsius - AMBIENT) * Math.exp(-COOLING_PER_TICK * elapsed);
    }

    /** How hot this stack is right now; the air temperature where it carries no heat at all. */
    public static double of(ItemStack stack, long now) {
        Heat heat = stack.get(ModDataComponents.HEAT);
        return heat == null ? AMBIENT : heat.at(now);
    }

    public static boolean hot(ItemStack stack, long now) {
        return of(stack, now) > COLD_BELOW;
    }
}
