package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import de.ipnats.hardwrought.smithing.ForgeQuality;
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
                && (stack.has(DataComponents.FOOD) || stack.has(DataComponents.POTION_CONTENTS)
                || stack.is(net.minecraft.world.item.Items.MILK_BUCKET))) {
            var runtime = CoreLifecycle.find(player.level().getServer());
            if (runtime != null) {
                runtime.survival().consumeFood(player, stack);
                // Section 82: eating a thing is one of the ways of finding out what it is.
                runtime.knowledge().study(player, stack.getItem());
            }
        }
    }

    /** Sections 37 and 38: a forged tool digs as fast as it was well made. Unforged tools are untouched. */
    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    private float hardwrought$forgedSpeed(float speed) {
        double factor = ForgeQuality.speedFactor((ItemStack) (Object) this);
        return factor == 1.0 || speed <= 1.0f ? speed : (float) (speed * factor);
    }

    /** And lasts as long as it was well made and well treated. */
    @ModifyReturnValue(method = "getMaxDamage", at = @At("RETURN"))
    private int hardwrought$forgedDurability(int durability) {
        double factor = ForgeQuality.durabilityFactor((ItemStack) (Object) this);
        return factor == 1.0 || durability <= 0 ? durability : Math.max(1, (int) Math.round(durability * factor));
    }
}
