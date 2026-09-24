package de.ipnats.hardwrought.client.machinery;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.machinery.ShaftBlock;
import de.ipnats.hardwrought.machinery.ShaftBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the part of a shaft that moves.
 *
 * <p>This is the whole reason the prototype exists. A block model is baked into its chunk's mesh
 * once and stays where it was put, so nothing drawn by a block model can turn. Anything that moves
 * has to be drawn per frame by a renderer like this one — which is why every machine with a moving
 * part is built as two halves: a static casing in the block model, and the moving piece here.
 *
 * <p>What gets drawn is an ordinary block state, {@code shaft_bar}, submitted through the same path
 * the game uses for a block riding a piston. That means it takes its textures off the block atlas
 * and its light off the world for free, and it means the bar can be edited as a model file rather
 * than as vertex code.
 *
 * <p>The angle is interpolated across the frame. Without that a shaft turns in twenty visible steps
 * a second whatever the frame rate, which looks like a slideshow rather than like machinery.
 */
public class ShaftRenderer implements BlockEntityRenderer<ShaftBlockEntity, ShaftRenderState> {
    public ShaftRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public ShaftRenderState createRenderState() {
        return new ShaftRenderState();
    }

    @Override
    public void extractRenderState(ShaftBlockEntity shaft, ShaftRenderState state, float partialTick,
                                   Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(shaft, state, partialTick, camera, crumbling);
        state.axis = shaft.getBlockState().getValue(ShaftBlock.AXIS);
        // Where it stood at the last tick, plus however far into the next one this frame is.
        state.angle = shaft.angle() + shaft.degreesPerTick() * partialTick;

        Level level = shaft.getLevel();
        BlockPos pos = shaft.getBlockPos();
        state.bar.blockPos = pos;
        state.bar.randomSeedPos = pos;
        state.bar.blockState = ModBlocks.SHAFT_BAR.defaultBlockState();
        if (level instanceof ClientLevel client) {
            state.bar.biome = client.getBiome(pos);
            state.bar.cardinalLighting = client.cardinalLighting();
            state.bar.lightEngine = client.getLightEngine();
        }
        // Each belt is drawn once, by the end with the lower position.
        BlockPos other = shaft.belt();
        state.belt = other != null && pos.asLong() < other.asLong()
                ? new Vec3(other.getX() - pos.getX(), other.getY() - pos.getY(), other.getZ() - pos.getZ()) : null;
        if (state.belt != null) {
            state.strip.blockPos = pos;
            state.strip.randomSeedPos = pos;
            state.strip.blockState = ModBlocks.BELT_STRIP.defaultBlockState();
            state.strip.biome = state.bar.biome;
            state.strip.cardinalLighting = state.bar.cardinalLighting;
            state.strip.lightEngine = state.bar.lightEngine;
        }
    }

    @Override
    public void submit(ShaftRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        if (state.bar.lightEngine == null) return;
        pose.pushPose();
        // Turn about the middle of the block rather than about its corner, which is where the
        // model's own origin sits.
        pose.translate(0.5f, 0.5f, 0.5f);
        orient(pose, state.axis);
        // The bar model lies along Y, so after orienting, the spin is always about the local Y.
        pose.rotateDegrees(Axis.YP, state.angle);
        pose.translate(-0.5f, -0.5f, -0.5f);
        // The last argument is an outline colour, not light: the light comes from the render state's
        // own light engine. Anything but zero draws the entity-glow outline round the bar.
        collector.submitMovingBlock(pose, state.bar, 0);
        pose.popPose();
        if (state.belt != null) submitBelt(state, pose, collector);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // A belt reaches up to eight blocks from the shaft that draws it.
        return true;
    }

    /**
     * The belt: two straight runs between the shafts, one over and one under, each the strip model
     * stretched to length. Built in a frame whose X runs from this shaft to the other, whose Z is the
     * shafts' axis, and whose Y is square to both.
     */
    private static void submitBelt(ShaftRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        Vec3 run = state.belt;
        double length = run.length();
        org.joml.Vector3f x = new org.joml.Vector3f((float) (run.x / length), (float) (run.y / length), (float) (run.z / length));
        org.joml.Vector3f z = switch (state.axis) {
            case X -> new org.joml.Vector3f(1, 0, 0);
            case Y -> new org.joml.Vector3f(0, 1, 0);
            case Z -> new org.joml.Vector3f(0, 0, 1);
        };
        org.joml.Vector3f y = new org.joml.Vector3f(z).cross(x);
        org.joml.Matrix4f frame = new org.joml.Matrix4f(
                x.x, x.y, x.z, 0,
                y.x, y.y, y.z, 0,
                z.x, z.y, z.z, 0,
                0, 0, 0, 1);
        for (float side : new float[] {BELT_RADIUS, -BELT_RADIUS}) {
            pose.pushPose();
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.mulPose(frame);
            pose.translate(0.0f, side, 0.0f);
            pose.scale((float) length, 1.0f, 1.0f);
            pose.translate(0.0f, -0.5f, -0.5f);
            collector.submitMovingBlock(pose, state.strip, 0);
            pose.popPose();
        }
    }

    /** How far each run of the belt lies from the axle: just outside the bar. */
    private static final float BELT_RADIUS = 0.2f;

    /** Lays the model's own Y down onto whichever axis this shaft runs along. */
    private static void orient(PoseStack pose, Direction.Axis axis) {
        switch (axis) {
            case X -> pose.rotateDegrees(Axis.ZP, 90.0f);
            case Z -> pose.rotateDegrees(Axis.XP, 90.0f);
            case Y -> { }
        }
    }
}
