package de.ipnats.hardwrought.client.survival;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModEffects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What tiredness looks and sounds like (see {@code Tiredness}): the eyes falling shut now and then — more
 * often and for longer the more tired — a microsleep's black, and, overtired, things heard behind that
 * are not there.
 */
public final class TirednessHud {
    /** By stage: about how often the eyes fall shut, and for how long, in ticks. */
    private static final int[] BLINK_EVERY = {600, 240, 100};
    private static final int[] BLINK_TICKS = {6, 10, 16};
    /** Overtired, the chance each tick of hearing something that is not there. */
    private static final int PHANTOM_ONE_IN = 500;
    private static final List<SoundEvent> PHANTOMS = List.of(SoundEvents.ZOMBIE_AMBIENT, SoundEvents.SKELETON_AMBIENT,
            SoundEvents.SPIDER_AMBIENT, SoundEvents.CREEPER_PRIMED, SoundEvents.ENDERMAN_AMBIENT);

    private static int blinkLeft, blinkLength;

    private TirednessHud() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(TirednessHud::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("tiredness"),
                (graphics, delta) -> render(graphics, delta));
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.isPaused()) return;
        if (blinkLeft > 0) blinkLeft--;
        MobEffectInstance tired = player.getEffect(ModEffects.TIRED);
        if (tired == null) return;
        int stage = Math.clamp(tired.getAmplifier(), 0, 2);
        RandomSource random = player.getRandom();
        if (blinkLeft == 0 && random.nextInt(BLINK_EVERY[stage]) == 0) {
            blinkLength = BLINK_TICKS[stage] + random.nextInt(BLINK_TICKS[stage]);
            blinkLeft = blinkLength;
        }
        if (stage == 2 && random.nextInt(PHANTOM_ONE_IN) == 0) {
            // Behind, and a little to one side: where a look over the shoulder finds nothing.
            Vec3 behind = player.getLookAngle().multiply(-1, 0, -1).normalize()
                    .yRot((float) ((random.nextDouble() - 0.5) * 1.2)).scale(3 + random.nextDouble() * 4);
            Vec3 at = player.position().add(behind);
            player.level().playLocalSound(at.x, at.y, at.z, PHANTOMS.get(random.nextInt(PHANTOMS.size())),
                    SoundSource.HOSTILE, 0.8f, 0.9f + random.nextFloat() * 0.2f, false);
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;
        double dark = 0;
        MobEffectInstance asleep = player.getEffect(ModEffects.MICROSLEEP);
        if (asleep != null) {
            // Dropping off and coming to: black in a few ticks, held, and light again.
            int left = asleep.getDuration();
            int gone = de.ipnats.hardwrought.survival.Tiredness.MICROSLEEP_TICKS - left;
            dark = Math.min(1.0, Math.min(gone / 5.0, left / 8.0));
        }
        if (blinkLeft > 0 && blinkLength > 0) {
            // Shut and open again: a lid coming down and going up.
            double shut = Math.sin(Math.PI * (blinkLength - blinkLeft + delta.getGameTimeDeltaPartialTick(false)) / blinkLength);
            dark = Math.max(dark, Math.clamp(shut, 0, 1) * 0.92);
        }
        if (dark <= 0) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        int alpha = (int) (255 * dark);
        // Lids from above and below, meeting in the middle as the black deepens.
        int lid = (int) (height / 2.0 * Math.min(1.0, dark * 1.15));
        graphics.fill(0, 0, width, lid, alpha << 24);
        graphics.fill(0, height - lid, width, height, alpha << 24);
        if (dark > 0.85) graphics.fill(0, 0, width, height, (int) (255 * (dark - 0.85) / 0.15) << 24);
    }
}
