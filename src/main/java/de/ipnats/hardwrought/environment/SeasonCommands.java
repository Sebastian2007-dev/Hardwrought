package de.ipnats.hardwrought.environment;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import static net.minecraft.commands.Commands.literal;

/**
 * {@code /day} and {@code /season}: what day it is, and what that means.
 *
 * <p>Hardwrought asks a player to plan around a calendar — to sow in spring, to lay in fuel before
 * winter, to cross a tundra in the one quarter it can be crossed. None of that is possible if the
 * only way to know the date is to count sunrises, so the date is simply askable. There is no item
 * to craft first and no block to stand at: a person knows roughly what time of year it is by
 * looking around, and these two commands are that knowledge written down.
 *
 * <p>They tell and never change. Setting the season is a debug tool and lives with the other debug
 * tools, behind the permission they all share.
 */
public final class SeasonCommands {
    private SeasonCommands() { }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            dispatcher.register(literal("day").executes(context -> day(context.getSource())));
            dispatcher.register(literal("season").executes(context -> season(context.getSource())));
        });
    }

    private static int day(CommandSourceStack source) {
        long clock = clock(source);
        Season season = SeasonCycle.at(clock);
        source.sendSuccess(() -> Component.translatable("commands.hardwrought.day",
                SeasonCycle.dayOfWorld(clock),
                SeasonCycle.year(clock),
                Component.translatable(season.translationKey()),
                SeasonCycle.dayInSeason(clock),
                SeasonCycle.DAYS_PER_SEASON), false);
        return (int) Math.min(Integer.MAX_VALUE, SeasonCycle.dayOfWorld(clock));
    }

    private static int season(CommandSourceStack source) {
        long clock = clock(source);
        Season season = SeasonCycle.at(clock);
        source.sendSuccess(() -> Component.translatable("commands.hardwrought.season",
                Component.translatable(season.translationKey()),
                SeasonCycle.dayInSeason(clock),
                SeasonCycle.DAYS_PER_SEASON,
                SeasonCycle.daysLeftInSeason(clock),
                Component.translatable(next(season).translationKey())), false);
        // What the season is doing, which is the half a player actually plans around.
        source.sendSuccess(() -> Component.translatable("commands.hardwrought.season.effects",
                Math.round(season.growthFactor() * 100),
                Math.round(season.rainFactor() * 100)), false);
        return SeasonCycle.dayInSeason(clock);
    }

    private static Season next(Season season) {
        Season[] seasons = Season.values();
        return seasons[(season.ordinal() + 1) % seasons.length];
    }

    /**
     * The overworld's clock, whichever dimension the asker is standing in. A player in a mine or in
     * the nether is still living through the same year as everyone else, and the calendar they need
     * is the one their crops are growing by.
     */
    private static long clock(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        return (overworld == null ? source.getLevel() : overworld).getOverworldClockTime();
    }
}
