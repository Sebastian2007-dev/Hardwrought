package de.ipnats.hardwrought.magic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A spell as its drawing describes it, before anyone has tried to cast it. How well it is then drawn
 * is {@link CastQuality}'s concern.
 *
 * <p>The grammar (Milestone M1):
 * <ul>
 *     <li><b>Element runes</b> say what the spell is made of. The first is its <b>core</b>; every
 *     further one <b>amplifies</b> it — the core's own rune again makes it stronger, any other
 *     changes its reach, area, duration or steadiness. A rune struck through is turned around.</li>
 *     <li><b>Auxiliary runes</b> say how and where it acts ({@link Sign}): without one, just in
 *     front of the caster; with an arrow, where the arrow ends; inside a circle, on the caster; inside
 *     a circle and with an arrow, the caster is moved to where the arrow ends. A triangle makes it
 *     burst, a spiral makes it linger, a square lays it as a trap. A circle with nothing in it is a
 *     shield.</li>
 *     <li><b>Gates</b> ({@link SketchReader.Gate}): an arrow out of a circle is a short step; from a
 *     circle into a second circle, a far one, to coordinates the caster names. Runes across the
 *     shaft are <b>anchors</b>: they steady the way and say how the caster arrives.</li>
 * </ul>
 * Light and Dark in the same spell cancel each other, pair by pair, unless two Earth runes hold
 * each pair apart; then the pair is twilight, the strongest and most dangerous magic there is. Earth
 * grounds Lightning, pair by pair, with whatever Earth is not holding twilight together.
 *
 * @param elements the element runes, in the order drawn
 * @param core the first of them, {@code null} for a shield of nothing but a circle
 * @param inverted whether the core is struck through
 * @param placement where it acts
 * @param aimX for an arrow: its direction on the parchment, rightward
 * @param aimY for an arrow: its direction on the parchment, downward; up is ahead
 * @param aimReach for an arrow: its length as a share of the parchment
 * @param power how strong it would be cast perfectly; 1 is a single plain rune
 * @param reach the furthest an arrow may send it, in blocks
 * @param area the radius it acts over where it lands
 * @param duration how much longer than plain its lasting effects last; 1 is plain
 * @param steadiness how much its own structure adds to or takes from stability
 * @param tier how much load it puts through a wand; the wand must carry at least this much
 * @param danger how badly it goes wrong when it goes wrong: 1 is a fireball's worth
 * @param cancelled whether Light and Dark have cancelled each other out of it
 * @param twilight how many Light and Dark pairs are held apart by Earth
 * @param anchors for a gate: the runes across its arrow's shaft
 */
public record Spell(List<Element> elements, Rune core, boolean inverted, Placement placement, float aimX, float aimY,
                    float aimReach, boolean burst, boolean linger, boolean trap, boolean shield, double power,
                    double reach, double area, double duration, double steadiness, int tier, double danger,
                    boolean cancelled, int twilight, List<Rune> anchors) {
    /** The most strokes one drawing may hold; the specification sets no limit, the network does. */
    public static final int MAX_STROKES = 48;
    /** The most runes and auxiliary runes one drawing may hold: an arrow of two strokes counts once. */
    public static final int MAX_SYMBOLS = 24;

    public record Element(Rune rune, boolean inverted) { }

    /** Where a spell acts. */
    public enum Placement {
        /** No arrow, no circle: just in front of the caster. */
        FRONT(6),
        /** Where the arrow ends. */
        ARROW(24),
        /** On the caster: the runes are inside a circle. */
        SELF(0),
        /** The caster, inside a circle, is moved to where the arrow ends: a step through nothing. */
        SHIFT(10),
        /** Circle, arrow, circle: the caster goes far, to coordinates they name once the gate is open. */
        PORTAL(160);

        private final double reach;

        Placement(double reach) {
            this.reach = reach;
        }

        public double reach() {
            return reach;
        }

        public String translationKey() {
            return "placement.hardwrought." + name().toLowerCase(Locale.ROOT);
        }
    }

    public Spell {
        elements = List.copyOf(elements);
        anchors = anchors == null ? List.of() : List.copyOf(anchors);
    }

    /** Whether it moves the caster through a gate, near or far. */
    public boolean gate() {
        return placement == Placement.PORTAL || (placement == Placement.SHIFT && core == null);
    }

    public List<Element> amplifiers() {
        return elements.size() <= 1 ? List.of() : elements.subList(1, elements.size());
    }

    /**
     * Reads a drawing as all the spells it holds: one for every shape with runes in it, and one for
     * the runes outside any shape (see {@link SketchReader.Part#group()}).
     */
    public static List<List<SketchReader.Part>> groups(List<SketchReader.Part> parts) {
        java.util.TreeMap<Integer, List<SketchReader.Part>> groups = new java.util.TreeMap<>();
        for (SketchReader.Part part : parts) groups.computeIfAbsent(part.group(), k -> new ArrayList<>()).add(part);
        return new ArrayList<>(groups.values());
    }

    /** Reads a drawing as a spell; {@code null} when it holds nothing that could be one. */
    public static Spell of(List<SketchReader.Part> parts) {
        if (parts == null || parts.isEmpty()) return null;
        List<Element> elements = new ArrayList<>();
        List<Rune> anchors = new ArrayList<>();
        SketchReader.Part arrow = null, gate = null;
        boolean self = false, shield = false, burst = false, linger = false, trap = false;
        for (SketchReader.Part part : parts) {
            if (part.isRune()) {
                if (part.gate() == SketchReader.Gate.ANCHOR) anchors.add(part.rune());
                else elements.add(new Element(part.rune(), part.inverted()));
                continue;
            }
            switch (part.sign()) {
                case ARROW -> {
                    if (part.gate() == SketchReader.Gate.SHORT || part.gate() == SketchReader.Gate.LONG) {
                        if (gate == null || part.gate() == SketchReader.Gate.LONG) gate = part;
                    } else if (arrow == null) {
                        arrow = part;
                    }
                }
                case CIRCLE -> {
                    if (part.gate() != SketchReader.Gate.NONE) { }
                    else if (part.enclosed()) self = true;
                    else shield = true;
                }
                case TRIANGLE -> burst = true;
                case SPIRAL -> linger = true;
                case SQUARE -> trap = true;
                case STRIKE -> { }
            }
        }
        if (gate != null) {
            // A gate moves the caster; whatever else is drawn with it goes along.
            arrow = gate;
            shield = false;
            self = false;
        }
        if (elements.isEmpty() && !shield && gate == null) return null;
        Rune core = elements.isEmpty() ? null : elements.getFirst().rune();
        boolean inverted = !elements.isEmpty() && elements.getFirst().inverted();
        Placement placement = gate != null ? (gate.gate() == SketchReader.Gate.LONG ? Placement.PORTAL : Placement.SHIFT)
                : self && arrow != null ? Placement.SHIFT : self ? Placement.SELF
                : arrow != null ? Placement.ARROW : Placement.FRONT;

        double power = 1, reach = placement.reach(), area = 2, duration = 1, steadiness = 0;
        for (Element element : elements.size() > 1 ? elements.subList(1, elements.size()) : List.<Element>of()) {
            Rune rune = element.rune();
            if (rune == core) {
                power *= 1.6;
                continue;
            }
            switch (rune) {
                case WIND -> reach *= 1.5;
                case EARTH -> {
                    steadiness += 0.08;
                    duration *= 1.4;
                }
                case WATER -> area += 1;
                case FIRE -> {
                    power *= 1.3;
                    steadiness -= 0.06;
                }
                case LIGHT -> steadiness += 0.08;
                case DARK -> duration *= 1.5;
                case LIGHTNING -> {
                    power *= 1.15;
                    area += 0.5;
                }
            }
        }
        // Anchors steady the way: Earth most of all; Wind carries further.
        for (Rune anchor : anchors) {
            switch (anchor) {
                case EARTH -> steadiness += 0.15;
                case WIND -> {
                    reach *= 1.5;
                    steadiness += 0.03;
                }
                default -> steadiness += 0.05;
            }
        }
        // A far step with nothing to hold it is a leap into the unknown.
        if (placement == Placement.PORTAL && anchors.isEmpty()) steadiness -= 0.12;
        int light = count(elements, Rune.LIGHT), dark = count(elements, Rune.DARK), earth = count(elements, Rune.EARTH);
        int pairs = Math.min(light, dark);
        boolean held = pairs > 0 && earth >= 2 * pairs;
        boolean cancelled = pairs > 0 && !held;
        int twilight = held ? pairs : 0;
        if (cancelled) power *= Math.pow(0.1, pairs);
        if (held) power *= Math.pow(1.6, pairs);
        // Earth grounds Lightning: each pair of them runs off into nothing.
        int grounded = Math.min(count(elements, Rune.LIGHTNING), Math.max(0, earth - 2 * twilight));
        if (grounded > 0) {
            power *= Math.pow(0.1, grounded);
            cancelled = true;
        }
        int tier = parts.size() + 2 * twilight;
        double shape = Math.max(placementDanger(placement), Math.max(burst ? 1.2 : 0, Math.max(linger ? 1.1 : 0, trap ? 1.0 : 0)));
        double danger = (core == null ? 0.1 : elementDanger(core)) * shape * power * (held ? 2 : 1);
        return new Spell(elements, core, inverted, placement, arrow == null ? 0 : arrow.dx(), arrow == null ? -1 : arrow.dy(),
                arrow == null ? 0 : arrow.reach(), burst, linger, trap, shield, power, reach, area, duration, steadiness,
                tier, danger, cancelled, twilight, anchors);
    }

    private static int count(List<Element> elements, Rune rune) {
        int n = 0;
        for (Element e : elements) if (e.rune() == rune) n++;
        return n;
    }

    /** How destructive an element is by nature; section 16 orders light spells far below fireballs. */
    static double elementDanger(Rune rune) {
        return switch (rune) {
            case FIRE -> 1.0;
            case EARTH -> 0.8;
            case DARK -> 0.7;
            case LIGHTNING -> 0.9;
            case WIND -> 0.4;
            case WATER -> 0.3;
            case LIGHT -> 0.15;
        };
    }

    static double placementDanger(Placement placement) {
        return switch (placement) {
            case ARROW -> 0.9;
            case FRONT, SHIFT, PORTAL -> 0.6;
            case SELF -> 0.4;
        };
    }
}
