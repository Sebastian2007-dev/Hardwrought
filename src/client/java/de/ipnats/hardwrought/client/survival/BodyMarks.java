package de.ipnats.hardwrought.client.survival;

import com.mojang.blaze3d.vertex.PoseStack;
import de.ipnats.hardwrought.health.Appearance;
import de.ipnats.hardwrought.survival.Overeating;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * What can be seen of a player's body: a wound, a bandage over it, a sick face, and a full belly.
 *
 * <p>The marks are drawn over the player's own skin, the way armour is, from textures laid out like a
 * skin: a strip of bandage round the right arm, a red gash on the left, shadows under the eyes and sweat
 * on a sick player's brow. Each is painted on both layers of the skin, the body and the sleeve, jacket or
 * hat over it, so a skin's outer layer cannot hide it. A stuffed player has a belly
 * (see {@code PlayerModelBellyMixin}). Everyone
 * sees it, of everyone: the server sends what shows ({@link Appearance}, {@link Overeating#STUFFED}).
 */
public final class BodyMarks {
    /** What is drawn on one player this frame, carried from the player to the layer by its render state. */
    public record Look(Appearance appearance, float fullness) { }

    public static final RenderStateDataKey<Look> LOOK = RenderStateDataKey.create(() -> "hardwrought:body_look");

    /** How much deeper the torso of a player as full as a body gets is drawn: the belly. */
    public static final float DEEPEST = 0.8f;
    /** And how much wider. */
    public static final float WIDEST = 0.15f;

    private static final Identifier BANDAGE = texture("bandage");
    private static final Identifier WOUND = texture("wound");
    private static final Identifier SICK = texture("sick");

    private BodyMarks() { }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("hardwrought", "textures/entity/body/" + name + ".png");
    }

    public static void initialize() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new Layer(avatar));
        });
    }

    /** What the render state of this player should carry. */
    public static void extract(Object entity, AvatarRenderState state) {
        if (!(entity instanceof Player player)) {
            ((FabricRenderState) state).setData(LOOK, null);
            return;
        }
        ((FabricRenderState) state).setData(LOOK, new Look(Appearance.of(player), (float) Overeating.fullness(player)));
    }

    public static Look look(AvatarRenderState state) {
        return ((FabricRenderState) state).getData(LOOK);
    }

    /** The marks over the skin. */
    static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
        Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
            Look look = look(state);
            if (look == null || state.isInvisible) return;
            Appearance shown = look.appearance();
            if (shown.sick()) coloredCutoutModelCopyLayerRender(getParentModel(), SICK, pose, collector, light, state, -1, 1);
            if (shown.wound() && !shown.bandaged()) coloredCutoutModelCopyLayerRender(getParentModel(), WOUND, pose, collector, light, state, -1, 1);
            if (shown.bandaged()) coloredCutoutModelCopyLayerRender(getParentModel(), BANDAGE, pose, collector, light, state, -1, 1);
        }
    }
}
