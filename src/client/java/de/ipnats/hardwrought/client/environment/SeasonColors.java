package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.environment.Season;
import de.ipnats.hardwrought.environment.SeasonState;
import org.joml.Vector3f;

/**
 * What the year looks like. Specification section 24.
 *
 * <p>A season a player can only read off a command is not a season. This is the half they see: the
 * world goes grey and cold in winter, dusty and bleached through high summer, and deep green in the
 * two quarters either side of it.
 *
 * <p>It is done in two places because no single one reaches everything.
 *
 * <ul>
 *   <li><b>Biome colour</b> — grass, leaves, vines, lily pads and water are tinted by the biome, and
 *       that is where most of the colour in a Minecraft landscape actually lives. Changing it is
 *       real saturation and hue control, so this is where the work is done. It is baked into the
 *       chunk meshes, which is why a season change asks for one re-render.</li>
 *   <li><b>Sky light</b> — dirt, stone, planks and everything else carries a fixed texture no tint
 *       reaches. Those are coloured by the light falling on them instead, which is both cheaper and
 *       truer: a winter afternoon is not a world repainted, it is a world under colder light. It
 *       stops at the cave mouth, as it should — a torchlit cellar has no season.</li>
 * </ul>
 *
 * <p>Both are kept gentle. The sky tint in particular multiplies <em>everything</em> outdoors, and
 * the difference between a seasonal cast and a broken-looking world is about a ten percent swing.
 */
public final class SeasonColors {
    /** What the grass and leaves move towards, and how far. */
    private record Wash(int towards, float blend, float saturation, float brightness) {
        /** Part way from this season's look to the next one's. */
        Wash towards(Wash next, float amount) {
            if (amount <= 0) return this;
            if (amount >= 1) return next;
            return new Wash(
                    mixColor(towards, next.towards, amount),
                    blend + (next.blend - blend) * amount,
                    saturation + (next.saturation - saturation) * amount,
                    brightness + (next.brightness - brightness) * amount);
        }
    }

    /*
     * These were all a good deal stronger to begin with, and a winter that took three quarters of
     * the colour out of the world looked less like winter than like a broken shader. A season
     * should be unmistakable at a glance and still leave the place recognisably itself; the biome
     * underneath has to keep showing through.
     */
    /** Cold and drained, but still grass. Not white — snow is white, winter grass is grey-fawn. */
    private static final Wash WINTER_PLANTS = new Wash(0xA9B0A6, 0.32f, 0.58f, 0.95f);
    /** High summer: dried at the edges, the green gone a little yellow and dusty. */
    private static final Wash SUMMER_PLANTS = new Wash(0xBDAE63, 0.22f, 0.88f, 1.02f);
    /** Spring and autumn, the wet quarters: a touch deeper and greener than the biome's own. */
    private static final Wash LUSH_PLANTS = new Wash(0x4C9A2A, 0.12f, 1.12f, 1.0f);

    private static final Wash WINTER_WATER = new Wash(0x5A6E7A, 0.28f, 0.65f, 0.94f);
    private static final Wash SUMMER_WATER = new Wash(0x4E7A5E, 0.12f, 0.97f, 1.0f);
    private static final Wash LUSH_WATER = new Wash(0x3A7FA8, 0.08f, 1.04f, 1.0f);

    /** The cast on everything the sky lights. Small numbers: this multiplies the whole outdoors. */
    private static final Vector3f WINTER_SKY = new Vector3f(0.95f, 0.98f, 1.04f);
    private static final Vector3f SUMMER_SKY = new Vector3f(1.05f, 1.01f, 0.93f);
    private static final Vector3f LUSH_SKY = new Vector3f(0.98f, 1.02f, 0.98f);

    /** Leaves the colour exactly as it was. */
    private static final Wash NOTHING = new Wash(0, 0f, 1f, 1f);

    private SeasonColors() { }

    public static int grass(int biomeColor) {
        return wash(biomeColor, turning(SeasonColors::plants));
    }

    public static int foliage(int biomeColor) {
        return wash(biomeColor, turning(SeasonColors::plants));
    }

    /**
     * Foliage that is already meant to look dead. Winter leaves it alone — there is nothing left to
     * take out of it — and the lush seasons do not revive it, because a dead bush in spring is
     * still a dead bush.
     */
    public static int dryFoliage(int biomeColor) {
        // Already meant to look dead: winter has nothing left to take out of it, and the lush
        // seasons do not revive it, because a dead bush in spring is still a dead bush.
        Wash wash = turning(season -> season == Season.SUMMER ? SUMMER_PLANTS : NOTHING);
        return wash(biomeColor, wash);
    }

    public static int water(int biomeColor) {
        return wash(biomeColor, turning(SeasonColors::water));
    }

    /** The cast this season puts on sky light, as a multiplier per channel. */
    public static Vector3f skyTint() {
        Season season = SeasonState.season();
        float blend = SeasonState.blend();
        Vector3f now = sky(season);
        if (blend <= 0) return now;
        // The light can follow the turn of the year every frame, where the chunk colours cannot.
        return new Vector3f(now).lerp(sky(SeasonState.next(season)), Math.min(1, blend));
    }

    /**
     * This season's look, already part of the way towards the next one's. The world starts turning
     * before the calendar does, which is both how it happens outside and the only way a season can
     * arrive without the ground changing colour between two frames.
     */
    private static Wash turning(java.util.function.Function<Season, Wash> look) {
        Season season = SeasonState.season();
        float blend = SeasonState.blend();
        Wash now = look.apply(season);
        return blend <= 0 ? now : now.towards(look.apply(SeasonState.next(season)), blend);
    }

    private static Wash plants(Season season) {
        return switch (season) {
            case WINTER -> WINTER_PLANTS;
            case SUMMER -> SUMMER_PLANTS;
            case SPRING, AUTUMN -> LUSH_PLANTS;
        };
    }

    private static Wash water(Season season) {
        return switch (season) {
            case WINTER -> WINTER_WATER;
            case SUMMER -> SUMMER_WATER;
            case SPRING, AUTUMN -> LUSH_WATER;
        };
    }

    private static Vector3f sky(Season season) {
        return switch (season) {
            case WINTER -> WINTER_SKY;
            case SUMMER -> SUMMER_SKY;
            case SPRING, AUTUMN -> LUSH_SKY;
        };
    }

    /**
     * Pull a colour towards a season: first its saturation is changed about its own brightness, then
     * it is mixed towards the season's colour, then the whole thing is lightened or darkened.
     *
     * <p>Doing the saturation around the colour's own luminance rather than around a fixed grey is
     * what keeps a dark spruce forest dark and a bright savanna bright when winter takes the colour
     * out of both.
     */
    static int wash(int color, Wash season) {
        // The alpha byte is carried through untouched, and that is not a detail. These colours are
        // ARGB; returning one with a zero top byte makes every leaf and every surface of water in
        // the world fully transparent, which is exactly what it did the first time.
        int alpha = color & 0xFF000000;
        float red = (color >> 16 & 0xFF) / 255.0f;
        float green = (color >> 8 & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;

        float luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f;
        red = luminance + (red - luminance) * season.saturation();
        green = luminance + (green - luminance) * season.saturation();
        blue = luminance + (blue - luminance) * season.saturation();

        float blend = season.blend();
        red += ((season.towards() >> 16 & 0xFF) / 255.0f - red) * blend;
        green += ((season.towards() >> 8 & 0xFF) / 255.0f - green) * blend;
        blue += ((season.towards() & 0xFF) / 255.0f - blue) * blend;

        return alpha
                | channel(red * season.brightness()) << 16
                | channel(green * season.brightness()) << 8
                | channel(blue * season.brightness());
    }

    /** Straight-line mix of two packed colours, alpha left out of it. */
    private static int mixColor(int from, int to, float amount) {
        int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
        int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return red << 16 | green << 8 | blue;
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }
}
