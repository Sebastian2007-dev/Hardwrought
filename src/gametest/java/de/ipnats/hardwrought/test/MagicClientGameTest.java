package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.magic.ActionCasting;
import de.ipnats.hardwrought.magic.CastSpellPayload;
import de.ipnats.hardwrought.magic.Magic;
import de.ipnats.hardwrought.magic.MagicShields;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Magic on screen: drawing in action casting with the real mouse, a shield seen from inside and
 * from outside, and a step through nothing. Photographed, since none of it can be judged otherwise.
 */
public final class MagicClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("time set 13000");
            world.getServer().runCommand("hardwrought magic learn @p all");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Magic.GOLDEN_WAND));
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            });
            context.waitTicks(20);

            // The parchment reads what is drawn as the server will: shapes and arrows by name, an
            // arrow of two strokes as one; and shapes with runes in them as spells of their own.
            context.runOnClient(client -> {
                var screen = new de.ipnats.hardwrought.client.magic.RuneCanvasScreen();
                client.gui.setScreen(screen);
                for (var s : List.of(MagicGameTests.path(40, 90, 110, 90, 75, 25, 40, 90), circle(220, 60, 40),
                        MagicGameTests.rune(de.ipnats.hardwrought.magic.Rune.LIGHT, 220, 60, 0.04f, 0, 0, false),
                        MagicGameTests.path(120, 130, 190, 130, 190, 195, 120, 195, 120, 130), MagicGameTests.arrow(60, 280, 60, 190),
                        // An arrow as it is often drawn: shaft and one half of the head in one go, the other half after.
                        MagicGameTests.path(150, 290, 150, 220, 166, 238), MagicGameTests.path(150, 220, 134, 238),
                        MagicGameTests.rune(de.ipnats.hardwrought.magic.Rune.LIGHTNING, 250, 230, 0.12f, 0, 0, false))) {
                    screen.drawForTesting(s.xs(), s.ys());
                }
            });
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-magic-canvas-signs");
            context.runOnClient(client -> client.gui.setScreen(null));
            context.waitTicks(5);

            // Action casting: hold the key, draw a stem upward that turns into a wave, and look.
            context.getInput().holdKey(ActionCasting.CAST);
            context.waitTicks(3);
            if (!context.computeOnClient(client -> ActionCasting.capturing())) {
                throw new AssertionError("Holding the cast key with a wand in hand must start drawing");
            }
            // A window in the background is handed no mouse movement, so the pen is moved as the mouse would move it.
            for (int i = 0; i < 12; i++) {
                context.runOnClient(client -> ActionCasting.move(0, -8));
                context.waitTicks(1);
            }
            for (int i = 0; i <= 24; i++) {
                double dy = -12 * Math.cos(i / 24.0 * Math.PI * 2) * 0.5;
                context.runOnClient(client -> ActionCasting.move(8, dy));
                context.waitTicks(1);
            }
            context.takeScreenshot("hardwrought-magic-action-drawing");
            context.getInput().releaseKey(ActionCasting.CAST);
            // Long enough for the wand to be ready again.
            context.waitTicks(40);

            // A plain shield: an empty circle, cast from the client as any drawing is.
            context.runOnClient(client -> ClientPlayNetworking.send(new CastSpellPayload(List.of(circle(150, 150, 110)), 300, false)));
            context.waitTicks(12);
            if (!world.getServer().computeOnServer(server -> MagicShields.shielded(server.getPlayerList().getPlayers().getFirst()))) {
                throw new AssertionError("An empty circle must raise a shield");
            }
            context.takeScreenshot("hardwrought-magic-shield-inside");
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(4);
            context.takeScreenshot("hardwrought-magic-shield-outside");
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

            // A step through nothing: Light running over the edge of a circle, and an arrow ahead.
            context.waitTicks(60);
            double[] before = context.computeOnClient(client -> new double[] {client.player.getX(), client.player.getZ()});
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.runOnClient(client -> ClientPlayNetworking.send(new CastSpellPayload(List.of(
                    circle(150, 150, 70), line(150, 260, 150, 40), arrow()), 300, false)));
            context.waitTicks(3);
            context.takeScreenshot("hardwrought-magic-teleport");
            double[] after = context.computeOnClient(client -> new double[] {client.player.getX(), client.player.getZ()});
            if (Math.hypot(after[0] - before[0], after[1] - before[1]) < 2) {
                throw new AssertionError("A rune across a circle with an arrow must move the caster");
            }
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

            // A far gate: circle, arrow, circle, Earth across the shaft. It asks where to; it goes there.
            context.waitTicks(60);
            context.runOnClient(client -> ClientPlayNetworking.send(new CastSpellPayload(List.of(
                    MagicGameTests.circle(150, 250, 35), MagicGameTests.arrow(150, 210, 150, 90), MagicGameTests.circle(150, 50, 35),
                    MagicGameTests.rune(de.ipnats.hardwrought.magic.Rune.EARTH, 150, 150, 0.08f, 0, 0, false)), 300, false)));
            context.waitTicks(10);
            if (!context.computeOnClient(client -> client.gui.screen() instanceof de.ipnats.hardwrought.client.magic.GateScreen)) {
                throw new AssertionError("A far gate must ask for its destination");
            }
            context.takeScreenshot("hardwrought-magic-gate-coordinates");
            int[] goal = context.computeOnClient(client -> new int[] {client.player.getBlockX() + 40, client.player.getBlockY(),
                    client.player.getBlockZ() + 25});
            context.runOnClient(client -> ((de.ipnats.hardwrought.client.magic.GateScreen) client.gui.screen())
                    .goForTesting(goal[0], goal[1], goal[2]));
            context.waitTicks(20);
            double[] arrived = context.computeOnClient(client -> new double[] {client.player.getX(), client.player.getZ()});
            if (Math.hypot(arrived[0] - goal[0] - 0.5, arrived[1] - goal[2] - 0.5) > 4) {
                throw new AssertionError("The far gate must lead where it was told, not to " + arrived[0] + ", " + arrived[1]);
            }
            context.takeScreenshot("hardwrought-magic-gate-arrived");

            // Every element alone, as a stream from the hand: something to see, every time.
            for (var rune : de.ipnats.hardwrought.magic.Rune.values()) {
                context.waitTicks(30);
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 0, 15, true);
                });
                context.waitTicks(2);
                context.runOnClient(client -> ClientPlayNetworking.send(new CastSpellPayload(List.of(
                        MagicGameTests.rune(rune, 150, 150, 0.2f, 0, 0, false)), 300, false)));
                context.waitTicks(4);
                context.takeScreenshot("hardwrought-magic-stream-" + rune.id());
            }

            // A sigil: the example of the specification, Fire in a square with four arrows pointing in,
            // laid on the ground just ahead, its fire hovering above it.
            context.waitTicks(40);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 0, 55, true);
            });
            context.waitTicks(5);
            context.runOnClient(client -> ClientPlayNetworking.send(new CastSpellPayload(SigilGameTests.example(), 300, false)));
            context.waitTicks(60);
            boolean laid = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                for (var pos : net.minecraft.core.BlockPos.betweenClosed(player.blockPosition().offset(-4, -2, -4),
                        player.blockPosition().offset(4, 2, 4))) {
                    if (level.getBlockState(pos).is(de.ipnats.hardwrought.magic.Magic.SIGIL)) return true;
                }
                return false;
            });
            if (!laid) throw new AssertionError("The example sigil must be laid on the ground ahead");
            context.takeScreenshot("hardwrought-magic-sigil");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(player.level(), player.getX(), player.getY() + 3, player.getZ() - 3, java.util.Set.of(), 0, 60, true);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-magic-sigil-above");
        }
    }

    private static CastSpellPayload.Stroke circle(float cx, float cy, float r) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= 48; i++) {
            double a = -Math.PI / 2 + i / 48.0 * Math.PI * 2;
            points.add(new float[] {cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a)});
        }
        return stroke(points);
    }

    private static CastSpellPayload.Stroke line(float x0, float y0, float x1, float y1) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= 20; i++) points.add(new float[] {x0 + (x1 - x0) * i / 20f, y0 + (y1 - y0) * i / 20f});
        return stroke(points);
    }

    /** An arrow pointing straight up the parchment, in one stroke: ahead of the caster. */
    private static CastSpellPayload.Stroke arrow() {
        List<float[]> points = new ArrayList<>();
        float[][] corners = {{260, 280}, {260, 90}, {240, 120}, {260, 90}, {280, 120}};
        for (int c = 0; c + 1 < corners.length; c++) {
            for (int i = 0; i < 12; i++) {
                float t = i / 12f;
                points.add(new float[] {corners[c][0] + (corners[c + 1][0] - corners[c][0]) * t,
                        corners[c][1] + (corners[c + 1][1] - corners[c][1]) * t});
            }
        }
        points.add(corners[corners.length - 1]);
        return stroke(points);
    }

    private static CastSpellPayload.Stroke stroke(List<float[]> points) {
        float[] xs = new float[points.size()], ys = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i)[0];
            ys[i] = points.get(i)[1];
        }
        return new CastSpellPayload.Stroke(xs, ys, 700);
    }
}
