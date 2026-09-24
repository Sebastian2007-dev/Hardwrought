package de.ipnats.hardwrought.client.smithing;

import com.mojang.serialization.MapCodec;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.smithing.Heat;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Hot metal looks hot. Two pieces the item models use, both read off the piece's heat as it is now:
 * a condition that switches a metal item to its pale glowing texture while it is hot enough to
 * glow, and a tint that colours that texture the way hot iron looks — dull red at the edge of
 * visible heat, cherry, orange, and yellow-white fresh from the fire.
 *
 * <p>The item definitions that use them are written by {@code tools/glow_textures.py}.
 */
public final class HeatLook {
    /** Below this, metal does not glow visibly, even in the dark. */
    public static final double GLOWS_FROM = 450.0;

    /** Colour temperature stops: degrees and the colour of iron at that heat. */
    private static final double[] STOPS = { 450, 650, 850, 1050, 1250 };
    private static final int[] COLOURS = { 0x5A1206, 0x9E1E0C, 0xE0461A, 0xFF9A2E, 0xFFEBA8 };

    private HeatLook() { }

    /** Hot enough to glow. */
    public record Hot() implements ConditionalItemModelProperty {
        public static final MapCodec<Hot> CODEC = MapCodec.unit(new Hot());

        @Override
        public boolean get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext context) {
            return Heat.of(stack, time(level)) >= GLOWS_FROM;
        }

        @Override
        public MapCodec<Hot> type() {
            return CODEC;
        }
    }

    /** The colour of the glow. */
    public record Tint() implements ItemTintSource {
        public static final MapCodec<Tint> CODEC = MapCodec.unit(new Tint());

        @Override
        public int calculate(ItemStack stack, ClientLevel level, LivingEntity owner) {
            return 0xFF000000 | colour(Heat.of(stack, time(level)));
        }

        @Override
        public MapCodec<Tint> type() {
            return CODEC;
        }
    }

    /** The glow colour at this temperature, blended between the stops. */
    public static int colour(double celsius) {
        if (celsius <= STOPS[0]) return COLOURS[0];
        for (int stop = 1; stop < STOPS.length; stop++) {
            if (celsius <= STOPS[stop]) {
                double t = (celsius - STOPS[stop - 1]) / (STOPS[stop] - STOPS[stop - 1]);
                return blend(COLOURS[stop - 1], COLOURS[stop], t);
            }
        }
        return COLOURS[COLOURS.length - 1];
    }

    private static int blend(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return r << 16 | g << 8 | b;
    }

    private static long time(ClientLevel level) {
        if (level != null) return level.getGameTime();
        var client = net.minecraft.client.Minecraft.getInstance();
        return client.level == null ? 0 : client.level.getGameTime();
    }

    public static void initialize() {
        ConditionalItemModelProperties.ID_MAPPER.put(Hardwrought.id("hot"), Hot.CODEC);
        ItemTintSources.ID_MAPPER.put(Hardwrought.id("heat"), Tint.CODEC);
    }
}
