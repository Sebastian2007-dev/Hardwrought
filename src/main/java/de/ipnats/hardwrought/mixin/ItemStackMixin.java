package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Credits nutrition only when vanilla has reached the successful consume operation. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void hardwrought$consume(Level level, LivingEntity entity,
                                     CallbackInfoReturnable<ItemStack> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!level.isClientSide() && entity instanceof ServerPlayer player
                && (stack.has(DataComponents.FOOD) || stack.has(DataComponents.POTION_CONTENTS))) {
            var runtime = CoreLifecycle.find(player.level().getServer());
            if (runtime != null) runtime.survival().consumeFood(player, stack);
        }
    }
}
