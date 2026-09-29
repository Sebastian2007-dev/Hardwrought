package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.smithing.Rust;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/** Rusty steel wears faster; see {@link Rust#wear}. Every other way of wearing an item ends up here. */
@Mixin(ItemStack.class)
public abstract class ItemStackRustMixin {
    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int hardwrought$rustWear(int amount, int original, ServerLevel level, ServerPlayer player, Consumer<ItemStack> onBreak) {
        return Rust.wear((ItemStack) (Object) this, amount, level.getRandom());
    }
}
