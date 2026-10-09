package de.ipnats.hardwrought.enchanting;

import de.ipnats.hardwrought.smithing.Mask;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The rules of writing runes on a piece, as plain arithmetic: the client uses them to show what a
 * rune would do where it hovers, and the server to judge what was written.
 *
 * <p>The piece is its own outline — the sixteen by sixteen shape the anvil works on — drawn three
 * times as fine, so that a rune's strokes are finer than the piece is thick. A rune is a small pattern of
 * squares, laid anywhere, turned any way, and it may hang over the edge. What counts is how much of
 * it lies on the piece: the more complete the rune, the higher the level of its enchantment. Two
 * runes may lie side by side but not over each other, so the room on a piece is what limits how much
 * can be written on it well.
 */
public final class RuneWork {
    /**
     * How much finer than the anvil's shape the writing surface is. Three squares to the pixel: fine
     * enough that a rune can be fitted to the piece closely, a square at a time.
     */
    public static final int SCALE = 3;
    /** Squares along one side of the writing surface. */
    public static final int GRID = Mask.SIZE * SCALE;
    /** The share of a rune that has to lie on the piece for the full level: a stroke over the edge is forgiven. */
    public static final double COMPLETE = 0.9;
    /** Squares of rune one measure of lapis powder writes. */
    public static final int SQUARES_PER_POWDER = 6;

    private RuneWork() { }

    /**
     * A rune's pattern: a square of squares, some of them written.
     *
     * @param size  squares along one side
     * @param cells row by row, {@code size * size} of them
     */
    public record Shape(int size, boolean[] cells) {
        public boolean at(int x, int y) {
            return x >= 0 && y >= 0 && x < size && y < size && cells[y * size + x];
        }

        public int count() {
            int count = 0;
            for (boolean cell : cells) if (cell) count++;
            return count;
        }

        /** This pattern turned a quarter clockwise, {@code turns} times. */
        public Shape turned(int turns) {
            Shape shape = this;
            for (int i = 0; i < Math.floorMod(turns, 4); i++) {
                boolean[] next = new boolean[size * size];
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) if (shape.at(x, y)) next[x * size + (size - 1 - y)] = true;
                }
                shape = new Shape(size, next);
            }
            return shape;
        }
    }

    /**
     * One rune laid on the piece.
     *
     * @param rune  the enchantment it is the rune of
     * @param x     where the pattern's left edge lies on the surface; may be off it
     * @param y     where its top edge lies
     * @param turns quarter turns clockwise
     * @param ink   what it is written with
     */
    public record Placement(Identifier rune, int x, int y, int turns, Ink ink) {
        /** A rune written in plain lapis. */
        public Placement(Identifier rune, int x, int y, int turns) {
            this(rune, x, y, turns, Ink.LAPIS);
        }

        public Placement {
            if (ink == null) ink = Ink.LAPIS;
        }
    }

    /**
     * The runes a piece carries, where they lie on it: kept on the piece, so that a later sitting
     * finds them there, writes beside them, or wipes one off to write it again.
     */
    public record Marks(List<Placement> runes) {
        private static final com.mojang.serialization.Codec<Placement> PLACEMENT =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC.fieldOf("rune").forGetter(Placement::rune),
                        com.mojang.serialization.Codec.INT.fieldOf("x").forGetter(Placement::x),
                        com.mojang.serialization.Codec.INT.fieldOf("y").forGetter(Placement::y),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("turns", 0).forGetter(Placement::turns),
                        com.mojang.serialization.Codec.STRING.optionalFieldOf("ink", "lapis").forGetter(placement -> placement.ink().id())
                ).apply(instance, (rune, x, y, turns, ink) -> new Placement(rune, x, y, turns & 3, Ink.byId(ink))));
        public static final com.mojang.serialization.Codec<Marks> CODEC = PLACEMENT.listOf().xmap(Marks::new, Marks::runes);
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Marks> STREAM_CODEC =
                net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(CODEC);

        public Marks {
            runes = List.copyOf(runes);
        }
    }

    /**
     * What a rune is written with. Lapis is the ordinary ink. Powdered mithril and adamant
     * overcharge the rune: written in them it reaches one or two levels beyond what its enchantment
     * has otherwise. They also let the piece hold more runes than its material takes by itself.
     */
    public enum Ink {
        LAPIS(0, 0), MITHRIL(1, 1), ADAMANT(2, 3);

        private final int overcharge, moreRunes;

        Ink(int overcharge, int moreRunes) {
            this.overcharge = overcharge;
            this.moreRunes = moreRunes;
        }

        /** Runes more than its material takes that a piece holds once something on it is written in this. */
        public int moreRunes() {
            return moreRunes;
        }

        /** Levels beyond the enchantment's own highest a rune in this ink can reach. */
        public int overcharge() {
            return overcharge;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Ink byId(String id) {
            for (Ink ink : values()) if (ink.id().equals(id)) return ink;
            return LAPIS;
        }
    }

    /**
     * The highest level a rune in this ink can reach. An enchantment that only has one level — it is
     * there or it is not — cannot be overcharged.
     */
    public static int highest(int own, Ink ink) {
        return own > 1 ? own + ink.overcharge() : own;
    }

    /**
     * How hard a rune is to come by, 0 to 3, from how rare its enchantment is. The rarer, the more
     * bookshelves and levels it wants, the more it costs to write, and the larger its pattern.
     * What vanilla kept for treasure chests and librarians — mending, frost walker and their kind —
     * is of the rarest sort whatever its weight says.
     */
    public static int tier(net.minecraft.core.Holder<Enchantment> rune) {
        if (rune.is(net.minecraft.tags.EnchantmentTags.TREASURE)) return 3;
        int weight = rune.value().getWeight();
        return weight >= 10 ? 0 : weight >= 5 ? 1 : weight >= 2 ? 2 : 3;
    }

    /** Bookshelves round the table a rune of this tier wants. */
    public static int shelvesFor(int tier) {
        return new int[] {0, 4, 9, 15}[tier];
    }

    /** The level its writer has to have had on sitting down. */
    public static int levelFor(int tier) {
        return new int[] {1, 8, 16, 25}[tier];
    }

    /** Levels of experience writing it takes. */
    public static int experienceFor(int tier) {
        return tier + 1;
    }

    /**
     * Squares along one side of a rune of this tier. A common rune is a little over two of the
     * piece's pixels across and a rare one three: large enough that a small piece does not hold every
     * rune it could take whole, so that which rune gets the room is a choice.
     */
    public static int size(int tier) {
        return size(tier, 0);
    }

    /**
     * As {@link #size(int)}, on a coarser material. Adamant takes the finest strokes; on mithril
     * every rune comes out a square larger, and on everything below two. The same piece that holds
     * its runes comfortably in adamant has to be planned square by square in iron.
     *
     * @param coarser squares larger than on adamant: 0, 1 or 2
     */
    public static int size(int tier, int coarser) {
        return (tier >= 2 ? 9 : 7) + Math.clamp(coarser, 0, 2);
    }

    /**
     * The pattern of a rune. Every enchantment has its own, always the same: a spine and a few
     * strokes off it, worked out from its name, so that the server and every client draw the same
     * rune without it being written down anywhere, and an enchantment from a datapack has one too.
     */
    public static Shape shape(Identifier rune, int tier) {
        return shape(rune, tier, 0);
    }

    /** As {@link #shape(Identifier, int)}, as large as the material writes it (see {@link #size(int, int)}). */
    public static Shape shape(Identifier rune, int tier, int coarser) {
        int size = size(tier, coarser);
        boolean[] cells = new boolean[size * size];
        Random random = new Random(rune.toString().hashCode() * 31L + 7);
        int middle = size / 2;
        boolean upright = random.nextBoolean();
        for (int i = 0; i < size; i++) cells[upright ? i * size + middle : middle * size + i] = true;
        int strokes = size - 2;
        int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int stroke = 0; stroke < strokes; stroke++) {
            // Every stroke starts on what is already written, so the rune is one figure.
            List<Integer> written = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) if (cells[i]) written.add(i);
            int from = written.get(random.nextInt(written.size()));
            int[] way = ways[random.nextInt(ways.length)];
            int x = from % size, y = from / size;
            int length = 2 + random.nextInt(size - 2);
            for (int step = 0; step < length; step++) {
                x += way[0];
                y += way[1];
                if (x < 0 || y < 0 || x >= size || y >= size) break;
                cells[y * size + x] = true;
            }
        }
        return new Shape(size, cells);
    }

    /** Whether this square of the surface is piece. */
    public static boolean onPiece(Mask piece, int x, int y) {
        return x >= 0 && y >= 0 && x < GRID && y < GRID && piece.get(x / SCALE, y / SCALE);
    }

    /** The squares a laid rune writes, as {@code x, y} pairs on the surface; some may be off it. */
    public static List<int[]> squares(Shape shape, Placement placement) {
        Shape turned = shape.turned(placement.turns());
        List<int[]> squares = new ArrayList<>();
        for (int y = 0; y < turned.size(); y++) {
            for (int x = 0; x < turned.size(); x++) {
                if (turned.at(x, y)) squares.add(new int[] {placement.x() + x, placement.y() + y});
            }
        }
        return squares;
    }

    /** How many of these squares lie on the piece. */
    public static int written(Mask piece, List<int[]> squares) {
        int written = 0;
        for (int[] square : squares) if (onPiece(piece, square[0], square[1])) written++;
        return written;
    }

    /** Whether two runes lie over each other: a square of one on a square of the other. Side by side they may lie. */
    public static boolean overlap(List<int[]> one, List<int[]> other) {
        for (int[] a : one) {
            for (int[] b : other) {
                if (a[0] == b[0] && a[1] == b[1]) return true;
            }
        }
        return false;
    }

    /**
     * The level a rune gives: its enchantment's highest when it lies on the piece (almost) whole, and
     * less in step with what is missing. Nought means too little of it is on the piece to work at all.
     *
     * <p>Written on a part — before the tool is put together — a rune works itself in fully. On a
     * finished tool it reaches one level short of the highest; an enchantment that only has one level
     * still takes.
     */
    public static int level(int written, int squares, int highest, boolean part) {
        if (squares <= 0) return 0;
        double complete = Math.min(1.0, written / (double) squares / COMPLETE);
        int level = (int) Math.floor(complete * highest + 1e-6);
        return Math.min(level, part ? highest : Math.max(1, highest - 1));
    }

    /** Measures of powder, of whichever ink, it takes to write this many squares onto the piece. */
    public static int powder(int written) {
        return written <= 0 ? 0 : (written + SQUARES_PER_POWDER - 1) / SQUARES_PER_POWDER;
    }

    /**
     * How many runes a piece takes with these on it: what its material takes, and one more where
     * mithril is among the inks, three more where adamant is. The finest ink on the piece counts.
     */
    public static int capacity(int material, java.util.Collection<Placement> runes) {
        int more = 0;
        for (Placement rune : runes) more = Math.max(more, rune.ink().moreRunes());
        return material + more;
    }

    /** Craftsmanship from which a well made piece holds a rune more, and how much more of it each further rune takes. */
    public static final float WELL_MADE = 0.75f, FINER_STILL = 0.25f;

    /**
     * Runes more than its material takes that a piece holds for being well made: one from 75 %
     * craftsmanship, two from 100 %, three from 125 % — which only a piece forged well and then
     * ground, or worked up with a true hand, reaches. This is what makes the smith's care worth
     * having to the enchanter: the best armor is the best made.
     */
    public static int forCraftsmanship(float craftsmanship) {
        if (craftsmanship < WELL_MADE) return 0;
        return 1 + (int) Math.floor((craftsmanship - WELL_MADE) / FINER_STILL + 1e-4);
    }

    /** How many runes a material takes, from how readily it takes enchantment: gold six, iron four, stone three. */
    public static int capacity(int enchantability) {
        return Math.clamp(2 + enchantability / 5, 2, 7);
    }
}
