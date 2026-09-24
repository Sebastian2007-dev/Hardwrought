package de.ipnats.hardwrought.smithing;

import java.util.List;

/**
 * A sixteen-by-sixteen grid of squares that hold metal or do not: the shape of a piece on the anvil.
 * Immutable; every blow makes a new one.
 */
public final class Mask {
    public static final int SIZE = 16;

    private final long[] bits;

    private Mask(long[] bits) {
        this.bits = bits;
    }

    public static Mask empty() {
        return new Mask(new long[4]);
    }

    public static Mask of(List<Long> words) {
        long[] bits = new long[4];
        for (int word = 0; word < 4; word++) bits[word] = words.get(word);
        return new Mask(bits);
    }

    public static Mask of(long[] words) {
        if (words.length != 4) throw new IllegalArgumentException("A shape is four words");
        return new Mask(words.clone());
    }

    public List<Long> words() {
        return List.of(bits[0], bits[1], bits[2], bits[3]);
    }

    public long[] toArray() {
        return bits.clone();
    }

    public boolean get(int x, int y) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return false;
        int index = y * SIZE + x;
        return (bits[index >> 6] >>> (index & 63) & 1L) != 0;
    }

    public Mask with(int x, int y, boolean filled) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return this;
        long[] copy = bits.clone();
        int index = y * SIZE + x;
        if (filled) copy[index >> 6] |= 1L << (index & 63);
        else copy[index >> 6] &= ~(1L << (index & 63));
        return new Mask(copy);
    }

    public int count() {
        int total = 0;
        for (long word : bits) total += Long.bitCount(word);
        return total;
    }

    /** How many squares differ from the other shape. */
    public int mismatch(Mask other) {
        int total = 0;
        for (int word = 0; word < 4; word++) total += Long.bitCount(bits[word] ^ other.bits[word]);
        return total;
    }

    /** A blow of the iron hammer: three by three squares around where it lands. */
    public Mask strike(int x, int y, Mask target) {
        return strike(x, y, target, 3);
    }

    /**
     * One blow of the hammer on a square: every square it covers that is wrong is put right — metal
     * standing proud is driven in, a gap is filled from the mass around it. How much it covers is the
     * hammer's {@link Hammers#reach}: one square, two by two, or three by three. A blow that finds
     * nothing wrong where it lands changes nothing, which is how a wasted blow is told apart.
     */
    public Mask strike(int x, int y, Mask target, int reach) {
        long[] copy = bits.clone();
        for (int dy = Hammers.from(reach); dy <= Hammers.to(reach); dy++) {
            for (int dx = Hammers.from(reach); dx <= Hammers.to(reach); dx++) {
                int cx = x + dx;
                int cy = y + dy;
                if (cx < 0 || cy < 0 || cx >= SIZE || cy >= SIZE) continue;
                int index = cy * SIZE + cx;
                long bit = 1L << (index & 63);
                if (target.get(cx, cy)) copy[index >> 6] |= bit;
                else copy[index >> 6] &= ~bit;
            }
        }
        return new Mask(copy);
    }
}
