package de.ipnats.hardwrought.magic;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which runes a player knows (magic specification sections 1 and 4). This is the resource magic
 * spends instead of mana, so it is kept with the player, survives death, and is told to that
 * player's own client alone: what someone else knows is theirs to find out.
 *
 * <p>Kept as a bit per {@link Glyph}: the six element runes and the auxiliary runes alike. A new
 * player knows none.
 */
public final class RuneKnowledge {
    private static final Codec<Integer> CODEC = Codec.INT;
    private static final StreamCodec<io.netty.buffer.ByteBuf, Integer> STREAM_CODEC = ByteBufCodecs.VAR_INT;

    public static final AttachmentType<Integer> KNOWN = AttachmentRegistry.<Integer>create(Hardwrought.id("known_runes"),
            builder -> builder.persistent(CODEC).copyOnDeath().initializer(() -> 0)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private RuneKnowledge() { }

    /** Loading the class registers the attachment. */
    public static void initialize() { }

    public static boolean knows(Player player, Glyph glyph) {
        return (bits(player) & (1 << glyph.ordinal())) != 0;
    }

    public static boolean knows(Player player, Rune rune) {
        return knows(player, Glyph.of(rune));
    }

    public static boolean knows(Player player, Sign sign) {
        return knows(player, Glyph.of(sign));
    }

    public static Set<Glyph> known(Player player) {
        Set<Glyph> glyphs = EnumSet.noneOf(Glyph.class);
        for (Glyph glyph : Glyph.values()) if (knows(player, glyph)) glyphs.add(glyph);
        return glyphs;
    }

    public static boolean learn(Player player, Rune rune) {
        return learn(player, Glyph.of(rune));
    }

    /** Teaches a rune; {@code false} when it was known already. Server side only. */
    public static boolean learn(Player player, Glyph glyph) {
        int bits = bits(player);
        int next = bits | (1 << glyph.ordinal());
        if (next == bits) return false;
        player.setAttached(KNOWN, next);
        return true;
    }

    /** Forgets everything; for operators and tests. */
    public static void forget(Player player) {
        player.setAttached(KNOWN, 0);
    }

    private static int bits(Player player) {
        Integer bits = player.getAttached(KNOWN);
        return bits == null ? 0 : bits;
    }
}
