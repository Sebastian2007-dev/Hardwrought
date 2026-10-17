package de.ipnats.hardwrought.client.ritual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import de.ipnats.hardwrought.ritual.Temple;
import de.ipnats.hardwrought.ritual.TempleFaults;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Every block of a temple still wrong, marked red in the world (see {@link TempleFaults}): where the wrong
 * block stands, or nothing does, the block the plan wants there is drawn over it as a red ghost, a little
 * larger, breathing. Each mark goes as soon as that block is right; all of them after a few minutes, or
 * once the temple stands.
 */
public final class TempleFaultsClient {
    /** How long the marks stay, in ticks, and how far off they are drawn. */
    private static final int LASTS = 20 * 180;
    private static final double DRAWN_WITHIN = 96;
    private static final int RED = 0xFF3A30;

    private static BlockPos altar;
    private static Temple.Theme theme;
    private static List<BlockPos> wrong = List.of();
    private static int age;
    private static Vec3 camera = Vec3.ZERO;

    private TempleFaultsClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(TempleFaults.Faults.TYPE, (payload, context) -> {
            altar = payload.altar();
            theme = payload.themeOf();
            wrong = new ArrayList<>(payload.wrong());
            age = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(TempleFaultsClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> wrong = List.of());
        LevelExtractionEvents.END_EXTRACTION.register(context -> camera = context.camera().position());
        LevelRenderEvents.COLLECT_SUBMITS.register(TempleFaultsClient::submit);
    }

    private static void tick(Minecraft client) {
        if (wrong.isEmpty()) return;
        if (client.level == null || ++age > LASTS) {
            wrong = List.of();
            return;
        }
        // Put right since: no longer marked.
        if (age % 5 == 0) wrong.removeIf(at -> Temple.fits(theme, altar, at, client.level.getBlockState(at)));
    }

    private static void submit(LevelRenderContext context) {
        if (wrong.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        PoseStack pose = context.poseStack();
        RandomSource random = RandomSource.create(42L);
        var collector = context.submitNodeCollector().order(1);
        float breath = 0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() % 100_000) / 250.0);
        int color = ((int) ((0.35f + 0.25f * breath) * 255) << 24) | RED;
        QuadInstance instance = new QuadInstance();
        instance.setColor(color);
        instance.setLightCoords(0xF000F0);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        for (BlockPos at : wrong) {
            if (Vec3.atCenterOf(at).distanceToSqr(camera) > DRAWN_WITHIN * DRAWN_WITHIN) continue;
            BlockState want = Temple.wanted(theme, altar, at);
            if (want == null || want.isAir()) want = Blocks.CONCRETE.white().defaultBlockState();
            List<BlockStateModelPart> parts = new ArrayList<>();
            random.setSeed(42L);
            client.getModelManager().getBlockStateModelSet().get(want).collectParts(random, parts);
            pose.pushPose();
            pose.translate(at.getX() - camera.x, at.getY() - camera.y, at.getZ() - camera.z);
            // A hair larger than a block, so it shows over the wrong one standing there.
            pose.translate(0.5, 0.5, 0.5);
            pose.scale(1.04f, 1.04f, 1.04f);
            pose.translate(-0.5, -0.5, -0.5);
            collector.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(), (matrix, vertices) -> {
                for (BlockStateModelPart part : parts) {
                    for (Direction side : Direction.values()) {
                        for (BakedQuad quad : part.getQuads(side)) vertices.putBakedQuad(matrix, quad, instance);
                    }
                    for (BakedQuad quad : part.getQuads(null)) vertices.putBakedQuad(matrix, quad, instance);
                }
            });
            pose.popPose();
        }
    }

    /** The places marked just now; for tests. */
    public static List<BlockPos> markedForTesting() {
        return List.copyOf(wrong);
    }
}
