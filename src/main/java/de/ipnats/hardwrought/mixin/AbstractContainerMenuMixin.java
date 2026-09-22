package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.progression.RecipeSelectionMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a workbench that is not vanilla's crafting table keep its own menu open.
 *
 * <p>An open menu is re-checked every tick against the block it was opened on, and the crafting
 * menu asks for {@code minecraft:crafting_table} by identity. A hewn workbench is a different block,
 * so the grid opened and shut again in the same breath — which looks exactly like a broken click.
 *
 * <p>The check is widened rather than replaced: any block that <em>is</em> a crafting table, this
 * mod's and any other, satisfies a menu that asked for one. The reach test stays vanilla's, so
 * walking away still closes the grid.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    /** The same reach vanilla checks an open menu against, so walking away still closes it. */
    private static final double VANILLA_REACH = 4.0;

    @Inject(method = "stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;"
            + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void hardwrought$acceptOtherWorkbenches(ContainerLevelAccess access, Player player,
                                                           Block block,
                                                           CallbackInfoReturnable<Boolean> info) {
        if (block != Blocks.CRAFTING_TABLE) return;
        boolean workbench = access.evaluate((level, pos) ->
                level.getBlockState(pos).getBlock() instanceof CraftingTableBlock
                        && level.getBlockState(pos).getBlock() != Blocks.CRAFTING_TABLE
                        && player.isWithinBlockInteractionRange(pos, VANILLA_REACH), false);
        if (workbench) info.setReturnValue(true);
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void hardwrought$selectAnotherRecipe(Player player, int button,
                                                  CallbackInfoReturnable<Boolean> info) {
        if (button != RecipeSelectionMenu.NEXT_RECIPE_BUTTON
                || !((Object) this instanceof RecipeSelectionMenu selection)
                || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        info.setReturnValue(selection.hardwrought$nextRecipe(serverPlayer));
    }
}
