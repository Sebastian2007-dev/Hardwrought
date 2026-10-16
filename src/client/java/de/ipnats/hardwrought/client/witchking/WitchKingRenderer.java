package de.ipnats.hardwrought.client.witchking;

import com.mojang.blaze3d.vertex.PoseStack;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.witchking.WitchKing;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the Witch King: his model in his robes, and over it a layer that glows in the dark — his eyes,
 * the gems of his crown and sceptre, the runes stitched into his robe, the shards circling him. His size
 * in his last phase comes from his scale, which every living thing's renderer already applies.
 */
public class WitchKingRenderer extends MobRenderer<WitchKing, WitchKingRenderState, WitchKingModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Hardwrought.id("witch_king"), "main");
    private static final Identifier TEXTURE = Hardwrought.id("textures/entity/witch_king.png");
    private static final Identifier GLOW = Hardwrought.id("textures/entity/witch_king_glow.png");
    private static final RenderType GLOW_TYPE = RenderTypes.eyes(GLOW);

    public WitchKingRenderer(EntityRendererProvider.Context context) {
        super(context, new WitchKingModel(context.bakeLayer(LAYER)), 0.6f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW_TYPE;
            }
        });
    }

    @Override
    public WitchKingRenderState createRenderState() {
        return new WitchKingRenderState();
    }

    @Override
    public void extractRenderState(WitchKing king, WitchKingRenderState state, float partialTicks) {
        super.extractRenderState(king, state, partialTicks);
        // Up at once, and down over the last few ticks of the cast.
        state.cast = Mth.clamp((king.casting() - partialTicks) / 5f, 0, 1);
        state.speed = (float) king.getDeltaMovement().horizontalDistance();
        state.phase = king.phase();
        state.dying = king.isDeadOrDying();
    }

    @Override
    protected void scale(WitchKingRenderState state, PoseStack pose) {
        super.scale(state, pose);
        pose.scale(1.05f, 1.05f, 1.05f);
    }

    @Override
    public Identifier getTextureLocation(WitchKingRenderState state) {
        return TEXTURE;
    }

    /** No death tumble: he dies upright, rising; the effects show the rest. */
    @Override
    protected float getFlipDegrees() {
        return 0;
    }
}
