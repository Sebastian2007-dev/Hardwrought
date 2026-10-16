package de.ipnats.hardwrought.client.underworld;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.underworld.GraveUndead;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/** The Graveyard's undead drawn with vanilla's zombie and skeleton, in skins of their own: rotted, soil-stained. */
public final class GraveUndeadClient {
    private static final Identifier WALKER = Hardwrought.id("textures/entity/grave_walker.png");
    private static final Identifier SKELETON = Hardwrought.id("textures/entity/grave_skeleton.png");

    private GraveUndeadClient() { }

    public static void initialize() {
        EntityRendererRegistry.register(GraveUndead.GRAVE_WALKER, WalkerRenderer::new);
        EntityRendererRegistry.register(GraveUndead.GRAVE_SKELETON, SkeletonSkin::new);
    }

    static final class WalkerRenderer extends ZombieRenderer {
        WalkerRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public Identifier getTextureLocation(ZombieRenderState state) {
            return WALKER;
        }
    }

    static final class SkeletonSkin extends SkeletonRenderer {
        SkeletonSkin(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public Identifier getTextureLocation(SkeletonRenderState state) {
            return SKELETON;
        }
    }
}
