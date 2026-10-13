package de.ipnats.hardwrought.environment;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import static net.minecraft.commands.Commands.literal;

/**
 * {@code /day}: what day it is, and where in its season that day falls.
 *
 * <p>Hardwrought asks a player to plan around a calendar — to sow in spring, to lay in fuel before
 * winter, to cross a tundra in the one quarter it can be crossed. None of that is possible if the
 * only way to know the date is to count sunrises, so the date is simply askable. There is no item
 * to craft first and no block to stand at: a person knows roughly what time of year it is by
 * looking around, and this command is that knowledge written down.
 *
 * <p>It tells and never changes. Setting the season is a debug tool and lives with the other debug
 * tools, behind the permission they all share.
 */
public final class SeasonCommands {
    private SeasonCommands() { }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            dispatcher.register(literal("day").executes(context -> day(context.getSource())));
        });
    }

    private static int day(CommandSourceStack source) {
        long clock = clock(source);
        Season season = SeasonCycle.at(clock);
        source.sendSuccess(() -> Component.translatable("commands.hardwrought.day",
                SeasonCycle.dayOfWorld(clock),
                SeasonCycle.dayInSeason(clock),
                SeasonCycle.DAYS_PER_SEASON,
                Component.translatable(season.translationKey())), false);
        return (int) Math.min(Integer.MAX_VALUE, SeasonCycle.dayOfWorld(clock));
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
