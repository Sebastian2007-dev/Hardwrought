package de.ipnats.hardwrought.magic;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Runes that work together. Most of magic composes by itself: the core element acts, every other rune
 * acts beside it at a share of its strength (see {@link SpellEffects}). A combination is where that is
 * not enough — where a few runes in a certain shape make something of their own: Darkness worked on
 * oneself is a veil, Light lingering is a ring that heals.
 *
 * <p>Each combination is one line below: the shape it takes, its core, the other runes it needs, and
 * what it does. Where several fit, the one that asks for the most runes wins. The runes a combination
 * uses are spent on it; any others in the spell still act beside it as they always do.
 */
public final class Combos {
    /** What a combination does, given the spell as it was released and the point it acts at. */
    @FunctionalInterface
    interface Action {
        void act(SpellEffects.Release spell, Vec3 at);
    }

    /**
     * One combination.
     *
     * @param id its name, for its translation and for tests
     * @param form the shape it takes
     * @param core its core rune
     * @param inverted whether that core is struck through
     * @param with the other runes it needs; more may be drawn
     */
    public record Combo(String id, SpellForm form, Rune core, boolean inverted, Set<Rune> with, Action action) {
        boolean fits(SpellForm form, Rune core, boolean inverted, Collection<Rune> others) {
            return this.form == form && this.core == core && this.inverted == inverted && others.containsAll(with);
        }

        public String translationKey() {
            return "combo.hardwrought." + id;
        }
    }

    private static final List<Combo> ALL = new ArrayList<>();

    // The book of combinations. Each names its shape, its core, what else it needs, and what it does.
    static {
        // Worked on oneself.
        add("veil", SpellForm.SELF, Rune.DARK, ComboEffects::veil);
        add("shadow_step", SpellForm.SELF, Rune.DARK, ComboEffects::shadowStep, Rune.WIND);
        add("ember_skin", SpellForm.SELF, Rune.FIRE, ComboEffects::emberSkin, Rune.EARTH);
        add("cleansing_tide", SpellForm.SELF, Rune.WATER, ComboEffects::cleansingTide, Rune.LIGHT);
        // Lingering, where cast or around the caster.
        add("healing_ring", SpellForm.FIELD, Rune.LIGHT, ComboEffects::healingRing);
        add("shroud", SpellForm.FIELD, Rune.DARK, ComboEffects::shroud);
        add("mire", SpellForm.FIELD, Rune.EARTH, ComboEffects::mire, Rune.WATER);
        add("steam", SpellForm.FIELD, Rune.FIRE, ComboEffects::steam, Rune.WATER);
        // Bursting all round.
        add("firestorm", SpellForm.BURST, Rune.FIRE, ComboEffects::firestorm, Rune.WIND);
        add("shockwave", SpellForm.BURST, Rune.EARTH, ComboEffects::shockwave, Rune.WIND);
        addInverted("flash_freeze", SpellForm.BURST, Rune.FIRE, ComboEffects::flashFreeze, Rune.WATER);
        // Sent along an arrow.
        add("storm_conduit", SpellForm.BOLT, Rune.LIGHTNING, ComboEffects::stormConduit, Rune.WATER);
        // Lying in wait.
        add("snare", SpellForm.TRAP, Rune.EARTH, ComboEffects::snare);
        add("stun_glyph", SpellForm.TRAP, Rune.LIGHTNING, ComboEffects::stunGlyph);
        add("flare", SpellForm.TRAP, Rune.LIGHT, ComboEffects::flare);
    }

    private Combos() { }

    private static void add(String id, SpellForm form, Rune core, Action action, Rune... with) {
        ALL.add(new Combo(id, form, core, false, with.length == 0 ? Set.of() : EnumSet.of(with[0], with), action));
    }

    private static void addInverted(String id, SpellForm form, Rune core, Action action, Rune... with) {
        ALL.add(new Combo(id, form, core, true, with.length == 0 ? Set.of() : EnumSet.of(with[0], with), action));
    }

    /** Every combination there is. */
    public static List<Combo> all() {
        return List.copyOf(ALL);
    }

    /** The combination a spell of this make takes, if any: of those that fit, the one that asks for the most runes. */
    public static Combo find(SpellForm form, Rune core, boolean inverted, Collection<Rune> others) {
        if (form == null || core == null) return null;
        Combo best = null;
        for (Combo combo : ALL) {
            if (combo.fits(form, core, inverted, others) && (best == null || combo.with().size() > best.with().size())) {
                best = combo;
            }
        }
        return best;
    }

    /** The combination a drawn spell would take, as it was drawn; a failed cast may come out otherwise. */
    public static Combo find(Spell spell) {
        if (spell == null || spell.core() == null) return null;
        List<Rune> others = spell.elements().stream().map(Spell.Element::rune).filter(rune -> rune != spell.core()).toList();
        return find(SpellForm.of(spell), spell.core(), spell.inverted(), others);
    }

    /**
     * Lets the combination the spell makes in this form act, if it makes one; the runes the combination
     * does not use then act beside it as they would have anyway.
     *
     * @param plain what this form does with a spell no combination takes, for those other runes
     * @return whether a combination acted
     */
    static boolean apply(SpellForm form, SpellEffects.Release spell, Vec3 at, BiConsumer<SpellEffects.Release, Vec3> plain) {
        Combo combo = find(form, spell.core(), spell.inverted(), spell.blend());
        if (combo == null) return false;
        combo.action().act(spell, at);
        for (Rune rune : spell.blend()) {
            if (!combo.with().contains(rune)) plain.accept(spell.as(rune), at);
        }
        return true;
    }
}
