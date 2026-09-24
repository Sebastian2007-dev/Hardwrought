package de.ipnats.hardwrought.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.ipnats.hardwrought.survival.Ultra;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Ultra switch, right under the game mode when a world is made: Survival with it on is Ultra
 * Survival, Hardcore with it on is Ultra Hardcore. It writes the world's {@link Ultra#RULE} game rule,
 * so the choice is saved with the world and read back from it. Off for a creative world, which has
 * nothing to earn. While it is on, the difficulty is Hard and its button is greyed out.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldGameTabMixin {
    @Unique private CycleButton<Boolean> hardwrought$ultra;
    @Unique private AbstractWidget hardwrought$difficulty;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;Lnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement hardwrought$ultraBelowGameMode(GridLayout.RowHelper rows, LayoutElement gameMode,
                                                         LayoutSettings settings, Operation<LayoutElement> original,
                                                         @Local(argsOnly = true) CreateWorldScreen screen) {
        LayoutElement added = original.call(rows, gameMode, settings);
        WorldCreationUiState state = screen.getUiState();
        hardwrought$ultra = CycleButton.onOffBuilder(state.getGameRules().get(Ultra.RULE))
                .withTooltip(value -> Tooltip.create(Component.translatable("createWorld.hardwrought.ultra.info")))
                .create(0, 0, 210, 20, Component.translatable("createWorld.hardwrought.ultra"),
                        (button, value) -> {
                            state.getGameRules().set(Ultra.RULE, value, null);
                            if (value) state.setDifficulty(Difficulty.HARD);
                            state.onChanged();
                        });
        rows.addChild(hardwrought$ultra, settings);
        return added;
    }

    /** The difficulty button, the next one vanilla adds, kept so it can be greyed out. */
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", ordinal = 2,
            target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;Lnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement hardwrought$rememberDifficulty(GridLayout.RowHelper rows, LayoutElement difficulty,
                                                         LayoutSettings settings, Operation<LayoutElement> original) {
        if (difficulty instanceof AbstractWidget widget) hardwrought$difficulty = widget;
        return original.call(rows, difficulty, settings);
    }

    /**
     * Registered last, so it has the last word after vanilla's own listeners have set the buttons:
     * Ultra means Hard, and a greyed-out difficulty; a creative world cannot be Ultra.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void hardwrought$ultraRules(CreateWorldScreen screen, CallbackInfo info) {
        WorldCreationUiState state = screen.getUiState();
        state.addListener(this::hardwrought$apply);
        hardwrought$apply(state);
    }

    @Unique
    private void hardwrought$apply(WorldCreationUiState state) {
        if (hardwrought$ultra == null) return;
        boolean creative = state.getGameMode() == WorldCreationUiState.SelectedGameMode.CREATIVE;
        hardwrought$ultra.active = !creative;
        if (creative && hardwrought$ultra.getValue()) {
            hardwrought$ultra.setValue(false);
            state.getGameRules().set(Ultra.RULE, false, null);
        }
        boolean ultra = hardwrought$ultra.getValue();
        if (ultra && state.getDifficulty() != Difficulty.HARD) state.setDifficulty(Difficulty.HARD);
        if (hardwrought$difficulty != null) {
            // Hardcore greys it out already; Ultra does too, and leaves it alone otherwise.
            if (ultra) hardwrought$difficulty.active = false;
            else if (!state.isHardcore()) hardwrought$difficulty.active = true;
        }
    }
}
