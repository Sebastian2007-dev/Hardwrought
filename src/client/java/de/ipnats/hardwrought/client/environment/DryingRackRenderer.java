package de.ipnats.hardwrought.client.environment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.ipnats.hardwrought.progression.DryingRackBlock;
import de.ipnats.hardwrought.progression.DryingRackBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** What hangs on a drying rack: up to four pieces side by side under the crossbar. */
public class DryingRackRenderer implements BlockEntityRenderer<DryingRackBlockEntity, DryingRackRenderer.State> {
    private final ItemModelResolver resolver;

    public static class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<Integer> slots = new ArrayList<>();
        float yaw;
    }

    public DryingRackRenderer(BlockEntityRendererProvider.Context context) {
        this.resolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DryingRackBlockEntity rack, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(rack, state, partialTick, camera, crumbling);
        state.items.clear();
        state.slots.clear();
        Direction facing = rack.getBlockState().getValue(DryingRackBlock.FACING);
        state.yaw = -facing.toYRot();
        int seed = (int) rack.getBlockPos().asLong();
        for (int slot = 0; slot < DryingRackBlockEntity.SLOTS; slot++) {
            ItemStack stack = rack.items().get(slot);
            if (stack.isEmpty()) continue;
            ItemStackRenderState item = new ItemStackRenderState();
            resolver.updateForTopItem(item, stack, ItemDisplayContext.FIXED, rack.getLevel(), null, seed + slot);
            state.items.add(item);
            state.slots.add(slot);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.items.size(); i++) {
            int slot = state.slots.get(i);
            pose.pushPose();
            pose.translate(0.5f, 0.0f, 0.5f);
            pose.rotateDegrees(Axis.YP, state.yaw);
            pose.translate(-0.33f + slot * 0.22f, 0.62f, 0.0f);
            pose.scale(0.4f, 0.4f, 0.4f);
            state.items.get(i).submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
