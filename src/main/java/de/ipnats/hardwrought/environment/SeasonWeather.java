package de.ipnats.hardwrought.environment;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;

import java.util.HashMap;
import java.util.Map;

/**
 * Specification section 24: how often it rains is part of what a season is.
 *
 * <p>This does not take the weather over. Vanilla still decides when the sky turns and rolls how
 * long the new spell will last; all this does is take that roll and lean on it. A dry summer is one
 * where the showers are short and the clear spells between them are long, and the same arrangement
 * run backwards is a wet autumn. Both halves matter — shortening the rain alone would give a summer
 * that was merely less wet rather than one that was actually dry.
 *
 * <p>Nothing here ever forces rain on or off, so a player who sets the weather with a command keeps
 * what they set. If {@code advance_weather} is off the whole pass stands down, because somebody has
 * said in as many words that they want the sky left alone.
 */
public final class SeasonWeather {
    /** No season may make a spell shorter or longer than this, however extreme its factor. */
    private static final int MIN_SPELL = 1_200;
    private static final int MAX_SPELL = 300_000;

    /** What the sky was doing last time this looked, so that a turn can be noticed. */
    private static final Map<ServerLevel, Boolean> wasRaining = new HashMap<>();

    private SeasonWeather() { }

    /**
     * Registered on a slow simulation tier. Weather does not need watching every tick: the pass
     * only has to notice a turn before the spell it is re-timing has run out, and the shortest
     * spell vanilla rolls is a great deal longer than that.
     */
    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        if (!overworld.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
            wasRaining.remove(overworld);
            return;
        }
        boolean raining = overworld.isRaining();
        Boolean before = wasRaining.put(overworld, raining);
        // The first pass after a world loads only learns the state; it has no turn to react to.
        if (before == null || before == raining) return;
        retime(overworld, raining);
    }

    public static void forget() {
        wasRaining.clear();
    }

    /**
     * The sky has just turned. Vanilla has already rolled how long it stays turned; this stretches
     * or shrinks that roll by the season. The timer counts down to the next turn either way, so one
     * multiplication does the rain when it is raining and the drought when it is not.
     */
    private static void retime(ServerLevel level, boolean raining) {
        double factor = SeasonCycle.current(level).rainFactor();
        if (factor == 1.0) return;
        WeatherData weather = level.getWeatherData();
        int rolled = weather.getRainTime();
        if (rolled <= 0) return;
        weather.setRainTime(spell(rolled, raining ? factor : 1.0 / factor));
    }

    private static int spell(int base, double factor) {
        return (int) Math.max(MIN_SPELL, Math.min(MAX_SPELL, Math.round(base * factor)));
    }
}
