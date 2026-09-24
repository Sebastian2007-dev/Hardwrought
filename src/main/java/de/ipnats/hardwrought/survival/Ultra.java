package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Ultra Survival and Ultra Hardcore: survival and hardcore with the mod's hardest rules switched on.
 *
 * <p>Chosen when the world is made, with the Ultra switch next to the game mode, and kept as a game
 * rule of the world. What it changes:
 * <ul>
 *   <li>nobody is handed a pack: the player's own grid is earned, a row at a time — woven pack,
 *       frame, leather;</li>
 *   <li>the difficulty is Hard, and locked there;</li>
 *   <li>health does not come back by itself. Only effects heal — a regeneration potion, a golden
 *       apple — or a night's sleep good enough to count, above {@link #HEALING_SLEEP} quality.</li>
 * </ul>
 */
public final class Ultra {
    /** Sleep better than this heals; anything less only rests. */
    public static final double HEALING_SLEEP = 0.75;
    /** Health a good sleep gives back per tick: a full bar over a few minutes of night. */
    public static final float SLEEP_HEAL_PER_TICK = 0.005f;

    public static final GameRule<Boolean> RULE = GameRuleBuilder.forBoolean(false)
            .category(GameRuleCategory.PLAYER)
            .buildAndRegister(Hardwrought.id("ultra"));

    private static volatile boolean active;
    private static boolean initialized;

    private Ultra() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ServerLifecycleEvents.SERVER_STARTED.register(Ultra::refresh);
        net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents.changeCallback(RULE)
                .register((value, server) -> refresh(server));
    }

    /** Reads the rule off the world and puts what follows from it in place. */
    public static void refresh(MinecraftServer server) {
        if (server == null) return;
        active = server.overworld().getGameRules().get(RULE);
        if (active) {
            server.overworld().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
            server.setDifficulty(net.minecraft.world.Difficulty.HARD, true);
            server.setDifficultyLocked(true);
        }
        de.ipnats.hardwrought.knowledge.Journal.setUltra(active);
    }

    /** Whether the running world is an Ultra one. */
    public static boolean active() {
        return active;
    }

    public static boolean active(MinecraftServer server) {
        return server != null && server.overworld() != null && server.overworld().getGameRules().get(RULE);
    }
}
