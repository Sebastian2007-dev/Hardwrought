package de.ipnats.hardwrought.client.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;

/**
 * Hardcore means one life. Dying in a hardcore world played on this machine leaves exactly one way
 * on: the world is deleted. No spectating what one has lost, no leaving it for later.
 *
 * <p>Only for a world this client runs itself; on a server there is nothing here that could be
 * deleted, and vanilla's own screen stays.
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin extends Screen {
    @Shadow @Final private boolean hardcore;
    @Shadow @Final private List<Button> exitButtons;
    @Shadow private Button exitToTitleButton;

    protected DeathScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void hardwrought$onlyDeleteWorld(CallbackInfo info) {
        if (!hardcore || minecraft == null || !minecraft.isLocalServer() || minecraft.getSingleplayerServer() == null) return;
        for (Button button : exitButtons) removeWidget(button);
        exitButtons.clear();
        Button delete = Button.builder(Component.translatable("deathScreen.hardwrought.delete_world"),
                        button -> hardwrought$deleteWorld())
                .bounds(width / 2 - 100, height / 4 + 72, 200, 20)
                .build();
        addRenderableWidget(delete);
        exitButtons.add(delete);
        exitToTitleButton = delete;
        // Vanilla holds its buttons back for a moment so a click meant for the game does not land on
        // them; this one waits the same way.
        delete.active = false;
    }

    @Unique
    private void hardwrought$deleteWorld() {
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (server == null) return;
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String id = root.getFileName().toString();
        client.disconnectWithProgressScreen();
        try (var access = client.getLevelSource().createAccess(id)) {
            access.deleteLevel();
        } catch (Exception failure) {
            LogUtils.getLogger().error("Could not delete hardcore world {}", id, failure);
        }
        client.gui.setScreen(new TitleScreen());
    }
}
