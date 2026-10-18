package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.smithing.ForgingIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A piece in work is drawn in every slot as the shape it has been beaten into; see {@link ForgingIcon}. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ForgingIconMixin {
    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"), cancellable = true)
    private void hardwrought$drawPieceInWork(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed,
                                            CallbackInfo callback) {
        if (ForgingIcon.draw((GuiGraphicsExtractor) (Object) this, stack, x, y)) callback.cancel();
    }
}
