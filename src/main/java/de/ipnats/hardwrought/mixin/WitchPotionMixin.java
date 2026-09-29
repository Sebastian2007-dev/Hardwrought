package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.ipnats.hardwrought.mobs.WitchTactics;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob specification section 16: the witch picks its potion for the target; see {@link WitchTactics}. */
@Mixin(Witch.class)
public abstract class WitchPotionMixin {
    @WrapOperation(method = "performRangedAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/alchemy/PotionContents;createItemStack(Lnet/minecraft/world/item/Item;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack hardwrought$choose(Item item, Holder<Potion> potion, Operation<ItemStack> original,
                                         @Local(argsOnly = true) LivingEntity target) {
        return original.call(item, WitchTactics.choose(potion, target));
    }
}
