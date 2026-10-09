package de.ipnats.hardwrought.client.electricity;

import com.mojang.blaze3d.vertex.PoseStack;
import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the wires strung from a point of a network. Each wire is drawn once, by the end with the
 * lower position, as {@link Electricity#SEGMENTS} straight pieces along its sag — each piece the
 * one-block strand model stretched to length, the way a belt is drawn between two shafts.
 */
public class WireRenderer<T extends ElectricBlockEntity> implements BlockEntityRenderer<T, WireRenderer.State> {
    private static final Block[] STRANDS = {ElectricBlocks.WIRE_STRAND, ElectricBlocks.WIRE_STRAND_HOT,
            ElectricBlocks.CABLE_STRAND, ElectricBlocks.CABLE_STRAND_HOT};

    /** A wire from one point to another, measured from the drawing block's corner. */
    record Run(Vec3 from, Vec3 to, int strand) { }

    public static class State extends BlockEntityRenderState {
        final List<Run> runs = new ArrayList<>();
        final MovingBlockRenderState[] strands = new MovingBlockRenderState[STRANDS.length];

        State() {
            for (int i = 0; i < strands.length; i++) strands[i] = new MovingBlockRenderState();
        }
    }

    public WireRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // A wire reaches up to two dozen blocks from the point that draws it.
        return true;
    }

    @Override
    public void extractRenderState(T node, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(node, state, partialTick, camera, crumbling);
        state.runs.clear();
        BlockPos pos = node.getBlockPos();
        if (!(node.getLevel() instanceof ClientLevel level)) return;
        Vec3 corner = Vec3.atLowerCornerOf(pos);
        for (ElectricBlockEntity.Wire wire : node.wires()) {
            if (pos.asLong() >= wire.other().asLong()) continue;
            Vec3 far = level.getBlockEntity(wire.other()) instanceof ElectricBlockEntity partner
                    ? partner.terminal() : Vec3.atCenterOf(wire.other());
            int strand = (wire.gauge() == Gauge.CABLE ? 2 : 0) + (wire.hot() ? 1 : 0);
            state.runs.add(new Run(node.terminal().subtract(corner), far.subtract(corner), strand));
        }
        for (int i = 0; i < STRANDS.length; i++) {
            MovingBlockRenderState strand = state.strands[i];
            strand.blockPos = pos;
            strand.randomSeedPos = pos;
            strand.blockState = STRANDS[i].defaultBlockState();
            strand.biome = level.getBiome(pos);
            strand.cardinalLighting = level.cardinalLighting();
            strand.lightEngine = level.getLightEngine();
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (Run run : state.runs) {
            MovingBlockRenderState strand = state.strands[run.strand()];
            if (strand.lightEngine == null) continue;
            Vec3 last = run.from();
            for (int i = 1; i <= Electricity.SEGMENTS; i++) {
                Vec3 next = Electricity.along(run.from(), run.to(), i / (double) Electricity.SEGMENTS);
                piece(pose, collector, strand, last, next);
                last = next;
            }
        }
    }

    /** One straight piece: the strand model, which lies along X, laid from one point to the next. */
    private static void piece(PoseStack pose, SubmitNodeCollector collector, MovingBlockRenderState strand,
                              Vec3 from, Vec3 to) {
        Vec3 run = to.subtract(from);
        double length = run.length();
        if (length < 1e-4) return;
        Vector3f x = new Vector3f((float) (run.x / length), (float) (run.y / length), (float) (run.z / length));
        // Any direction square to the piece does for the other two axes; straight up fails only for a piece that is.
        Vector3f up = Math.abs(x.y) > 0.99f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f y = new Vector3f(up).sub(new Vector3f(x).mul(up.dot(x))).normalize();
        Vector3f z = new Vector3f(x).cross(y);
        Matrix4f frame = new Matrix4f(
                x.x, x.y, x.z, 0,
                y.x, y.y, y.z, 0,
                z.x, z.y, z.z, 0,
                0, 0, 0, 1);
        pose.pushPose();
        pose.translate((float) from.x, (float) from.y, (float) from.z);
        pose.mulPose(frame);
        // A hair longer than it is, so that two pieces meeting at an angle leave no gap.
        pose.scale((float) length * 1.03f, 1.0f, 1.0f);
        pose.translate(0.0f, -0.5f, -0.5f);
        collector.submitMovingBlock(pose, strand, 0);
        pose.popPose();
    }
}
