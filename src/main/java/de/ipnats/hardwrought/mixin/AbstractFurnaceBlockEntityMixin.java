package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.ipnats.hardwrought.metallurgy.Smelting;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A furnace only smelts what it can get hot enough to melt.
 *
 * <p>Hooked on the one question vanilla asks before it burns fuel or advances the cooking: can this
 * input be smelted into that output slot. Answering no there leaves the ore lying in the furnace,
 * burns nothing for it and makes no progress — exactly what a furnace too cold for its load does.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;canBurn(Lnet/minecraft/core/NonNullList;ILnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean hardwrought$onlyWhatItCanMelt(NonNullList<ItemStack> items, int maxStackSize,
                                                         ItemStack result, Operation<Boolean> original,
                                                         @Local(argsOnly = true) ServerLevel level,
                                                         @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace) {
        if (!Smelting.hotEnough(level, furnace, items.get(0))) return false;
        return original.call(items, maxStackSize, result);
    }

    /**
     * A piece of forge metal with no smelting recipe of its own is heated instead: the furnace is
     * handed a recipe whose result is the piece itself. See {@code Heating}.
     */
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager$CachedCheck;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/server/level/ServerLevel;)Ljava/util/Optional;"))
    private static java.util.Optional<?> hardwrought$heatForgeMetal(
            net.minecraft.world.item.crafting.RecipeManager.CachedCheck<?, ?> check,
            net.minecraft.world.item.crafting.RecipeInput input, ServerLevel level,
            Operation<java.util.Optional<?>> original) {
        java.util.Optional<?> found = original.call(check, input, level);
        if (found.isPresent()) return found;
        var heating = de.ipnats.hardwrought.smithing.Heating.recipeFor(input.getItem(0));
        return heating == null ? found : java.util.Optional.of(heating);
    }

    /** The heated piece is the piece that went in — progress and quality intact — with heat on it. */
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack hardwrought$putHeatOn(net.minecraft.world.item.crafting.AbstractCookingRecipe recipe,
                                                  net.minecraft.world.item.crafting.SingleRecipeInput input,
                                                  Operation<ItemStack> original,
                                                  @Local(argsOnly = true) ServerLevel level,
                                                  @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace) {
        if (recipe instanceof de.ipnats.hardwrought.smithing.Heating.HeatingRecipe) {
            return de.ipnats.hardwrought.smithing.Heating.heated(level, furnace, input.item());
        }
        return original.call(recipe, input);
    }

    /**
     * Heating moves the complete load in one pass. Vanilla's burn routine already inserts the full
     * assembled result, but normally shrinks the input by only one; clear the remainder only for the
     * heated same-item result. Real smelting recipes still use Vanilla's one-item consumption.
     */
    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;burn(Lnet/minecraft/core/NonNullList;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"))
    private static void hardwrought$consumeWholeHeatedLoad(NonNullList<ItemStack> items, ItemStack input,
                                                            ItemStack result, Operation<Void> original) {
        boolean heating = result.is(input.getItem())
                && result.has(de.ipnats.hardwrought.core.registry.ModDataComponents.HEAT)
                && result.getCount() == input.getCount();
        original.call(items, input, result);
        if (heating) input.setCount(0);
    }
}
