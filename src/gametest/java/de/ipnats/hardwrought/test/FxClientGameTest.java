package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.fx.FxClient;
import de.ipnats.hardwrought.client.fx.FxPresets;
import de.ipnats.hardwrought.client.fx.sound.FxSounds;
import de.ipnats.hardwrought.fx.FxEffect;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.phys.Vec3;

/**
 * The FX module on screen, at night, one effect after another. Photographed, because a shader that
 * compiles but draws nothing passes every other test there is; the first frame also proves that the
 * pipelines load at all, since a broken one stops the game from starting.
 */
public final class FxClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("time set 18000");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamerule doDaylightCycle false");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 180.0f, 8.0f, true);
                // A wall behind the effects, slabs and tall grass in front of them: the light must fall on all of it.
                var level = player.level();
                var base = player.blockPosition();
                for (int x = -9; x <= 9; x++) {
                    for (int y = 0; y < 6; y++) {
                        level.setBlockAndUpdate(base.offset(x, y, -14), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
                    }
                    if (x % 3 == 0) {
                        level.setBlockAndUpdate(base.offset(x, 0, -6), net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState());
                    } else {
                        level.setBlockAndUpdate(base.offset(x, 0, -7), net.minecraft.world.level.block.Blocks.TALL_GRASS.defaultBlockState());
                        level.setBlockAndUpdate(base.offset(x, 1, -7), net.minecraft.world.level.block.Blocks.TALL_GRASS.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
                    }
                }
            });
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();

            // Through the server, as the magic system will: the command must reach the client's engine.
            context.runOnClient(client -> client.player.connection.sendCommand("hardwrought fx play sparkle"));
            context.waitFor(client -> FxClient.engine().particleCount() > 0);
            clear(context);

            shoot(context,FxEffect.NOVA, 0, 3, "hardwrought-fx-nova");
            shoot(context,FxEffect.NOVA, 0, 22, "hardwrought-fx-nova-late");
            shoot(context,FxEffect.LIGHTNING, 0, 2, "hardwrought-fx-lightning");
            shoot(context,FxEffect.RUNE_CIRCLE, 0, 45, "hardwrought-fx-rune-circle");
            shoot(context,FxEffect.RUNE_CIRCLE, 0, 78, "hardwrought-fx-rune-pillar");
            shoot(context,FxEffect.FROST_NOVA, 0, 5, "hardwrought-fx-frost-nova");
            shoot(context,FxEffect.VORTEX, 0, 60, "hardwrought-fx-vortex");
            shoot(context,FxEffect.SHIELD, 0, 20, "hardwrought-fx-shield");
            shoot(context,FxEffect.HEAL, 0, 25, "hardwrought-fx-heal");
            shoot(context,FxEffect.BEAM, 0, 20, "hardwrought-fx-beam");
            shoot(context,FxEffect.ARCANE_BOLT, 0, 5, "hardwrought-fx-arcane-bolt");
            shoot(context,FxEffect.CHAIN_LIGHTNING, 0, 4, "hardwrought-fx-chain-lightning");
            shoot(context,FxEffect.FIREBALL, 0, 6, "hardwrought-fx-fireball");

            verifySounds(context);
            verifyCarriedTorch(context, world);
        }
    }

    /**
     * Every recipe must render to real sound: finite, audible, not clipped flat. Written out as WAV
     * files next to the screenshots, so a person can listen to them. And a synthesized sound must be
     * accepted by the sound engine like any other.
     */
    private static void verifySounds(ClientGameTestContext context) {
        java.util.Map<String, java.util.function.LongFunction<float[]>> recipes = new java.util.LinkedHashMap<>();
        recipes.put("nova", FxSounds::nova);
        recipes.put("fire_cast", FxSounds::fireCast);
        recipes.put("arcane_cast", FxSounds::arcaneCast);
        recipes.put("arcane_impact", FxSounds::arcaneImpact);
        recipes.put("thunder", FxSounds::thunder);
        recipes.put("zap", FxSounds::zap);
        recipes.put("beam_start", FxSounds::beamStart);
        recipes.put("beam_loop", FxSounds::beamLoop);
        recipes.put("rune_circle", FxSounds::runeCircle);
        recipes.put("vortex", FxSounds::vortex);
        recipes.put("frost", FxSounds::frost);
        recipes.put("shield_up", FxSounds::shieldUp);
        recipes.put("shield_loop", FxSounds::shieldLoop);
        recipes.put("shield_break", FxSounds::shieldBreak);
        recipes.put("heal", FxSounds::heal);
        recipes.put("sparkle", FxSounds::sparkle);
        java.nio.file.Path folder = java.nio.file.Path.of("fx-sounds");
        try {
            java.nio.file.Files.createDirectories(folder);
            for (var recipe : recipes.entrySet()) {
                float[] samples = recipe.getValue().apply(42);
                double energy = 0;
                for (float sample : samples) {
                    if (!Float.isFinite(sample)) throw new AssertionError("Sound " + recipe.getKey() + " is not finite");
                    energy += sample * sample;
                }
                double rms = Math.sqrt(energy / samples.length);
                if (rms < 0.01 || rms > 0.7) {
                    throw new AssertionError("Sound " + recipe.getKey() + " must be audible but not a wall of noise: rms " + rms);
                }
                writeWav(folder.resolve(recipe.getKey() + ".wav"), samples);
            }
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not write the rendered sounds", e);
        }
        context.runOnClient(client -> FxSounds.play(client.player.position(), 16, 1, FxSounds::sparkle));
        context.waitFor(client -> client.getSoundManager().getSoundEvent(
                de.ipnats.hardwrought.client.fx.sound.FxSoundInstance.EVENT) != null, 40);
    }

    private static void writeWav(java.nio.file.Path file, float[] samples) throws java.io.IOException {
        java.nio.ByteBuffer data = java.nio.ByteBuffer.allocate(44 + samples.length * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int rate = de.ipnats.hardwrought.client.fx.sound.FxSynth.RATE;
        data.put("RIFF".getBytes()).putInt(36 + samples.length * 2).put("WAVE".getBytes());
        data.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16);
        data.put("data".getBytes()).putInt(samples.length * 2);
        for (float sample : samples) data.putShort((short) Math.max(-32768, Math.min(32767, Math.round(sample * 32767))));
        java.nio.file.Files.write(file, data.array());
    }

    /** A torch carried in tall grass at night lights its surroundings, though no light block fits there. */
    private static void verifyCarriedTorch(ClientGameTestContext context,
                                           net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        clear(context);
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.level();
            var feet = player.blockPosition().north(8);
            level.setBlockAndUpdate(feet, net.minecraft.world.level.block.Blocks.TALL_GRASS.defaultBlockState());
            level.setBlockAndUpdate(feet.above(), net.minecraft.world.level.block.Blocks.TALL_GRASS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, java.util.Set.of(), 180.0f, 20.0f, true);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH));
        });
        context.waitFor(client -> de.ipnats.hardwrought.client.environment.DynamicLight.ownLevel() > 0, 100);
        context.waitTicks(10);
        context.takeScreenshot("hardwrought-fx-carried-torch-in-grass");
        world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY));
    }

    private static void clear(ClientGameTestContext context) {
        context.runOnClient(client -> FxPresets.play(FxClient.engine(), FxEffect.CLEAR, Vec3.ZERO, Vec3.ZERO, 0, 1, null));
        context.waitTicks(2);
    }

    /** Plays the effect in front of the player, waits and takes a picture. */
    private static void shoot(ClientGameTestContext context, FxEffect effect, int color, int ticks,
                              String name) {
        clear(context);
        context.runOnClient(client -> {
            var player = client.player;
            Vec3 forward = new Vec3(0, 0, -1);
            Vec3 ground = new Vec3(player.getX(), player.getY(), player.getZ()).add(forward.scale(9));
            Vec3 hand = player.getEyePosition().add(forward.scale(0.6)).add(0.35, -0.25, 0);
            Vec3 pos = effect.aim() == FxEffect.Aim.RAY ? hand : ground;
            Vec3 target = effect.aim() == FxEffect.Aim.RAY ? ground.add(0, 0.8, -6) : ground;
            FxPresets.play(FxClient.engine(), effect, pos, target, color == 0 ? effect.defaultColor() : color, 1, null);
        });
        context.waitTicks(ticks);
        context.takeScreenshot(name);
    }
}
