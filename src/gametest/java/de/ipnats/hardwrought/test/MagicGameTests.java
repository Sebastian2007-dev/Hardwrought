package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.magic.CastQuality;
import de.ipnats.hardwrought.magic.CastSpellPayload;
import de.ipnats.hardwrought.magic.Glyph;
import de.ipnats.hardwrought.magic.Magic;
import de.ipnats.hardwrought.magic.MagicShields;
import de.ipnats.hardwrought.magic.Rune;
import de.ipnats.hardwrought.magic.RuneKnowledge;
import de.ipnats.hardwrought.magic.RuneRecognizer;
import de.ipnats.hardwrought.magic.RuneRuins;
import de.ipnats.hardwrought.magic.RuneStoneBlock;
import de.ipnats.hardwrought.magic.Sign;
import de.ipnats.hardwrought.magic.SketchReader;
import de.ipnats.hardwrought.magic.Spell;
import de.ipnats.hardwrought.magic.SpellCaster;
import de.ipnats.hardwrought.magic.WandTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Magic, Milestone M1: reading runes and auxiliary runes, the grammar, judging a cast, and casting
 * in the world.
 */
public final class MagicGameTests {
    // --- Drawing ---------------------------------------------------------------------------------

    /** A rune drawn as its sheet shows it, moved to (x, y), scaled, turned by an angle and shaken a little. */
    static CastSpellPayload.Stroke rune(Rune rune, float x, float y, float scale, double degrees, double shake, boolean reversed) {
        List<float[]> shape = rune.shape();
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i + 1 < shape.size(); i++) {
            for (int k = 0; k < 8; k++) {
                float t = k / 8f;
                points.add(new float[] {shape.get(i)[0] + (shape.get(i + 1)[0] - shape.get(i)[0]) * t,
                        shape.get(i)[1] + (shape.get(i + 1)[1] - shape.get(i)[1]) * t});
            }
        }
        points.add(shape.getLast());
        if (reversed) java.util.Collections.reverse(points);
        double a = Math.toRadians(degrees);
        List<float[]> placed = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            double px = (points.get(i)[0] - 600) * scale, py = (points.get(i)[1] - 600) * scale;
            // A tremor that is the same every run: a test must not depend on luck.
            double wobble = shake * Math.sin(i * 1.7);
            placed.add(new float[] {(float) (x + px * Math.cos(a) - py * Math.sin(a) + wobble),
                    (float) (y + px * Math.sin(a) + py * Math.cos(a) - wobble)});
        }
        return stroke(placed);
    }

    static CastSpellPayload.Stroke rune(Rune rune, float x, float y) {
        return rune(rune, x, y, 0.12f, 0, 0.5, false);
    }

    static CastSpellPayload.Stroke circle(float cx, float cy, float r) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= 48; i++) {
            double a = -Math.PI / 2 + i / 48.0 * Math.PI * 2;
            points.add(new float[] {cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a)});
        }
        return stroke(points);
    }

    static CastSpellPayload.Stroke path(float... xy) {
        List<float[]> points = new ArrayList<>();
        for (int c = 0; c + 3 < xy.length; c += 2) {
            for (int i = 0; i < 12; i++) {
                float t = i / 12f;
                points.add(new float[] {xy[c] + (xy[c + 2] - xy[c]) * t, xy[c + 1] + (xy[c + 3] - xy[c + 1]) * t});
            }
        }
        points.add(new float[] {xy[xy.length - 2], xy[xy.length - 1]});
        return stroke(points);
    }

    /** An arrow in one stroke, from (x0, y0) to (x1, y1), with its head drawn back from the tip. */
    static CastSpellPayload.Stroke arrow(float x0, float y0, float x1, float y1) {
        double dx = x1 - x0, dy = y1 - y0, l = Math.hypot(dx, dy);
        double ux = dx / l, uy = dy / l;
        float bx = (float) (x1 - ux * 30 - uy * 20), by = (float) (y1 - uy * 30 + ux * 20);
        float cx = (float) (x1 - ux * 30 + uy * 20), cy = (float) (y1 - uy * 30 - ux * 20);
        return path(x0, y0, x1, y1, bx, by, x1, y1, cx, cy);
    }

    static CastSpellPayload.Stroke stroke(List<float[]> points) {
        float[] xs = new float[points.size()], ys = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i)[0];
            ys[i] = points.get(i)[1];
        }
        return new CastSpellPayload.Stroke(xs, ys, 600);
    }

    static Spell read(CastSpellPayload.Stroke... strokes) {
        return Spell.of(parts(false, strokes));
    }

    /** As drawn in action casting, where one stroke may hold two things. */
    static Spell readAction(CastSpellPayload.Stroke... strokes) {
        return Spell.of(parts(true, strokes));
    }

    static List<SketchReader.Part> parts(boolean action, CastSpellPayload.Stroke... strokes) {
        List<float[]> xs = new ArrayList<>(), ys = new ArrayList<>();
        for (CastSpellPayload.Stroke stroke : strokes) {
            xs.add(stroke.xs());
            ys.add(stroke.ys());
        }
        return SketchReader.read(xs, ys, 300, action);
    }

    /** A player in the level, with a connection, since casting talks back to its caster. */
    @SuppressWarnings("removal")
    static ServerPlayer mage(GameTestHelper helper, Glyph... known) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Magic.WOODEN_WAND));
        RuneKnowledge.forget(player);
        for (Glyph glyph : known) RuneKnowledge.learn(player, glyph);
        return player;
    }

    static void floor(GameTestHelper helper, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
    }

    // --- Reading ---------------------------------------------------------------------------------

    @GameTest
    public void everyRuneReadsAsItself(GameTestHelper helper) {
        for (Rune rune : Rune.values()) {
            for (boolean reversed : new boolean[] {false, true}) {
                var stroke = rune(rune, 150, 150, 0.25f, 0, 0, reversed);
                var reading = RuneRecognizer.read(stroke.xs(), stroke.ys());
                helper.assertTrue(reading.rune() == rune && reading.accuracy() > 0.93,
                        rune + " drawn " + (reversed ? "backwards" : "forwards") + " must read as itself, read " + reading);
            }
            var shaky = rune(rune, 150, 150, 0.25f, 8, 4, false);
            helper.assertTrue(RuneRecognizer.read(shaky.xs(), shaky.ys()).rune() == rune,
                    "A slightly tilted, shaky " + rune + " is still " + rune);
        }
        // Orientation is part of a rune: Wind turned on its side is no longer Wind.
        var turned = rune(Rune.WIND, 150, 150, 0.25f, 90, 0, false);
        helper.assertTrue(RuneRecognizer.read(turned.xs(), turned.ys()).rune() != Rune.WIND, "Turned, Wind is not Wind");
        helper.assertTrue(!RuneRecognizer.read(new float[] {0, 3}, new float[] {0, 2}).recognized(), "A twitch is no rune");
        helper.succeed();
    }

    @GameTest
    public void auxiliaryRunesSayWhereAndHow(GameTestHelper helper) {
        Spell front = read(rune(Rune.FIRE, 150, 150));
        helper.assertTrue(front.placement() == Spell.Placement.FRONT && front.tier() == 1, "One rune: just in front");

        Spell ahead = read(rune(Rune.FIRE, 80, 150), arrow(220, 260, 220, 60));
        helper.assertTrue(ahead.placement() == Spell.Placement.ARROW && ahead.aimY() < -0.9 && ahead.aimReach() > 0.5,
                "Fire with an arrow up the page: ahead, a good way off");
        Spell right = read(rune(Rune.FIRE, 80, 150), path(60, 250, 160, 250), path(130, 225, 160, 250, 130, 275));
        helper.assertTrue(right.placement() == Spell.Placement.ARROW && right.aimX() > 0.9, "A shaft and a head: to the right");

        Spell self = read(circle(150, 150, 70), rune(Rune.FIRE, 150, 150, 0.4f, 0, 0, false));
        helper.assertTrue(self.placement() == Spell.Placement.SELF && !self.shield(),
                "A rune running over the edge of its circle acts on the caster");
        Spell fireShield = read(circle(150, 150, 110), rune(Rune.FIRE, 150, 150, 0.1f, 0, 0, false));
        helper.assertTrue(fireShield.shield() && fireShield.core() == Rune.FIRE, "A rune wholly inside gives the shield its element");
        Spell plain = read(circle(150, 150, 100));
        helper.assertTrue(plain.shield() && plain.core() == null, "An empty circle is a plain shield");
        Spell shift = read(circle(150, 150, 70), rune(Rune.WIND, 150, 150, 0.4f, 0, 0, false), arrow(270, 260, 270, 60));
        helper.assertTrue(shift.placement() == Spell.Placement.SHIFT, "A circle and an arrow move the caster");

        helper.assertTrue(read(rune(Rune.FIRE, 80, 150), path(200, 60, 270, 190, 130, 190, 200, 60)).burst(), "A triangle bursts");
        helper.assertTrue(read(rune(Rune.LIGHT, 80, 150), path(150, 80, 260, 80, 260, 190, 150, 190, 150, 80)).trap(),
                "A square lays a trap");
        List<float[]> spiral = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            double t = i / 60.0, a = t * Math.PI * 4.5, r = 6 + 60 * t;
            spiral.add(new float[] {200 + (float) (r * Math.cos(a)), 150 + (float) (r * Math.sin(a))});
        }
        helper.assertTrue(read(rune(Rune.LIGHT, 60, 150), stroke(spiral)).linger(), "A spiral lingers");
        Spell struck = read(rune(Rune.LIGHT, 150, 150), path(90, 200, 210, 100));
        helper.assertTrue(struck.inverted() && struck.core() == Rune.LIGHT, "A line across a rune turns it around");

        // In action casting the pen never lifts: a stem that turns into a rune, a loop that runs on into one.
        List<float[]> flag = new ArrayList<>();
        for (int i = 0; i <= 12; i++) flag.add(new float[] {95 + i, 280 - i * 13});
        for (int i = 0; i <= 24; i++) flag.add(new float[] {110 + i * 13, 124 - 30 * (float) Math.sin(i / 24.0 * Math.PI * 2)});
        Spell stem = readAction(stroke(flag));
        helper.assertTrue(stem.placement() == Spell.Placement.ARROW && stem.core() == Rune.WATER, "A stem into Water: Water ahead");
        helper.assertTrue(read(stroke(flag)).placement() == Spell.Placement.FRONT, "but on the parchment a stroke is one thing");
        List<float[]> loop = new ArrayList<>();
        for (int i = 0; i <= 48; i++) {
            double a = -Math.PI / 2 + i / 48.0 * Math.PI * 2;
            loop.add(new float[] {150 + 110 * (float) Math.cos(a), 150 + 110 * (float) Math.sin(a)});
        }
        for (int i = 0; i <= 24; i++) loop.add(new float[] {90 + i * 5, 150 - 25 * (float) Math.sin(i / 24.0 * Math.PI * 2)});
        Spell looped = readAction(stroke(loop));
        helper.assertTrue(looped.shield() && looped.core() == Rune.WATER, "A loop running on into Water: a water shield");

        Spell cancelled = read(rune(Rune.LIGHT, 60, 150), rune(Rune.FIRE, 150, 150), rune(Rune.DARK, 240, 150));
        helper.assertTrue(cancelled.cancelled() && cancelled.power() < 0.2, "Light and Dark cancel each other");
        Spell twilight = read(rune(Rune.LIGHT, 40, 150), rune(Rune.EARTH, 110, 150), rune(Rune.EARTH, 180, 150),
                rune(Rune.DARK, 250, 150));
        helper.assertTrue(!twilight.cancelled() && twilight.twilight() == 1 && twilight.tier() == 6,
                "Held apart by two Earth runes, Light and Dark are twilight, and heavy");
        helper.succeed();
    }

    @GameTest
    public void everyShapeWithRunesIsASpellOfItsOwn(GameTestHelper helper) {
        // Light in a circle, Dark in a square, Fire in a triangle: three spells, all cast.
        var parts = parts(false, circle(60, 60, 45), rune(Rune.LIGHT, 60, 60, 0.04f, 0, 0, false),
                path(160, 20, 250, 20, 250, 100, 160, 100, 160, 20), rune(Rune.DARK, 205, 60, 0.05f, 0, 0, false),
                path(150, 160, 230, 290, 70, 290, 150, 160), rune(Rune.FIRE, 150, 250, 0.06f, 0, 0, false));
        List<List<SketchReader.Part>> groups = Spell.groups(parts);
        helper.assertTrue(groups.size() == 3, "Three shapes with runes in them are three spells, not " + groups.size());
        List<Spell> spells = groups.stream().map(Spell::of).toList();
        helper.assertTrue(spells.stream().anyMatch(s -> s.core() == Rune.LIGHT && s.shield()), "a light shield");
        helper.assertTrue(spells.stream().anyMatch(s -> s.core() == Rune.DARK && s.trap() && !s.cancelled()),
                "a trap of darkness, not cancelled by the light beside it");
        helper.assertTrue(spells.stream().anyMatch(s -> s.core() == Rune.FIRE && s.burst()), "and a burst of fire");
        ServerPlayer player = mage(helper, Glyph.values());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Magic.GOLDEN_WAND));
        var result = SpellCaster.cast(player, List.of(circle(60, 60, 45), rune(Rune.LIGHT, 60, 60, 0.04f, 0, 0, false),
                path(160, 20, 250, 20, 250, 100, 160, 100, 160, 20), rune(Rune.DARK, 205, 60, 0.05f, 0, 0, false),
                path(150, 160, 230, 290, 70, 290, 150, 160), rune(Rune.FIRE, 150, 250, 0.06f, 0, 0, false)), 300);
        helper.assertTrue(result != null && result.spells().size() == 3, "and casting the drawing casts all three");
        helper.assertTrue(MagicShields.shielded(player), "the shield among them");
        helper.succeed();
    }

    @GameTest
    public void castsAreJudgedNotGuessed(GameTestHelper helper) {
        Spell bolt = read(rune(Rune.FIRE, 80, 150), arrow(220, 260, 220, 60));
        var clean = List.of(drawn(CastQuality.Role.CORE, 0.97, true), drawn(CastQuality.Role.SIGN, 0.95, true));
        CastQuality good = CastQuality.judge(clean, bolt, WandTier.WOOD, 1);
        helper.assertTrue(good.outcome() == CastQuality.Outcome.SUCCESS && good.power() > 1.0,
                "Fast and clean: works, and a little stronger than plain; was " + good);
        var poorCore = List.of(drawn(CastQuality.Role.CORE, 0.3, true), drawn(CastQuality.Role.SIGN, 0.6, true));
        helper.assertTrue(CastQuality.judge(poorCore, bolt, WandTier.WOOD, 1).outcome() == CastQuality.Outcome.WRONG_ELEMENT,
                "A poor core rune is read as what it resembled");
        var poorSign = List.of(drawn(CastQuality.Role.CORE, 0.6, true), drawn(CastQuality.Role.SIGN, 0.3, true));
        helper.assertTrue(CastQuality.judge(poorSign, bolt, WandTier.WOOD, 1).outcome() == CastQuality.Outcome.ASTRAY,
                "A poor auxiliary rune sends the spell astray");
        var scrawl = List.of(drawn(CastQuality.Role.CORE, 0.2, true), drawn(CastQuality.Role.SIGN, 0.25, true));
        helper.assertTrue(CastQuality.judge(scrawl, bolt, WandTier.WOOD, 1).outcome() == CastQuality.Outcome.BACKFIRE,
                "A scrawl turns on its caster");
        var unknown = List.of(drawn(CastQuality.Role.CORE, 0.97, false), drawn(CastQuality.Role.SIGN, 0.95, false));
        helper.assertTrue(CastQuality.judge(unknown, bolt, WandTier.WOOD, 1).stability() < good.stability() - 0.2,
                "Runes the caster does not know hold badly");
        Spell heavy = read(rune(Rune.FIRE, 40, 150), rune(Rune.FIRE, 110, 150), rune(Rune.FIRE, 180, 150), arrow(260, 260, 260, 60));
        var four = List.of(drawn(CastQuality.Role.CORE, 0.95, true), drawn(CastQuality.Role.AMPLIFIER, 0.95, true),
                drawn(CastQuality.Role.AMPLIFIER, 0.95, true), drawn(CastQuality.Role.SIGN, 0.95, true));
        helper.assertTrue(CastQuality.judge(four, heavy, WandTier.WOOD, 1).stability()
                        < CastQuality.judge(four, heavy, WandTier.IRON, 1).stability() - 0.25,
                "A wooden wand overloaded by two tiers shakes the spell; an iron one carries it");
        helper.succeed();
    }

    private static CastQuality.Drawn drawn(CastQuality.Role role, double accuracy, boolean known) {
        return new CastQuality.Drawn(role, accuracy, true, 500, known);
    }

    // --- Casting ---------------------------------------------------------------------------------

    @GameTest
    public void lightOnTheCasterHealsAndExperimentTeaches(GameTestHelper helper) {
        ServerPlayer known = mage(helper, Glyph.LIGHT, Glyph.CIRCLE);
        known.setHealth(8);
        var result = SpellCaster.cast(known, List.of(circle(150, 150, 70), rune(Rune.LIGHT, 150, 150, 0.25f, 0, 0.5, false)), 300);
        helper.assertTrue(result != null && result.quality().outcome() == CastQuality.Outcome.SUCCESS
                        && result.spell().placement() == Spell.Placement.SELF,
                "Light across a circle: a clean self-heal; was " + (result == null ? null : result.quality()));
        helper.assertTrue(known.getHealth() > 8, "and it heals");

        // Section 5: what nobody taught can be found by drawing it, at a risk.
        ServerPlayer novice = mage(helper);
        novice.setHealth(8);
        var experiment = SpellCaster.cast(novice, List.of(circle(150, 150, 70), rune(Rune.LIGHT, 150, 150, 0.25f, 0, 0.5, false)), 300);
        helper.assertTrue(experiment != null && experiment.quality().stability() < result.quality().stability(),
                "Unknown runes hold worse");
        helper.assertTrue(RuneKnowledge.knows(novice, Rune.LIGHT) && RuneKnowledge.knows(novice, Sign.CIRCLE),
                "but drawn well, the magic answers, and both the rune and the circle are understood");
        helper.succeed();
    }

    @GameTest
    public void shieldStopsBlowsBothWays(GameTestHelper helper) {
        ServerPlayer player = mage(helper, Glyph.CIRCLE);
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        SpellCaster.cast(player, List.of(circle(150, 150, 100)), 300);
        helper.assertTrue(MagicShields.shielded(player), "An empty circle: a shield around the caster");
        Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(6, 1, 1));
        ServerLevel level = helper.getLevel();
        float before = player.getHealth();
        player.hurtServer(level, level.damageSources().mobAttack(husk), 4);
        helper.assertTrue(player.getHealth() == before, "A blow from outside stops at the shield");
        float huskBefore = husk.getHealth();
        husk.hurtServer(level, level.damageSources().playerAttack(player), 4);
        helper.assertTrue(husk.getHealth() == huskBefore, "and the caster's own blow stops at it from inside");
        helper.succeed();
    }

    @GameTest
    public void aSpiralHoldsAShieldWhereItIsRaised(GameTestHelper helper) {
        List<float[]> spiral = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            double t = i / 60.0, a = t * Math.PI * 4.5, r = 6 + 60 * t;
            spiral.add(new float[] {130 + (float) (r * Math.cos(a)), 180 + (float) (r * Math.sin(a))});
        }
        // As drawn on the parchment: Light in a circle, a spiral beside it.
        CastSpellPayload.Stroke[] drawing = {circle(230, 70, 40), rune(Rune.LIGHT, 230, 70, 0.06f, 0, 0, false), stroke(spiral)};
        Spell spell = read(drawing);
        helper.assertTrue(spell != null && spell.shield() && spell.linger() && spell.core() == Rune.LIGHT,
                "A light shield with a spiral is a standing light shield");

        ServerPlayer player = mage(helper, Glyph.LIGHT, Glyph.CIRCLE, Glyph.SPIRAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Magic.GOLDEN_WAND));
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
        floor(helper, from.offset(-1, -1, -1), from.offset(8, -1, 1));
        player.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        SpellCaster.cast(player, List.of(drawing), 300);
        Vec3 dome = MagicShields.fixedShield(player);
        helper.assertTrue(dome != null && !MagicShields.shielded(player), "It stands on its own, not around the caster");
        player.teleportTo(from.getX() + 6.5, from.getY(), from.getZ() + 0.5);
        helper.assertTrue(dome.equals(MagicShields.fixedShield(player)), "and stays where it was raised when the caster walks off");
        helper.succeed();
    }

    @GameTest
    public void overloadWearsAndBreaksWands(GameTestHelper helper) {
        ServerPlayer player = mage(helper, Glyph.values());
        ItemStack wand = player.getMainHandItem();
        SpellCaster.cast(player, List.of(circle(150, 150, 70), rune(Rune.LIGHT, 130, 150, 0.25f, 0, 0, false),
                rune(Rune.LIGHT, 170, 150, 0.25f, 0, 0, false)), 300);
        helper.assertTrue(wand.getDamageValue() == WandTier.wear(1),
                "A tier-three spell through a tier-two wand wears it hard: " + wand.getDamageValue());
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(wand));
        List<CastSpellPayload.Stroke> heavy = new ArrayList<>();
        heavy.add(circle(150, 150, 70));
        for (int i = 0; i < 5; i++) heavy.add(rune(Rune.LIGHT, 100 + i * 25, 150, 0.25f, 0, 0, false));
        var result = SpellCaster.cast(player, heavy, 300);
        helper.assertTrue(result != null && result.wandBroke() && player.getMainHandItem().isEmpty(),
                "Four tiers over, a wooden wand breaks");
        helper.assertTrue(result.quality().outcome() == CastQuality.Outcome.BACKFIRE, "and the spell turns on its caster");
        helper.succeed();
    }

    @GameTest(maxTicks = 60)
    public void arrowSendsFireAhead(GameTestHelper helper) {
        ServerPlayer player = mage(helper, Glyph.FIRE, Glyph.ARROW);
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
        floor(helper, from.offset(-1, -1, -1), from.offset(9, -1, 1));
        player.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(7, 1, 1));
        husk.setNoAi(true);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getEyePosition());
        float full = husk.getHealth();
        var result = SpellCaster.cast(player, List.of(rune(Rune.FIRE, 80, 150), arrow(220, 280, 220, 60)), 300);
        helper.assertTrue(result != null && result.spell().placement() == Spell.Placement.ARROW, "Fire with an arrow flies ahead");
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < full || husk.isOnFire(), "and reaches the husk and burns it"));
    }

    @GameTest
    public void circleAndArrowMoveTheCaster(GameTestHelper helper) {
        ServerPlayer player = mage(helper, Glyph.WIND, Glyph.CIRCLE, Glyph.ARROW);
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
        floor(helper, from.offset(-1, -1, -1), from.offset(10, -1, 1));
        player.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(from.east(8)).add(0, 0.6, 0));
        Vec3 before = player.position();
        SpellCaster.cast(player, List.of(circle(150, 150, 70), rune(Rune.WIND, 150, 150, 0.4f, 0, 0, false),
                arrow(270, 280, 270, 40)), 300);
        helper.assertTrue(player.position().distanceTo(before) > 3 && player.getX() > before.x,
                "Wind across a circle, with an arrow ahead: a step forward through nothing");
        helper.succeed();
    }

    /** A shaft up the page from (150, 210) to (150, 90), wavering left and right as a quick hand draws it, then its head. */
    static CastSpellPayload.Stroke wavyArrow(float amplitude) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= 48; i++) {
            double t = i / 48.0;
            points.add(new float[] {150 + (float) (amplitude * Math.sin(t * Math.PI * 6)), (float) (210 - 120 * t)});
        }
        float[][] head = {{132, 116}, {150, 90}, {168, 116}};
        for (float[] corner : head) {
            float[] last = points.getLast();
            for (int i = 1; i <= 8; i++) {
                float t = i / 8f;
                points.add(new float[] {last[0] + (corner[0] - last[0]) * t, last[1] + (corner[1] - last[1]) * t});
            }
        }
        return stroke(points);
    }

    @GameTest
    public void gatesAreReadFromCirclesAndArrows(GameTestHelper helper) {
        // A circle and an arrow out of it, nothing else: a short step.
        Spell near = read(circle(150, 250, 35), arrow(150, 215, 150, 80));
        helper.assertTrue(near != null && near.placement() == Spell.Placement.SHIFT && near.gate() && near.core() == null,
                "A circle with an arrow out of it is a short gate");
        // Circle, arrow, circle: a far one.
        Spell far = read(circle(150, 250, 35), arrow(150, 210, 150, 90), circle(150, 50, 35));
        helper.assertTrue(far != null && far.placement() == Spell.Placement.PORTAL && !far.shield() && far.anchors().isEmpty(),
                "Circle, arrow, circle is a far gate, and its circles are no shields");
        // A rune across the shaft holds it: Earth best of all.
        Spell held = read(circle(150, 250, 35), arrow(150, 210, 150, 90), circle(150, 50, 35),
                rune(Rune.EARTH, 150, 150, 0.08f, 0, 0, false));
        helper.assertTrue(held != null && held.placement() == Spell.Placement.PORTAL && held.anchors().equals(List.of(Rune.EARTH))
                        && held.elements().isEmpty(), "Earth across the shaft anchors the far gate, and is no element of it");
        helper.assertTrue(held.steadiness() > far.steadiness() + 0.2, "An anchored gate holds far steadier than a bare one");
        List<SketchReader.Part> parts = parts(false, circle(150, 250, 35), arrow(150, 210, 150, 90), circle(150, 50, 35),
                rune(Rune.DARK, 150, 150, 0.08f, 0, 0, false));
        helper.assertTrue(Spell.groups(parts).size() == 1, "A gate is one spell: circles, arrow and anchor together");
        helper.assertTrue(parts.stream().anyMatch(p -> p.gate() == SketchReader.Gate.ANCHOR && p.rune() == Rune.DARK),
                "Dark across the shaft is an anchor");
        // A shaft that wavers, out of a circle, is still an arrow and not a bolt of Lightning.
        List<SketchReader.Part> wavy = parts(false, circle(150, 250, 35), wavyArrow(9), circle(150, 50, 35));
        helper.assertTrue(wavy.stream().anyMatch(p -> p.sign() == Sign.ARROW && p.gate() == SketchReader.Gate.LONG)
                        && wavy.stream().noneMatch(SketchReader.Part::isRune),
                "A wavering arrow between two circles reads as an arrow: " + wavy);
        helper.succeed();
    }

    @GameTest
    public void gatesMoveTheCasterNearAndFar(GameTestHelper helper) {
        ServerPlayer player = mage(helper, Glyph.CIRCLE, Glyph.ARROW, Glyph.EARTH);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Magic.GOLDEN_WAND));
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
        floor(helper, from.offset(-1, -1, -1), from.offset(10, -1, 1));
        player.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(from.east(8)).add(0, 0.6, 0));
        Vec3 before = player.position();
        SpellCaster.cast(player, List.of(circle(150, 250, 35), arrow(150, 215, 150, 80)), 300);
        helper.assertTrue(player.position().distanceTo(before) > 2 && player.getX() > before.x,
                "A short gate steps the caster the way its arrow points");

        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
        player.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        var result = SpellCaster.cast(player, List.of(circle(150, 250, 35), arrow(150, 210, 150, 90), circle(150, 50, 35),
                rune(Rune.EARTH, 150, 150, 0.08f, 0, 0, false)), 300);
        helper.assertTrue(result != null && result.spell().placement() == Spell.Placement.PORTAL,
                "Circle, arrow, circle with Earth across is cast as an anchored far gate");
        helper.assertTrue(de.ipnats.hardwrought.magic.Portals.isOpen(player), "A far gate waits open for its destination");
        BlockPos target = from.east(8);
        helper.assertTrue(de.ipnats.hardwrought.magic.Portals.travel(player, target.getX(), target.getY(), target.getZ()),
                "Naming a place steps through the gate");
        helper.assertTrue(player.position().distanceTo(Vec3.atBottomCenterOf(target)) < 1.5,
                "Anchored with Earth, it lands where it was told: " + player.position() + " for " + target);
        helper.assertTrue(!de.ipnats.hardwrought.magic.Portals.isOpen(player)
                        && !de.ipnats.hardwrought.magic.Portals.travel(player, target.getX(), target.getY(), target.getZ()),
                "A gate is gone once passed");
        helper.succeed();
    }

    @GameTest(maxTicks = 120)
    public void squareLaysATrap(GameTestHelper helper) {
        ServerPlayer player = mage(helper, Glyph.FIRE, Glyph.SQUARE);
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
        floor(helper, from.offset(-1, -1, -1), from.offset(8, -1, 1));
        player.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atBottomCenterOf(from.east(4)));
        var result = SpellCaster.cast(player, List.of(rune(Rune.FIRE, 80, 150),
                path(150, 80, 260, 80, 260, 190, 150, 190, 150, 80)), 300);
        helper.assertTrue(result != null && result.spell().trap(), "Fire with a square is a trap");
        helper.runAfterDelay(40, () -> {
            Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(4, 1, 1));
            husk.setNoAi(true);
            float full = husk.getHealth();
            helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < full || husk.isOnFire(),
                    "and it springs on whatever steps onto it"));
        });
    }

    @GameTest
    public void waterIsWeakInTheNether(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The Nether exists");
        try {
            var method = SpellCaster.class.getDeclaredMethod("surroundings", ServerLevel.class, Rune.class);
            method.setAccessible(true);
            helper.assertTrue((double) method.invoke(null, nether, Rune.WATER) < 1, "Water magic is weaker in the Nether");
            helper.assertTrue((double) method.invoke(null, nether, Rune.FIRE) == 1, "Fire magic is not");
            helper.assertTrue((double) method.invoke(null, helper.getLevel(), Rune.WATER) == 1, "Water is itself elsewhere");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        helper.succeed();
    }

    @GameTest
    public void ruinsTeachTheirRune(GameTestHelper helper) {
        ServerPlayer player = mage(helper);
        BlockPos ground = helper.absolutePos(new BlockPos(3, 1, 3));
        floor(helper, ground.offset(-3, -1, -3), ground.offset(3, -1, 3));
        helper.assertTrue(RuneRuins.build(helper.getLevel(), ground, RandomSource.create(7), Glyph.ARROW), "A ruin stands");
        var stone = helper.getLevel().getBlockState(ground.above());
        helper.assertTrue(stone.is(Magic.RUNE_STONE) && stone.getValue(RuneStoneBlock.GLYPH) == Glyph.ARROW,
                "with the stone of its rune on the plinth");
        RuneStoneBlock.study(helper.getLevel(), player, Glyph.ARROW, Vec3.atCenterOf(ground.above()));
        helper.assertTrue(RuneKnowledge.knows(player, Sign.ARROW), "and studying the carving teaches it");
        helper.succeed();
    }
}
