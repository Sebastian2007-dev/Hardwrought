package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.progression.RecipeSelectionMenu;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stores and synchronizes the selected result for both inventory and workbench crafting. */
@Mixin(AbstractCraftingMenu.class)
public abstract class AbstractCraftingMenuMixin extends RecipeBookMenu implements RecipeSelectionMenu {
    @Shadow @Final protected CraftingContainer craftSlots;
    @Shadow @Final protected ResultContainer resultSlots;
    @Shadow protected abstract Player owner();

    @Unique private DataSlot hardwrought$choiceCount;
    @Unique private DataSlot hardwrought$choiceIndex;
    @Unique private ResourceKey<Recipe<?>> hardwrought$selectedRecipe;

    protected AbstractCraftingMenuMixin(MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void hardwrought$addRecipeChoiceData(MenuType<?> type, int containerId, int width,
                                                 int height, CallbackInfo info) {
        hardwrought$choiceCount = DataSlot.standalone();
        hardwrought$choiceIndex = DataSlot.standalone();
        addDataSlot(hardwrought$choiceCount);
        addDataSlot(hardwrought$choiceIndex);
    }

    @Override
    public void hardwrought$refreshRecipeChoices(ServerLevel level, ServerPlayer player,
                                                  CraftingContainer input,
                                                  ResultContainer result) {
        List<Choice> choices = hardwrought$matchingChoices(level, player, input.asCraftInput());
        hardwrought$choiceCount.set(choices.size());
        if (choices.isEmpty()) {
            hardwrought$choiceIndex.set(0);
            hardwrought$selectedRecipe = null;
            return;
        }

        int selected = hardwrought$indexOf(choices, hardwrought$selectedRecipe);
        if (selected < 0 && result.getRecipeUsed() != null) {
            selected = hardwrought$indexOf(choices, result.getRecipeUsed().id());
        }
        if (selected < 0) selected = 0;
        hardwrought$applyChoice(player, result, choices, selected);
    }

    @Override
    public boolean hardwrought$nextRecipe(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        List<Choice> choices = hardwrought$matchingChoices(level, player, craftSlots.asCraftInput());
        hardwrought$choiceCount.set(choices.size());
        if (choices.size() < 2) return false;

        int selected = hardwrought$indexOf(choices, hardwrought$selectedRecipe);
        if (selected < 0 && resultSlots.getRecipeUsed() != null) {
            selected = hardwrought$indexOf(choices, resultSlots.getRecipeUsed().id());
        }
        hardwrought$applyChoice(player, resultSlots, choices, Math.floorMod(selected + 1, choices.size()));
        broadcastChanges();
        return true;
    }

    @Override
    public int hardwrought$recipeChoiceCount() {
        return hardwrought$choiceCount == null ? 0 : hardwrought$choiceCount.get();
    }

    @Unique
    private List<Choice> hardwrought$matchingChoices(ServerLevel level, ServerPlayer player,
                                                      CraftingInput input) {
        if (input.isEmpty()) return List.of();
        RecipeManager manager = level.getServer().getRecipeManager();
        var map = ((RecipeManagerAccessor) manager).hardwrought$recipeMap();
        List<Choice> choices = new ArrayList<>();
        map.getRecipesFor(RecipeType.CRAFTING, input, level)
                .sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
                .forEach(holder -> hardwrought$addChoice(choices, holder, input, level, player));
        return choices;
    }

    @Unique
    private static void hardwrought$addChoice(List<Choice> choices,
                                               RecipeHolder<CraftingRecipe> holder,
                                               CraftingInput input, ServerLevel level,
                                               ServerPlayer player) {
        CraftingRecipe recipe = holder.value();
        if (!hardwrought$isAvailable(player, holder)) return;
        ItemStack output = recipe.assemble(input);
        if (output.isEmpty() || !output.isItemEnabled(level.enabledFeatures())) return;
        boolean alreadyShown = choices.stream().anyMatch(choice ->
                choice.output.getCount() == output.getCount()
                        && ItemStack.isSameItemSameComponents(choice.output, output));
        if (!alreadyShown) choices.add(new Choice(holder, output));
    }

    @Unique
    private static boolean hardwrought$isAvailable(ServerPlayer player,
                                                    RecipeHolder<CraftingRecipe> holder) {
        return holder.value().isSpecial()
                || !player.level().getGameRules().get(GameRules.LIMITED_CRAFTING)
                || player.getRecipeBook().contains(holder.id());
    }

    @Unique
    private static int hardwrought$indexOf(List<Choice> choices, ResourceKey<Recipe<?>> recipe) {
        if (recipe == null) return -1;
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).recipe.id().equals(recipe)) return index;
        }
        return -1;
    }

    @Unique
    private void hardwrought$applyChoice(ServerPlayer player, ResultContainer result,
                                         List<Choice> choices, int index) {
        Choice choice = choices.get(index);
        hardwrought$selectedRecipe = choice.recipe.id();
        hardwrought$choiceIndex.set(index);
        if (!result.setRecipeUsed(player, choice.recipe)) return;
        ItemStack output = choice.output.copy();
        result.setItem(0, output);
        setRemoteSlot(0, output);
        player.connection.send(new ClientboundContainerSetSlotPacket(
                containerId, incrementStateId(), 0, output));
    }

    @Unique
    private record Choice(RecipeHolder<CraftingRecipe> recipe, ItemStack output) { }
}
