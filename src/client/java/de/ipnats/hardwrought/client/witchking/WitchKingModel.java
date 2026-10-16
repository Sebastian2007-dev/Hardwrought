package de.ipnats.hardwrought.client.witchking;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The Witch King: a gaunt pale face deep in a hood, a crown of gold spikes over it, spiked pauldrons, a
 * robe that ends in a tattered hem above nothing — he has no legs to stand on, he floats — a long cape
 * stirring behind him, a sceptre with a soul gem, and six shards of crystal circling him. The texture
 * layout (128 by 128) is drawn by tools to match the boxes here.
 */
public class WitchKingModel extends EntityModel<WitchKingRenderState> {
    private final ModelPart body, head, cape, robe, hem, rightArm, leftArm, sceptre;
    private final ModelPart[] shards = new ModelPart[6];

    public WitchKingModel(ModelPart root) {
        super(root.getChild("root"), RenderTypes::entityTranslucent);
        ModelPart model = root.getChild("root");
        body = model.getChild("body");
        head = model.getChild("head");
        cape = body.getChild("cape");
        robe = body.getChild("robe");
        hem = robe.getChild("hem");
        rightArm = body.getChild("right_arm");
        leftArm = body.getChild("left_arm");
        sceptre = rightArm.getChild("sceptre");
        for (int i = 0; i < shards.length; i++) shards[i] = model.getChild("shard_" + i);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, -4, 0));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8)
                .texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.7f)), PartPose.offset(0, 0, 0));
        // The crown: a band and its spikes, the tallest at the front with a gem.
        PartDefinition crown = head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(40, 44).addBox(-4.5f, -10.5f, -4.5f, 9, 2, 9),
                PartPose.ZERO);
        float[][] spikes = {{-4, -4}, {3, -4}, {-4, 3}, {3, 3}, {-0.5f, 3.5f}, {-4f, -0.5f}, {3.5f, -0.5f}};
        for (int i = 0; i < spikes.length; i++) {
            crown.addOrReplaceChild("spike_" + i, CubeListBuilder.create().texOffs(80, 40).addBox(spikes[i][0], -13.5f, spikes[i][1], 1, 3, 1), PartPose.ZERO);
        }
        crown.addOrReplaceChild("spike_front", CubeListBuilder.create().texOffs(84, 40).addBox(-0.5f, -15.5f, -4.5f, 1, 5, 1)
                .texOffs(88, 40).addBox(-0.5f, -12f, -5.2f, 1, 1, 1), PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4, 0, -2.5f, 8, 12, 5), PartPose.offset(0, 0, 0));
        PartDefinition robe = body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 34).addBox(-5, 0, -3.5f, 10, 14, 7), PartPose.offset(0, 11, 0));
        robe.addOrReplaceChild("hem", CubeListBuilder.create().texOffs(0, 56).addBox(-5.5f, 0, -4, 11, 4, 8), PartPose.offset(0, 14, 0));
        body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(64, 0).addBox(-6, 0, 0, 12, 24, 1), PartPose.offset(0, 0.5f, 2.7f));
        for (int side : new int[] {-1, 1}) {
            String name = side < 0 ? "right" : "left";
            PartDefinition pauldron = body.addOrReplaceChild(name + "_pauldron", CubeListBuilder.create().texOffs(96, side < 0 ? 0 : 12)
                    .addBox(side < 0 ? -5 : -1, -2.5f, -3.5f, 6, 4, 7), PartPose.offset(side * 5, 1, 0));
            for (int s = 0; s < 3; s++) {
                pauldron.addOrReplaceChild("spike_" + s, CubeListBuilder.create().texOffs(80, 46)
                        .addBox(side < 0 ? -3.5f + s * 1.6f - 1 : 0.5f + s * 1.6f, -5.5f, -0.5f, 1, 3, 1), PartPose.ZERO);
            }
        }
        PartDefinition right = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 10, 4)
                .texOffs(40, 32).addBox(-3.5f, 7, -2.5f, 5, 5, 5), PartPose.offset(-5, 2, 0));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(60, 26).addBox(-1, -2, -2, 4, 10, 4)
                .texOffs(76, 26).addBox(-1.5f, 7, -2.5f, 5, 5, 5), PartPose.offset(5, 2, 0));
        PartDefinition sceptre = right.addOrReplaceChild("sceptre", CubeListBuilder.create().texOffs(124, 24).addBox(-0.5f, -10, -0.5f, 1, 22, 1)
                .texOffs(100, 40).addBox(-1.5f, -13, -1.5f, 3, 3, 3), PartPose.offset(-1, 11, -1));
        for (int p = 0; p < 4; p++) {
            float px = p % 2 == 0 ? (p == 0 ? -2 : 1) : -0.5f, pz = p % 2 == 1 ? (p == 1 ? -2 : 1) : -0.5f;
            sceptre.addOrReplaceChild("prong_" + p, CubeListBuilder.create().texOffs(112, 40).addBox(px, -15, pz, 1, 4, 1), PartPose.ZERO);
        }
        for (int i = 0; i < 6; i++) {
            root.addOrReplaceChild("shard_" + i, CubeListBuilder.create().texOffs(116, 40).addBox(-0.5f, -1.5f, -0.5f, 1, 3, 1), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(WitchKingRenderState state) {
        super.setupAnim(state);
        float t = state.ageInTicks;
        // He floats: a slow rise and fall.
        root().y = -4 + Mth.sin(t * 0.08f) * 1.2f;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        // Arms: held a little out, drifting; raised high while he casts, the sceptre lifted.
        float cast = state.cast;
        float drift = Mth.sin(t * 0.06f) * 0.08f;
        rightArm.xRot = Mth.lerp(cast, -0.25f + drift, -2.7f);
        rightArm.zRot = Mth.lerp(cast, 0.15f, 0.35f);
        leftArm.xRot = Mth.lerp(cast, -0.1f - drift, -2.5f);
        leftArm.zRot = Mth.lerp(cast, -0.2f, -0.55f);
        sceptre.xRot = Mth.lerp(cast, 0.2f, -0.3f);
        // The robe and its hem sway; the cape stirs behind him, more when he moves.
        float wind = Mth.clamp(state.speed * 6, 0, 1);
        robe.xRot = Mth.sin(t * 0.07f) * 0.04f + wind * 0.25f;
        hem.xRot = Mth.sin(t * 0.09f + 1) * 0.06f + wind * 0.2f;
        cape.xRot = 0.12f + Mth.sin(t * 0.1f) * 0.06f + wind * 0.7f;
        cape.zRot = Mth.sin(t * 0.05f) * 0.03f;
        // The shards circle him, rising and falling, each turning on itself.
        for (int i = 0; i < shards.length; i++) {
            float a = t * 0.05f + i * Mth.TWO_PI / shards.length;
            shards[i].x = Mth.cos(a) * 13;
            shards[i].z = Mth.sin(a) * 13;
            shards[i].y = 6 + Mth.sin(t * 0.09f + i * 1.3f) * 4;
            shards[i].yRot = t * 0.1f + i;
            shards[i].zRot = 0.4f;
        }
    }
}
