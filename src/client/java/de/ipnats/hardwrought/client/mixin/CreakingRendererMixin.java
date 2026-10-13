package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.CreakingWoodState;
import de.ipnats.hardwrought.mobs.HeartWood;
import de.ipnats.hardwrought.mobs.TreeHearts;
import net.minecraft.client.renderer.entity.CreakingRenderer;
import net.minecraft.client.renderer.entity.state.CreakingRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.creaking.Creaking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.Map;

/** A creaking called by a tree heart wears that tree's bark. See {@link TreeHearts}. */
@Mixin(CreakingRenderer.class)
public abstract class CreakingRendererMixin {
    @Unique
    private static final Map<HeartWood, Identifier> HARDWROUGHT$TEXTURES = new EnumMap<>(HeartWood.class);

    static {
        for (HeartWood wood : HeartWood.values()) {
            HARDWROUGHT$TEXTURES.put(wood, Hardwrought.id("textures/entity/creaking/" + wood.getSerializedName() + ".png"));
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/creaking/Creaking;Lnet/minecraft/client/renderer/entity/state/CreakingRenderState;F)V",
            at = @At("TAIL"))
    private void hardwrought$extractWood(Creaking creaking, CreakingRenderState state, float partialTicks, CallbackInfo callback) {
        ((CreakingWoodState) state).hardwrought$setWood(creaking.getAttached(TreeHearts.CREAKING_WOOD));
    }

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/CreakingRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void hardwrought$barkTexture(CreakingRenderState state, CallbackInfoReturnable<Identifier> callback) {
        HeartWood wood = ((CreakingWoodState) state).hardwrought$wood();
        if (wood != null) callback.setReturnValue(HARDWROUGHT$TEXTURES.get(wood));
    }
}
