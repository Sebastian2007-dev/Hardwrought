package de.ipnats.hardwrought.magic;

import java.util.List;

/**
 * A sigil as its drawing describes it (sigil specification): two circles one inside the other; in
 * the inner one the main element and the stabilising figure around it; in the ring between them the
 * auxiliary runes, its instructions.
 *
 * <p>Sigils are read from the same auxiliary runes as spells, so that a rune means one thing
 * wherever it is drawn (sigil specification section 9):
 * <ul>
 *     <li>arrows give a direction — all of them together; arrows that cancel out, such as four
 *     pointing in from four sides, leave the effect hovering above the sigil (section 11);</li>
 *     <li>a small circle shapes the effect as a sphere, a small triangle makes it burst;</li>
 *     <li>a small square makes it wait for someone to come near, as a spell's square makes a trap;</li>
 *     <li>a spiral makes it pulse twice as often;</li>
 *     <li>each short stroke makes it stronger (section 13).</li>
 * </ul>
 * Every instruction costs one point of stabilisation; the figure around the element carries as
 * many points as it has corners (section 6).
 *
 * @param element the main element, {@code null} when none was read
 * @param inverted whether the element is struck through
 * @param corners the stabilising figure's corners, 0 without one
 * @param dirX the arrows' combined direction on the page, rightward, as a share of the arrows drawn
 * @param dirY the same, downward; up the page is ahead of whoever inscribes it
 * @param arrows how many arrows were drawn
 * @param shape the shape of the effect
 * @param presence whether it waits for someone to come near
 * @param pulse whether it acts twice as often
 * @param strength how many strength strokes it carries
 * @param cost the stabilisation its instructions need
 * @param glyphs the runes it is drawn with, which the inscriber must know to draw it steadily
 * @param accuracies how well each part was drawn: circles, figure, element, instructions
 * @param noise how many strokes made no sense in the sigil
 * @param lines the drawing itself, scaled so the outer circle has radius 1, for showing it on the ground
 * @param program the instructions of stage S2 and S3 (docs/magic-roadmap.md, M4 and M5)
 */
public record SigilDesign(Rune element, boolean inverted, int corners, float dirX, float dirY, int arrows,
                          Shape shape, boolean presence, boolean pulse, int strength, int cost, List<Glyph> glyphs,
                          List<Double> accuracies, int noise, List<float[]> lines, Program program) {
    /** Whom a sigil acts on (sigil specification §20, §38). */
    public enum Target {
        /** Anyone but its maker. */
        ANYONE,
        /** Monsters only: Dark in the ring. */
        HOSTILE,
        /** Only its maker's side: Dark struck through in the ring. */
        FRIENDLY
    }

    /** What has to hold for a sigil to act (sigil specification §39): a rune drawn outside its outer circle. */
    public enum Condition {
        /** Dark outside: the light where it lies is 7 or less. */
        DARK,
        /** Dark struck through: the light is 8 or more. */
        LIGHT,
        /** Fire outside: the air is 25 °C or warmer. */
        HOT,
        /** Fire struck through: the air is 5 °C or colder. */
        COLD,
        /** Lightning outside: a monster is within 8 blocks. */
        HOSTILE_NEAR,
        /** Water outside: it is raining on it. */
        RAIN
    }

    /**
     * The further instructions (docs/magic-roadmap.md, stages M4 and M5), read from runes in the ring
     * and outside it, with the meaning they have as amplifiers of a spell (sigil specification §9).
     *
     * @param range Wind runes in the ring: a directed sigil reaches × 1.5 for each
     * @param size Water runes in the ring: its sphere, burst or ring × 1.4 for each
     * @param duration Earth runes in the ring: it lasts twice as long for each
     * @param target whom it acts on
     * @param delay Lightning runes in the ring: 1.5 s between trigger and effect for each
     * @param contact two small squares: it waits for someone to stand on it, not merely near
     * @param conditions what must hold for it to act
     */
    public record Program(int range, int size, int duration, Target target, int delay, boolean contact, List<Condition> conditions) {
        public static final Program NONE = new Program(0, 0, 0, Target.ANYONE, 0, false, List.of());

        public Program {
            conditions = List.copyOf(conditions);
        }
    }

    public SigilDesign(Rune element, boolean inverted, int corners, float dirX, float dirY, int arrows,
                       Shape shape, boolean presence, boolean pulse, int strength, int cost, List<Glyph> glyphs,
                       List<Double> accuracies, int noise, List<float[]> lines) {
        this(element, inverted, corners, dirX, dirY, arrows, shape, presence, pulse, strength, cost, glyphs, accuracies, noise, lines,
                Program.NONE);
    }
    /** How the effect is shaped. */
    public enum Shape {
        /** A small, single effect: a flame, a spark, a mote. */
        POINT,
        /** A sphere: the effect reaches all round. */
        SPHERE,
        /** A burst outward where it acts. */
        BURST,
        /** A ring round the sigil (sigil specification §16): a small circle and a small triangle in the ring. */
        RING
    }

    /** The most stabilisation a figure may carry: an octagon. */
    public static final int MAX_CORNERS = 8;

    public SigilDesign {
        glyphs = List.copyOf(glyphs);
        accuracies = List.copyOf(accuracies);
        lines = List.copyOf(lines);
    }

    /** How much stabilisation it carries: one point per corner. */
    public int capacity() {
        return corners;
    }

    /** Instructions beyond what the figure carries. */
    public int overflow() {
        // A sigil that lasts beyond an hour wants at least a hexagon to hold it so long.
        int lasting = program.duration() >= 2 && corners < 6 ? 1 : 0;
        return Math.max(0, cost - capacity()) + lasting;
    }

    /** Whether its arrows point somewhere, rather than cancelling out. */
    public boolean directed() {
        return arrows > 0 && Math.hypot(dirX, dirY) >= 0.35;
    }

    /** How much load inscribing it puts through a wand: a triangle or square is light, an octagon heavy. */
    public int tier() {
        return Math.max(1, corners - 2);
    }

    public double accuracy() {
        return accuracies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
