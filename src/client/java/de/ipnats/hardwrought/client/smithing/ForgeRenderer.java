package de.ipnats.hardwrought.client.smithing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.ipnats.hardwrought.smithing.ForgeBlock;
import de.ipnats.hardwrought.smithing.ForgeBlockEntity;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The pieces lying in the coals of a forge, flat. A forge on its own shows its one piece in the
 * middle of the bed; a joined forge keeps all its pieces in the controller at the north-west corner,
 * and that block draws them one to each block of the hearth, so they lie spread across the whole bed.
 */
public class ForgeRenderer implements BlockEntityRenderer<ForgeBlockEntity, ForgeRenderer.State> {
    private final ItemModelResolver resolver;

    /** One item and where it lies, relative to the block being drawn. */
    public static class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<float[]> places = new ArrayList<>();
    }

    public ForgeRenderer(BlockEntityRendererProvider.Context context) {
        this.resolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void extractRenderState(ForgeBlockEntity forge, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(forge, state, partialTick, camera, crumbling);
        state.items.clear();
        state.places.clear();
        int layout = forge.layout();
        if (layout != 0 && !forge.isController()) return;
        int side = layout == 2 ? 3 : layout == 1 ? 2 : 1;
        int seed = (int) forge.getBlockPos().asLong();
        // The pieces lie on the coal, however high it is heaped; in an empty pit, on the ash.
        var block = forge.getBlockState();
        int fuel = block.hasProperty(ForgeBlock.FUEL) ? block.getValue(ForgeBlock.FUEL) : 0;
        float bed = (fuel == 0 ? 6 : 6 + 2 * fuel) / 16.0f + 0.03f;
        for (int i = 0; i < ForgeBlockEntity.metalSlots(layout); i++) {
            ItemStack stack = forge.items().get(ForgeBlockEntity.FIRST_METAL + i);
            if (stack.isEmpty()) continue;
            ItemStackRenderState item = new ItemStackRenderState();
            resolver.updateForTopItem(item, stack, ItemDisplayContext.FIXED, forge.getLevel(), null, seed + i);
            state.items.add(item);
            state.places.add(new float[] {i % side + 0.5f, bed, i / side + 0.5f, i * 90.0f});
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.items.size(); i++) {
            float[] place = state.places.get(i);
            pose.pushPose();
            pose.translate(place[0], place[1], place[2]);
            pose.rotateDegrees(Axis.XP, 90.0f);
            pose.rotateDegrees(Axis.ZP, place[3]);
            pose.scale(0.45f, 0.45f, 0.45f);
            // Full brightness: the pieces lie in a bed of glowing coal.
            state.items.get(i).submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
