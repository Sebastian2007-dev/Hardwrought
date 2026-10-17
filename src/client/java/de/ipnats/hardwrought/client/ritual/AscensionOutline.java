package de.ipnats.hardwrought.client.ritual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The golden outline round a player rising at the end of the ritual (see {@link RitualCutscene}): not the
 * glowing effect's flat line, but a rim of shimmering gold hugging the body as it moves.
 *
 * <p>It is the player's own model drawn again behind them: pushed back away from the camera, so the body
 * in front hides all of it but the edge, and made larger by just as much as being further off makes it
 * smaller, and a little more — that little more is the rim that shows round the silhouette. A thin bright
 * shell and a wide faint one over it, both shimmering.
 */
public final class AscensionOutline {
    public static final RenderStateDataKey<Float> STRENGTH = RenderStateDataKey.create(() -> "hardwrought:ascension_outline");
    private static final Identifier GOLD = Identifier.fromNamespaceAndPath("hardwrought", "textures/entity/ritual/ascension_gold.png");
    /** How far behind the player the shells are drawn: more than the body is deep. */
    private static final float BEHIND = 0.75f;
    /** Where in the model the middle of the body is: the shells grow round it. */
    private static final float MIDDLE = 0.6f;

    private AscensionOutline() { }

    public static void initialize() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new Layer(avatar));
        });
    }

    /** Carries how strong the outline is into the render state. */
    public static void extract(Object entity, AvatarRenderState state, float partialTick) {
        float strength = entity instanceof Player player ? RitualCutscene.outline(player.getUUID(), partialTick) : 0;
        ((FabricRenderState) state).setData(STRENGTH, strength > 0.001f ? strength : null);
    }

    static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
        Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
            Float strength = ((FabricRenderState) state).getData(STRENGTH);
            if (strength == null || state.isInvisible) return;
            Vector3f middle = pose.last().pose().transformPosition(0, MIDDLE, 0, new Vector3f());
            float distance = middle.length();
            if (distance < 0.5f) return;
            Vector3f away = new Vector3f(middle).div(distance).mul(BEHIND);
            float t = state.ageInTicks;
            shell(pose, collector, state, light, away, (distance + BEHIND) / distance * (1 + 0.045f * Math.min(1.8f, strength)),
                    Math.min(1, strength), t * 0.012f, t * 0.008f);
            shell(pose, collector, state, light, away, (distance + BEHIND) / distance * (1 + 0.12f * Math.min(1.8f, strength)),
                    0.4f * Math.min(1, strength), -t * 0.009f, t * 0.011f);
        }

        private void shell(PoseStack pose, SubmitNodeCollector collector, AvatarRenderState state, int light, Vector3f away, float scale,
                           float brightness, float u, float v) {
            pose.pushPose();
            Matrix4f matrix = pose.last().pose();
            matrix.translateLocal(away.x, away.y, away.z);
            pose.translate(0, MIDDLE, 0);
            pose.scale(scale, scale, scale);
            pose.translate(0, -MIDDLE, 0);
            // Laid over what is behind, not added to it: added over a bright sky, gold washes out to white. It
            // shimmers by its alpha, the drift standing for the time.
            float b = Math.clamp(brightness * (0.85f + 0.15f * (float) Math.sin((u + v) * 40)), 0, 1);
            int color = (int) (255 * b) << 24 | 0xFFFFFF;
            collector.order(1).submitModel(getParentModel(), state, pose, RenderTypes.entityTranslucentEmissive(GOLD), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
            pose.popPose();
        }
    }
}
