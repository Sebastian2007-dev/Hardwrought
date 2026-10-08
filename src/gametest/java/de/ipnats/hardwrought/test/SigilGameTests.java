package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.magic.CastQuality;
import de.ipnats.hardwrought.magic.CastSpellPayload;
import de.ipnats.hardwrought.magic.Glyph;
import de.ipnats.hardwrought.magic.Magic;
import de.ipnats.hardwrought.magic.Rune;
import de.ipnats.hardwrought.magic.SigilBlockEntity;
import de.ipnats.hardwrought.magic.SigilDesign;
import de.ipnats.hardwrought.magic.SketchReader;
import de.ipnats.hardwrought.magic.Spell;
import de.ipnats.hardwrought.magic.SpellCaster;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static de.ipnats.hardwrought.test.MagicGameTests.circle;
import static de.ipnats.hardwrought.test.MagicGameTests.floor;
import static de.ipnats.hardwrought.test.MagicGameTests.mage;
import static de.ipnats.hardwrought.test.MagicGameTests.path;
import static de.ipnats.hardwrought.test.MagicGameTests.rune;
import static de.ipnats.hardwrought.test.MagicGameTests.stroke;

/** Sigils: two circles, an element in a figure, instructions in the ring; laid on the ground, working by themselves. */
public final class SigilGameTests {
    /** A regular figure of n corners around (cx, cy). */
    static CastSpellPayload.Stroke figure(float cx, float cy, float r, int n) {
        List<float[]> points = new ArrayList<>();
        double start = n == 4 ? 45 : -90;
        for (int i = 0; i < n; i++) {
            double a0 = Math.toRadians(start + 360.0 * i / n), a1 = Math.toRadians(start + 360.0 * (i + 1) / n);
            for (int k = 0; k < 10; k++) {
                double t = k / 10.0;
                points.add(new float[] {(float) (cx + r * ((1 - t) * Math.cos(a0) + t * Math.cos(a1))),
                        (float) (cy + r * ((1 - t) * Math.sin(a0) + t * Math.sin(a1)))});
            }
        }
        points.add(points.getFirst());
        return stroke(points);
    }

    /** An arrow with a foot across its tail and its head drawn apart, as in the example sigil. */
    static List<CastSpellPayload.Stroke> footedArrow(float ax, float ay, float bx, float by) {
        double dx = bx - ax, dy = by - ay, l = Math.hypot(dx, dy), ux = dx / l, uy = dy / l;
        return List.of(
                path((float) (ax - uy * 12), (float) (ay + ux * 12), (float) (ax + uy * 12), (float) (ay - ux * 12)),
                path(ax, ay, bx, by),
                path((float) (bx - ux * 10 - uy * 8), (float) (by - uy * 10 + ux * 8), bx, by,
                        (float) (bx - ux * 10 + uy * 8), (float) (by - uy * 10 - ux * 8)));
    }

    /** Two circles around (150, 150), a figure of the given corners, and an element in it. */
    static List<CastSpellPayload.Stroke> base(int corners, Rune element) {
        List<CastSpellPayload.Stroke> strokes = new ArrayList<>();
        strokes.add(circle(150, 150, 120));
        strokes.add(circle(150, 150, 48));
        strokes.add(figure(150, 150, 32, corners));
        strokes.add(rune(element, 150, 150, 0.05f, 0, 0, false));
        return strokes;
    }

    /** The example sigil: Fire in a square, four arrows pointing in from four sides. */
    static List<CastSpellPayload.Stroke> example() {
        List<CastSpellPayload.Stroke> strokes = base(4, Rune.FIRE);
        strokes.addAll(footedArrow(150, 45, 150, 85));
        strokes.addAll(footedArrow(150, 255, 150, 215));
        strokes.addAll(footedArrow(45, 150, 85, 150));
        strokes.addAll(footedArrow(255, 150, 215, 150));
        return strokes;
    }

    static SigilDesign read(List<CastSpellPayload.Stroke> strokes) {
        List<float[]> xs = new ArrayList<>(), ys = new ArrayList<>();
        for (CastSpellPayload.Stroke s : strokes) {
            xs.add(s.xs());
            ys.add(s.ys());
        }
        return SketchReader.readSigil(xs, ys, 300);
    }

    /** A mage on a floor, looking down at the ground two blocks ahead (east). */
    static ServerPlayer mageOnFloor(GameTestHelper helper, Glyph... known) {
        ServerPlayer player = mage(helper, known);
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
        floor(helper, from.offset(-1, -1, -1), from.offset(10, -1, 2));
        player.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atBottomCenterOf(from.east(2)).add(0, 0.01, 0));
        return player;
    }

    @GameTest
    public void theExampleSigilIsRead(GameTestHelper helper) {
        SigilDesign example = read(example());
        helper.assertTrue(example != null && example.element() == Rune.FIRE && example.corners() == 4,
                "Two circles, Fire in a square: " + example);
        helper.assertTrue(example.arrows() == 4 && !example.directed() && example.cost() == 4 && example.overflow() == 0,
                "four arrows pointing in cancel out, and a square carries all four: " + example);
        List<CastSpellPayload.Stroke> one = base(4, Rune.FIRE);
        one.addAll(footedArrow(45, 150, 85, 150));
        SigilDesign right = read(one);
        helper.assertTrue(right.directed() && right.dirX() > 0.9, "One arrow pointing right gives the sigil a direction");
        List<CastSpellPayload.Stroke> waiting = base(6, Rune.LIGHT);
        waiting.add(figure(240, 70, 14, 4));
        waiting.add(circle(60, 240, 14));
        waiting.add(path(235, 210, 235, 235));
        SigilDesign hex = read(waiting);
        helper.assertTrue(hex.corners() == 6 && hex.presence() && hex.shape() == SigilDesign.Shape.SPHERE && hex.strength() == 1,
                "A hexagon, a square in the ring, a small circle and a stroke: waits, a sphere, stronger: " + hex);
        helper.assertTrue(read(List.of(circle(150, 150, 120), rune(Rune.FIRE, 150, 150))) == null, "One circle is no sigil");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void aHoveringFireBurnsWhatStepsIntoIt(GameTestHelper helper) {
        ServerPlayer player = mageOnFloor(helper, Glyph.FIRE, Glyph.ARROW);
        var result = SpellCaster.cast(player, example(), 300);
        helper.assertTrue(result != null && result.sigil() != null && result.quality().outcome() == CastQuality.Outcome.SUCCESS,
                "The example sigil is inscribed cleanly: " + (result == null ? null : result.quality()));
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 1));
        helper.assertTrue(helper.getLevel().getBlockState(at).is(Magic.SIGIL), "It lies where the mage looked");
        var sigil = (SigilBlockEntity) helper.getLevel().getBlockEntity(at);
        helper.assertTrue(sigil.element() == Rune.FIRE && !sigil.directed(), "a fire that hovers");
        Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(3, 1, 1));
        husk.setNoAi(true);
        float full = husk.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < full || husk.isOnFire(), "and it burns what stands in it"));
    }

    @GameTest(maxTicks = 120)
    public void aDirectedSigilSendsItsElementOff(GameTestHelper helper) {
        ServerPlayer player = mageOnFloor(helper, Glyph.FIRE, Glyph.ARROW);
        List<CastSpellPayload.Stroke> strokes = base(4, Rune.FIRE);
        // Up the page is ahead of whoever inscribes it: east, here.
        strokes.addAll(footedArrow(150, 255, 150, 215));
        SpellCaster.cast(player, strokes, 300);
        var sigil = (SigilBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 1)));
        helper.assertTrue(sigil != null && sigil.directed(), "One arrow: a sigil that shoots");
        // Within the test's walls, which a sigil's fire stops at like any other.
        Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(6, 1, 1));
        husk.setNoAi(true);
        float full = husk.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < full || husk.isOnFire(), "and its fire reaches the husk ahead"));
    }

    @GameTest(maxTicks = 140)
    public void aWaitingSigilWaits(GameTestHelper helper) {
        ServerPlayer player = mageOnFloor(helper, Glyph.FIRE, Glyph.SQUARE);
        List<CastSpellPayload.Stroke> strokes = base(4, Rune.FIRE);
        strokes.add(figure(240, 70, 14, 4));
        SpellCaster.cast(player, strokes, 300);
        var sigil = (SigilBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 1)));
        helper.assertTrue(sigil != null && sigil.waiting(), "A square in the ring: it waits");
        helper.runAfterDelay(40, () -> {
            Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(3, 1, 1));
            husk.setNoAi(true);
            float full = husk.getHealth();
            helper.succeedWhen(() -> helper.assertTrue(husk.getHealth() < full || husk.isOnFire(), "until someone comes"));
        });
    }

    @GameTest(maxTicks = 240)
    public void tooManyInstructionsMakeASigilUnstable(GameTestHelper helper) {
        ServerPlayer player = mageOnFloor(helper, Glyph.FIRE, Glyph.ARROW);
        List<CastSpellPayload.Stroke> strokes = base(3, Rune.FIRE);
        strokes.addAll(footedArrow(150, 45, 150, 85));
        strokes.addAll(footedArrow(150, 255, 150, 215));
        strokes.addAll(footedArrow(45, 150, 85, 150));
        strokes.addAll(footedArrow(255, 150, 215, 150));
        strokes.add(path(230, 60, 230, 85));
        var result = SpellCaster.cast(player, strokes, 300);
        helper.assertTrue(result != null && result.sigil().overflow() == 2, "Five instructions on a triangle: two too many");
        helper.assertTrue(result.quality().outcome() == CastQuality.Outcome.SURGE, "so it is unstable: " + result.quality());
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 1));
        helper.succeedWhen(() -> helper.assertTrue(!helper.getLevel().getBlockState(at).is(Magic.SIGIL), "and gives way soon"));
    }

    @GameTest
    public void earthGroundsLightning(GameTestHelper helper) {
        Spell grounded = MagicGameTests.read(rune(Rune.LIGHTNING, 80, 150), rune(Rune.EARTH, 220, 150));
        helper.assertTrue(grounded.cancelled() && grounded.power() < 0.2, "Earth grounds Lightning");
        Spell bolt = MagicGameTests.read(rune(Rune.LIGHTNING, 150, 150));
        helper.assertTrue(bolt.core() == Rune.LIGHTNING && !bolt.cancelled(), "Lightning alone is Lightning");
        helper.succeed();
    }

    @GameTest(maxTicks = 60)
    public void lightningLeapsOn(GameTestHelper helper) {
        ServerPlayer player = mage(helper, Glyph.LIGHTNING, Glyph.ARROW);
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
        floor(helper, from.offset(-1, -1, -1), from.offset(8, -1, 3));
        player.teleportTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        Husk first = helper.spawn(EntityTypes.HUSK, new BlockPos(4, 1, 1));
        Husk second = helper.spawn(EntityTypes.HUSK, new BlockPos(5, 1, 3));
        first.setNoAi(true);
        second.setNoAi(true);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, first.getEyePosition());
        float full = second.getHealth();
        // Lightning on its own acts just in front: reach further with an arrow.
        SpellCaster.cast(player, List.of(rune(Rune.LIGHTNING, 80, 150), MagicGameTests.arrow(220, 280, 220, 60)), 300);
        helper.succeedWhen(() -> helper.assertTrue(second.getHealth() < full, "Lightning leaps on to the husk beside the first"));
    }
}
