package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModEffects;
import de.ipnats.hardwrought.magic.CastQuality;
import de.ipnats.hardwrought.magic.CastSpellPayload;
import de.ipnats.hardwrought.magic.Combos;
import de.ipnats.hardwrought.magic.Glyph;
import de.ipnats.hardwrought.magic.Magic;
import de.ipnats.hardwrought.magic.Rune;
import de.ipnats.hardwrought.magic.SpellCaster;
import de.ipnats.hardwrought.magic.SpellForm;
import de.ipnats.hardwrought.magic.Veil;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.ipnats.hardwrought.test.MagicGameTests.circle;
import static de.ipnats.hardwrought.test.MagicGameTests.floor;
import static de.ipnats.hardwrought.test.MagicGameTests.mage;
import static de.ipnats.hardwrought.test.MagicGameTests.path;
import static de.ipnats.hardwrought.test.MagicGameTests.read;
import static de.ipnats.hardwrought.test.MagicGameTests.rune;
import static de.ipnats.hardwrought.test.MagicGameTests.stroke;

/** Runes that work together: see {@link Combos}. */
public final class ComboGameTests {
    private static CastSpellPayload.Stroke spiral(float x, float y) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            double t = i / 60.0, a = t * Math.PI * 4.5, r = 4 + 40 * t;
            points.add(new float[] {x + (float) (r * Math.cos(a)), y + (float) (r * Math.sin(a))});
        }
        return stroke(points);
    }

    private static String combo(CastSpellPayload.Stroke... strokes) {
        Combos.Combo combo = Combos.find(read(strokes));
        return combo == null ? null : combo.id();
    }

    private static ServerPlayer goldenMage(GameTestHelper helper, Glyph... known) {
        ServerPlayer player = mage(helper, known);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Magic.GOLDEN_WAND));
        return player;
    }

    @GameTest
    public void drawingsMakeTheirCombinations(GameTestHelper helper) {
        Set<String> ids = new HashSet<>();
        for (Combos.Combo combo : Combos.all()) {
            helper.assertTrue(ids.add(combo.id()), "Every combination has a name of its own: " + combo.id());
            helper.assertTrue(combo.form() != null && combo.core() != null, combo.id() + " says where and of what it is");
        }
        helper.assertTrue("veil".equals(combo(circle(150, 150, 70), rune(Rune.DARK, 150, 150, 0.4f, 0, 0, false))),
                "Darkness across a circle is the veil");
        helper.assertTrue("shadow_step".equals(combo(circle(150, 150, 90), rune(Rune.DARK, 150, 150, 0.45f, 0, 0, false),
                        rune(Rune.WIND, 150, 165, 0.08f, 0, 0, false))),
                "and with Wind in the circle too, the shadow step: the combination asking for more wins");
        helper.assertTrue("healing_ring".equals(combo(rune(Rune.LIGHT, 80, 150), spiral(220, 150))),
                "Light with a spiral is a healing ring");
        helper.assertTrue("healing_ring".equals(combo(circle(110, 150, 70), rune(Rune.LIGHT, 110, 150, 0.4f, 0, 0, false),
                        spiral(250, 250))),
                "and across a circle, one that goes along with its caster");
        helper.assertTrue("firestorm".equals(combo(path(150, 40, 260, 230, 40, 230, 150, 40),
                        rune(Rune.FIRE, 135, 175, 0.07f, 0, 0, false), rune(Rune.WIND, 175, 185, 0.07f, 0, 0, false))),
                "Fire and Wind in a triangle are a firestorm");
        helper.assertTrue("snare".equals(combo(path(60, 60, 240, 60, 240, 240, 60, 240, 60, 60),
                        rune(Rune.EARTH, 150, 150, 0.1f, 0, 0, false))),
                "Earth in a square is a snare");
        helper.assertTrue(combo(rune(Rune.FIRE, 150, 150)) == null, "A plain Fire stream is no combination");
        helper.assertTrue(Combos.find(SpellForm.SELF, Rune.DARK, true, List.of()) == null,
                "Darkness struck through is no veil");
        helper.succeed();
    }

    @GameTest
    public void darknessOnOneselfIsATrueVeil(GameTestHelper helper) {
        ServerPlayer player = goldenMage(helper, Glyph.DARK, Glyph.CIRCLE);
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        floor(helper, at.offset(-2, -1, -2), at.offset(4, -1, 2));
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        var zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(5, 1, 2));

        var result = SpellCaster.cast(player, List.of(circle(150, 150, 70), rune(Rune.DARK, 150, 150, 0.4f, 0, 0, false)), 300);
        helper.assertTrue(result != null && result.quality().outcome() == CastQuality.Outcome.SUCCESS,
                "Darkness across a circle casts cleanly; was " + (result == null ? null : result.quality()));
        helper.assertTrue(Veil.veiled(player) && player.hasEffect(ModEffects.VEILED), "and veils its caster");
        int ticks = player.getEffect(ModEffects.VEILED).getDuration();
        helper.assertTrue(ticks >= 100 && ticks <= 200, "for five to ten seconds, was " + ticks + " ticks");
        helper.assertTrue(player.getVisibilityPercent(helper.getLevel(), zombie) == 0, "No monster can see a veiled body");

        // Striking from the veil tears it.
        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1);
        helper.assertTrue(!Veil.veiled(player) && !player.hasEffect(ModEffects.VEILED), "A blow struck from the veil tears it");

        // Anything veiled is shaken off: a zombie after a villager loses it, and cannot set on it again.
        var villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 0));
        zombie.setTarget(villager);
        helper.assertTrue(zombie.getTarget() == villager, "The zombie has set on the villager");
        Veil.apply(villager, 100);
        helper.assertTrue(zombie.getTarget() == null, "Veiled, the villager is lost to it at once");
        zombie.setTarget(villager);
        helper.assertTrue(zombie.getTarget() == null, "and cannot be found again while the veil holds");
        helper.succeed();
    }

    @GameTest(maxTicks = 60)
    public void aHealingRingMendsFriendsAndNotFoes(GameTestHelper helper) {
        ServerPlayer player = goldenMage(helper, Glyph.LIGHT, Glyph.CIRCLE, Glyph.SPIRAL);
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        floor(helper, at.offset(-3, -1, -3), at.offset(3, -1, 3));
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.setHealth(8);
        var villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 3));
        villager.setHealth(5);
        var spider = helper.spawnWithNoFreeWill(EntityTypes.SPIDER, new BlockPos(3, 1, 4));
        spider.setHealth(5);
        var zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 3));
        float zombieHealth = zombie.getHealth();

        // Light across a circle, with a spiral: the ring goes along with its caster.
        var result = SpellCaster.cast(player, List.of(circle(110, 150, 70), rune(Rune.LIGHT, 110, 150, 0.4f, 0, 0, false),
                spiral(250, 250)), 300);
        helper.assertTrue(result != null && !result.quality().outcome().failed(), "The ring is cast; was "
                + (result == null ? null : result.quality()));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(player.getHealth() > 8, "The caster in the ring is mended");
            helper.assertTrue(villager.getHealth() > 5, "and so is the villager beside them");
            helper.assertTrue(spider.getHealth() <= 5, "but not the spider");
            helper.assertTrue(zombie.getHealth() < zombieHealth, "and the undead burn in it");
            helper.succeed();
        });
    }

    /**
     * How far above the ground of the test the firestorm is cast. Down there it shared the ground with
     * whatever the tests beside it had standing about: an evoker within twelve blocks of the caster
     * unsettles the hand and can send the burst astray, and a monster wandering into the line of the
     * cast takes the burst in the zombie's place.
     */
    private static final int RAISED = 16;

    @GameTest(maxTicks = 60)
    public void fireAndWindBurstIntoAFirestorm(GameTestHelper helper) {
        ServerPlayer player = goldenMage(helper, Glyph.FIRE, Glyph.WIND, Glyph.TRIANGLE);
        // Everyone in it stands inside the test's own eight by eight, where the chunks are kept ticking.
        BlockPos at = helper.absolutePos(new BlockPos(1, 1 + RAISED, 3));
        floor(helper, at.offset(-1, -1, -3), at.offset(9, -1, 4));
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        var near = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(5, 1 + RAISED, 3));
        // Beyond a plain fire burst's reach of three blocks, within a firestorm's five.
        var far = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(5, 1 + RAISED, 7));
        // Helmets, or the sun sets them alight by day and "it burns" says nothing about the spell.
        near.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        far.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, near.position().add(0, 1, 0));

        var result = SpellCaster.cast(player, List.of(path(150, 40, 260, 230, 40, 230, 150, 40),
                rune(Rune.FIRE, 135, 175, 0.07f, 0, 0, false), rune(Rune.WIND, 175, 185, 0.07f, 0, 0, false)), 300);
        helper.assertTrue(result != null && !result.quality().outcome().failed(), "The firestorm is cast; was "
                + (result == null ? null : result.quality()));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(near.getRemainingFireTicks() > 0, "What it strikes burns");
            helper.assertTrue(far.getRemainingFireTicks() > 0, "and so does what stands four blocks off, out of a plain burst's reach");
            Vec3 pushed = far.position().subtract(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 1 + RAISED, 7))));
            helper.assertTrue(pushed.horizontalDistance() > 0.2, "and the wind flings it outward");
            // Up here they are outside what the test clears away after itself.
            near.discard();
            far.discard();
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, -1, -3), at.offset(9, -1, 4))) {
                helper.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            helper.succeed();
        });
    }
}
