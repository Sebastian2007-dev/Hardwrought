package de.ipnats.hardwrought.client.knowledge;

import de.ipnats.hardwrought.knowledge.MultiblockKnowledge;
import de.ipnats.hardwrought.knowledge.Multiblocks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * The compendium's multiblock section: a shelf of every structure built of several blocks. Which of
 * them can be read is the server's answer ({@link MultiblockKnowledge}): a structure whose every part
 * has been studied shows its name and opens its view; the rest stay unread.
 */
public final class MultiblockSection {
    private static boolean open;
    private static Set<Identifier> studied = Set.of();

    private MultiblockSection() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(MultiblockKnowledge.Known.TYPE,
                (payload, context) -> studied = new HashSet<>(payload.studied()));
    }

    /** Whether the section is the shelf showing. */
    public static boolean open() {
        return open;
    }

    /** Turns to the section, and asks again what is known of it. */
    public static void select() {
        open = true;
        ClientPlayNetworking.send(new MultiblockKnowledge.Request());
    }

    /** Back to the ordinary shelves. */
    public static void leave() {
        open = false;
    }

    public static Set<Identifier> studied() {
        return studied;
    }

    public static boolean readable(Multiblocks.Multiblock multiblock) {
        return MultiblockScreen.readable(multiblock, studied);
    }
}
