package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Casting, from a drawing to what happens: the server's half of magic. It reads the strokes itself,
 * judges them, puts the load through the wand, sends the spell out — or lets it go wrong — and
 * tells the caster how it went.
 */
public final class SpellCaster {
    /** Unknown runes drawn at least this well are understood afterwards (section 5). */
    static final double LEARN = 0.6;

    private SpellCaster() { }

    /**
     * Everything a cast came to, for the caster's feedback and for tests.
     *
     * @param spell the spell the strokes made, {@code null} if they made none
     * @param quality how it was judged
     * @param learned runes understood by casting them
     * @param wandBroke whether the wand broke under it
     * @param sigil the sigil it was, if the drawing was one; {@code null} for a spell
     * @param spells every spell the drawing held, the first being {@code spell}
     * @param qualities how each of them was judged
     */
    public record Result(Spell spell, CastQuality quality, List<Glyph> learned, boolean wandBroke, SigilDesign sigil,
                         List<Spell> spells, List<CastQuality> qualities) {
        public Result(Spell spell, CastQuality quality, List<Glyph> learned, boolean wandBroke, SigilDesign sigil) {
            this(spell, quality, learned, wandBroke, sigil, spell == null ? List.of() : List.of(spell), List.of(quality));
        }
    }

    /** Casts a drawing for a player holding a wand. Returns {@code null} when nothing was cast at all. */
    public static Result cast(ServerPlayer player, List<CastSpellPayload.Stroke> strokes) {
        return cast(player, strokes, 256);
    }

    /** Casts a drawing from the parchment, using its size to measure arrows. */
    public static Result cast(ServerPlayer player, List<CastSpellPayload.Stroke> strokes, float size) {
        return cast(player, strokes, size, false);
    }

    /**
     * Casts a drawing. A drawing may hold several spells — every shape with runes in it is one — and
     * all of them are cast at once, the wand carrying their load together.
     *
     * @param action whether it was drawn in action casting, where one stroke may hold two things
     */
    public static Result cast(ServerPlayer player, List<CastSpellPayload.Stroke> strokes, float size, boolean action) {
        ItemStack wand = WandItem.held(player);
        if (wand == null || strokes.isEmpty() || player.isSpectator()) return null;
        if (player.getCooldowns().isOnCooldown(wand)) {
            // Not silently: a spell that is not cast should say why.
            player.sendOverlayMessage(Component.translatable("magic.hardwrought.wand_not_ready").withStyle(ChatFormatting.GRAY));
            return null;
        }
        ServerLevel level = player.level();
        WandTier tier = ((WandItem) wand.getItem()).tier();
        List<float[]> xs = strokes.stream().map(CastSpellPayload.Stroke::xs).toList();
        List<float[]> ys = strokes.stream().map(CastSpellPayload.Stroke::ys).toList();
        // Two circles one inside the other are no spell: they are a sigil, and are laid on the ground.
        SigilDesign sigil = SketchReader.readSigil(xs, ys, Math.max(1, size), action);
        if (sigil != null) return Sigils.inscribe(player, wand, tier, sigil);

        List<SketchReader.Part> parts = SketchReader.read(xs, ys, Math.max(1, size), action);
        List<List<SketchReader.Part>> groups = new ArrayList<>();
        List<List<CastQuality.Drawn>> drawn = new ArrayList<>();
        List<Spell> spells = new ArrayList<>();
        for (List<SketchReader.Part> group : Spell.groups(parts)) {
            List<CastQuality.Drawn> judged = new ArrayList<>();
            boolean foundCore = false;
            for (SketchReader.Part part : group) {
                CastQuality.Role role;
                if (part.isRune() && part.gate() == SketchReader.Gate.ANCHOR) {
                    // An anchor holds the way; it is no element of the spell.
                    role = CastQuality.Role.AMPLIFIER;
                } else if (part.isRune()) {
                    role = foundCore ? CastQuality.Role.AMPLIFIER : CastQuality.Role.CORE;
                    foundCore = true;
                } else {
                    role = CastQuality.Role.SIGN;
                }
                int millis = part.stroke() >= 0 && part.stroke() < strokes.size() ? strokes.get(part.stroke()).millis() : 0;
                judged.add(new CastQuality.Drawn(role, part.accuracy(), part.recognized(), millis,
                        RuneKnowledge.knows(player, part.glyph())));
            }
            Spell spell = group.stream().anyMatch(SketchReader.Part::recognized) ? Spell.of(group) : null;
            if (spell == null) continue;
            groups.add(group);
            drawn.add(judged);
            spells.add(spell);
        }

        // Nothing that could be a spell: the magic fizzles.
        if (spells.isEmpty()) {
            List<CastQuality.Drawn> all = new ArrayList<>();
            for (SketchReader.Part part : parts) {
                all.add(new CastQuality.Drawn(CastQuality.Role.SIGN, part.accuracy(), part.recognized(), 0, true));
            }
            CastQuality fizzle = CastQuality.judge(all, null, tier, 1);
            player.getCooldowns().addCooldown(wand, 6 + 4 * parts.size());
            perform(level, player, null, fizzle, CastQuality.Outcome.FIZZLE, parts);
            Result result = new Result(null, fizzle, List.of(), false, null);
            tell(player, result);
            return result;
        }

        int load = spells.stream().mapToInt(Spell::tier).sum();
        List<CastQuality> qualities = new ArrayList<>();
        List<CastQuality.Outcome> outcomes = new ArrayList<>();
        for (int i = 0; i < spells.size(); i++) {
            Spell spell = spells.get(i);
            CastQuality q = CastQuality.judge(drawn.get(i), spell, tier, surroundings(level, spell.core()), load - spell.tier());
            qualities.add(q);
            outcomes.add(q.outcome());
        }

        // The load passes through the wand whatever comes of the spells (sections 18 and 19).
        boolean broke = false;
        if (outcomes.stream().anyMatch(o -> o != CastQuality.Outcome.FIZZLE)) {
            int overload = Math.max(0, load - tier.tier());
            int wear = WandTier.wear(overload);
            broke = overload >= WandTier.SHATTER || wand.getDamageValue() + wear >= wand.getMaxDamage();
            InteractionHand hand = player.getMainHandItem() == wand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (broke) {
                wand.hurtAndBreak(wand.getMaxDamage(), player, hand);
                // A wand breaking under a spell does not let it go cleanly.
                for (int i = 0; i < outcomes.size(); i++) {
                    if (outcomes.get(i) != CastQuality.Outcome.FIZZLE) {
                        outcomes.set(i, overload >= 2 ? CastQuality.Outcome.BACKFIRE : CastQuality.Outcome.ASTRAY);
                    }
                }
            } else {
                wand.hurtAndBreak(wear, player, hand);
            }
        }
        player.getCooldowns().addCooldown(wand, 6 + 4 * parts.size());

        for (int i = 0; i < spells.size(); i++) perform(level, player, spells.get(i), qualities.get(i), outcomes.get(i), groups.get(i));

        List<Glyph> learned = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++) {
            if (outcomes.get(g) == CastQuality.Outcome.FIZZLE) continue;
            for (int i = 0; i < groups.get(g).size(); i++) {
                SketchReader.Part part = groups.get(g).get(i);
                if (part.recognized() && !drawn.get(g).get(i).known() && part.accuracy() >= LEARN
                        && RuneKnowledge.learn(player, part.glyph())) {
                    learned.add(part.glyph());
                }
            }
        }
        List<CastQuality> final_ = new ArrayList<>();
        for (int i = 0; i < qualities.size(); i++) {
            CastQuality q = qualities.get(i);
            final_.add(new CastQuality(q.accuracy(), q.stability(), q.power(), outcomes.get(i), q.worst()));
        }
        Result result = new Result(spells.getFirst(), final_.getFirst(), learned, broke, null, spells, final_);
        tell(player, result);
        return result;
    }

    /** What the place does to a spell's core (section 22): water magic is weak in the Nether. */
    static double surroundings(ServerLevel level, Rune core) {
        if (core == Rune.WATER && level.dimension() == Level.NETHER) return 0.5;
        return 1;
    }

    private static void perform(ServerLevel level, ServerPlayer player, Spell spell, CastQuality quality,
                                CastQuality.Outcome outcome, List<SketchReader.Part> parts) {
        if (spell == null || outcome == CastQuality.Outcome.FIZZLE) {
            Vec3 at = SpellEffects.hand(player);
            Fx.play(level, FxEffect.SPARKLE, at, at, 0x8A8A8A, 0.4f, null);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
            return;
        }
        Rune core = spell.core();
        double power = spell.power() * quality.power();
        double distance = spell.placement() == Spell.Placement.ARROW || spell.placement() == Spell.Placement.SHIFT
                ? spell.reach() * Math.max(0.1, spell.aimReach()) : spell.reach();
        Spell.Placement placement = spell.placement();
        Vec3 direction = direction(player, spell);
        switch (outcome) {
            case WEAKENED -> {
                power *= 0.6;
                distance *= 0.7;
            }
            case WRONG_ELEMENT -> {
                // The core rune was read as what it most resembled; if it resembled nothing else, it holds.
                int worst = quality.worst();
                Rune mistaken = worst >= 0 && worst < parts.size() ? parts.get(worst).runnerUp() : null;
                if (mistaken != null && mistaken != core) core = mistaken;
                power *= 0.8;
            }
            case ASTRAY -> {
                int stroke = quality.worst() >= 0 && quality.worst() < parts.size()
                        ? parts.get(quality.worst()).stroke() : 0;
                double angle = Math.toRadians(stroke % 2 == 0 ? 40 : -40);
                direction = new Vec3(direction.x * Math.cos(angle) - direction.z * Math.sin(angle), direction.y,
                        direction.x * Math.sin(angle) + direction.z * Math.cos(angle));
                if (placement == Spell.Placement.SELF) placement = Spell.Placement.FRONT;
                else if (placement == Spell.Placement.SHIFT && core != null) placement = Spell.Placement.ARROW;
            }
            case SURGE -> {
                power *= 1.5;
                // Some of it comes back through the hand that held it.
                player.hurtServer(level, level.damageSources().magic(), (float) (1 + 2 * spell.danger()));
            }
            default -> { }
        }
        // Every other element in the spell acts beside the core, at a share of its strength.
        final Rune acting = core;
        List<Rune> blend = spell.elements().stream().map(Spell.Element::rune).filter(rune -> rune != acting).distinct().toList();
        SpellEffects.Release release = new SpellEffects.Release(level, player, core, spell.inverted(), placement,
                direction, distance, spell.burst(), spell.linger(), spell.trap(), spell.shield(), power, spell.area(),
                spell.duration(), blend);
        if (outcome == CastQuality.Outcome.BACKFIRE) {
            backfire(release, spell);
            return;
        }
        if (placement == Spell.Placement.PORTAL) {
            // The far step waits for the caster to say where.
            Portals.open(player, spell, quality, outcome, power);
            return;
        }
        SpellEffects.release(release);
        if (placement == Spell.Placement.SHIFT) Portals.arrive(player, spell.anchors(), spell.duration());
    }

    private static Vec3 direction(ServerPlayer player, Spell spell) {
        Vec3 look = player.getLookAngle();
        if (spell.placement() != Spell.Placement.ARROW && spell.placement() != Spell.Placement.SHIFT
                && spell.placement() != Spell.Placement.PORTAL) return look;
        // Up the parchment is where the crosshair points, tilt and all; right is to the caster's right.
        Vec3 forward = look.normalize();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
        Vec3 right = flat.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 direction = right.scale(spell.aimX()).add(forward.scale(-spell.aimY()));
        return direction.lengthSqr() < 1.0e-4 ? look : direction.normalize();
    }

    /**
     * The spell turns on its caster (section 15): its core acts where they stand, on them too. How
     * much that hurts is the spell's own danger: a destructive spell becomes an explosion.
     */
    private static void backfire(SpellEffects.Release r, Spell spell) {
        ServerPlayer caster = r.caster();
        Vec3 at = caster.position().add(0, 1, 0);
        if (r.core() == null) {
            // A gate or a shield of nothing gone wrong: the magic tears at its caster, no more.
            Fx.play(r.level(), FxEffect.SPARKLE, at, at, 0xB04AE0, 1.2f, null);
            caster.hurtServer(r.level(), r.level().damageSources().magic(), (float) (2 + 2 * spell.danger()));
            caster.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, 120));
            return;
        }
        SpellEffects.impactEffect(r, at, Math.max(2, r.area()));
        if (spell.danger() >= 1 && (r.core() == Rune.FIRE || r.core() == Rune.EARTH)) {
            r.level().explode(caster, SpellEffects.damage(r), null, at, (float) Math.min(5, 0.8 + 1.4 * spell.danger()),
                    r.core() == Rune.FIRE, Level.ExplosionInteraction.NONE);
        }
        // Light turned on its caster heals them; an unkind element does what it always does.
        SpellEffects.strike(r, at, Math.max(2, r.area()), true);
    }

    /** "Fire Bolt — accuracy 82 %, stability 75 %, power 90 %", and what went wrong, in the action bar. */
    private static void tell(ServerPlayer player, Result result) {
        MutableComponent text;
        if (result.spell() == null) {
            text = Component.translatable("magic.hardwrought.outcome.fizzle").withStyle(ChatFormatting.GRAY);
        } else if (result.spells().size() == 1) {
            CastQuality q = result.quality();
            text = name(player, result.spell()).copy().append(Component.literal(" — "))
                    .append(Component.translatable("magic.hardwrought.quality",
                            percent(q.accuracy()), percent(q.stability()), percent(q.power())));
            if (q.outcome().failed()) text.append(Component.literal(" — ")).append(outcome(q.outcome()));
        } else {
            // Several spells at once: each by name, and how each went.
            text = Component.empty();
            for (int i = 0; i < result.spells().size(); i++) {
                if (i > 0) text.append(Component.literal(" + "));
                text.append(name(player, result.spells().get(i)));
                CastQuality q = result.qualities().get(i);
                if (q.outcome().failed()) text.append(Component.literal(" (")).append(outcome(q.outcome())).append(Component.literal(")"));
            }
        }
        player.sendOverlayMessage(text);
        for (Glyph glyph : result.learned()) {
            player.sendSystemMessage(Component.translatable("magic.hardwrought.rune_answered",
                    Component.translatable(glyph.translationKey()).withColor(glyph.color())).withStyle(ChatFormatting.ITALIC));
        }
        if (result.wandBroke()) {
            player.sendSystemMessage(Component.translatable("magic.hardwrought.wand_broke").withStyle(ChatFormatting.RED));
        }
    }

    private static Component outcome(CastQuality.Outcome outcome) {
        return Component.translatable(outcome.translationKey())
                .withStyle(outcome == CastQuality.Outcome.WEAKENED ? ChatFormatting.YELLOW : ChatFormatting.RED);
    }

    /** A spell's name as its caster can read it: runes they do not know stay a question. */
    public static Component name(ServerPlayer player, Spell spell) {
        if (spell.gate()) {
            MutableComponent name = Component.translatable(spell.placement() == Spell.Placement.PORTAL
                    ? "magic.hardwrought.gate.long" : "magic.hardwrought.gate.short");
            for (Rune anchor : spell.anchors().stream().distinct().toList()) {
                name.append(Component.literal(" · ")).append(runeName(player, anchor));
            }
            return name;
        }
        MutableComponent name = spell.core() == null ? glyphName(player, Glyph.of(Sign.CIRCLE))
                : runeName(player, spell.core());
        if (spell.placement() != Spell.Placement.FRONT) {
            Glyph placement = Glyph.of(spell.placement() == Spell.Placement.ARROW ? Sign.ARROW : Sign.CIRCLE);
            name.append(Component.literal(" · ")).append(glyphName(player, placement));
        }
        if (!spell.amplifiers().isEmpty()) name.append(Component.literal(" +" + spell.amplifiers().size()));
        return name;
    }

    private static MutableComponent glyphName(ServerPlayer player, Glyph glyph) {
        return RuneKnowledge.knows(player, glyph)
                ? Component.translatable(glyph.translationKey()).withColor(glyph.color())
                : Component.literal("?").withStyle(ChatFormatting.OBFUSCATED);
    }

    private static MutableComponent runeName(ServerPlayer player, Rune rune) {
        return RuneKnowledge.knows(player, rune)
                ? Component.translatable(rune.translationKey()).withColor(rune.color())
                : Component.literal("?").withStyle(ChatFormatting.OBFUSCATED);
    }

    private static String percent(double value) {
        return Long.toString(Math.round(value * 100));
    }
}
