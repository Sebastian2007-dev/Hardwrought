package de.ipnats.hardwrought.client.ritual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.ipnats.hardwrought.ritual.RitualPedestal;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What hangs over a ritual pedestal: the offering it wants as a ghost, pale and breathing, until it is laid
 * there; then the offering itself, whole and lit, slowly turning.
 */
public class RitualPedestalRenderer implements BlockEntityRenderer<RitualPedestal.Entity, RitualPedestalRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0;
    /** The ghost's tint, without its alpha. */
    private static final int GHOST = 0xE6F2FF;

    private final ItemModelResolver resolver;

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean shown, ghost;
        float time;
    }

    public RitualPedestalRenderer(BlockEntityRendererProvider.Context context) {
        this.resolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RitualPedestal.Entity pedestal, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(pedestal, state, partialTick, camera, crumbling);
        ItemStack held = pedestal.held(), wanted = pedestal.wanted();
        state.ghost = held.isEmpty();
        ItemStack shown = state.ghost ? wanted : held;
        state.shown = !shown.isEmpty();
        state.item.clear();
        if (state.shown) {
            resolver.updateForTopItem(state.item, shown, ItemDisplayContext.FIXED, pedestal.getLevel(), null, (int) pedestal.getBlockPos().asLong());
        }
        // Each pedestal a little out of step with the others.
        state.time = (pedestal.getLevel() == null ? 0 : pedestal.getLevel().getGameTime()) + partialTick + (pedestal.getBlockPos().asLong() & 63) * 7;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.shown || state.item.isEmpty()) return;
        float t = state.time;
        pose.pushPose();
        pose.translate(0.5f, 1.45f + (float) Math.sin(t * 0.05f) * 0.06f, 0.5f);
        if (state.ghost) {
            pose.rotateDegrees(Axis.YP, t * 0.8f);
            pose.scale(0.75f, 0.75f, 0.75f);
            // Breathing: now fainter, now clearer, never solid.
            float alpha = 0.5f + 0.15f * (float) Math.sin(t * 0.08f);
            GhostItem.submit(state.item, pose, collector, ((int) (alpha * 255) << 24) | GHOST);
        } else {
            pose.rotateDegrees(Axis.YP, t * 1.6f);
            pose.scale(0.9f, 0.9f, 0.9f);
            state.item.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }
        pose.popPose();
    }
}
