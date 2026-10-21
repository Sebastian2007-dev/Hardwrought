package de.ipnats.hardwrought.nuclear;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * A Geiger–Müller counter. Held in either hand it clicks as fast as the radiation comes, and reads the
 * dose rate where the player stands; used, it also reads the dose the player has taken.
 *
 * <p>A natural background clicks now and then; a crate of uranium ore ticks; next to a running reactor
 * without its shield it roars.
 */
public class GeigerCounterItem extends Item {
    /** The counts a second the tube gives for a microsievert an hour, as a pancake probe does, about. */
    static final double COUNTS_PER_MICROSIEVERT = 1.5;
    /** The background of the world, in microsieverts an hour. */
    static final double BACKGROUND = 0.1;

    public GeigerCounterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer holder) {
            holder.sendSystemMessage(Component.translatable("message.hardwrought.geiger.dose",
                    format(Radiation.rate(holder)), String.format(Locale.ROOT, "%.3f", Radiation.dose(holder)))
                    .withStyle(ChatFormatting.YELLOW));
        }
        return InteractionResult.SUCCESS;
    }

    /** Told once a second what the player is in; clicks and reads if they hold a counter. */
    static void listen(ServerPlayer player, double svPerHour) {
        if (!player.getMainHandItem().is(NuclearItems.GEIGER_COUNTER) && !player.getOffhandItem().is(NuclearItems.GEIGER_COUNTER)) {
            return;
        }
        double microsieverts = svPerHour * 1e6 + BACKGROUND;
        // The counts of a second, as chance has them: a few heard as clicks, a roar heard as one long rattle.
        double counts = microsieverts * COUNTS_PER_MICROSIEVERT;
        int clicks = (int) Math.min(12, counts);
        if (player.getRandom().nextDouble() < counts - Math.floor(counts)) clicks++;
        clicks = Math.min(12, clicks);
        for (int i = 0; i < clicks; i++) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.PLAYERS, 0.15f, 1.8f + player.getRandom().nextFloat() * 0.2f);
        }
        ChatFormatting colour = svPerHour >= 1e-2 ? ChatFormatting.RED : svPerHour >= 1e-4 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        player.sendOverlayMessage(Component.translatable("message.hardwrought.geiger.rate", format(svPerHour)).withStyle(colour));
    }

    /** A dose rate as a counter shows it: in the unit that keeps the number readable. */
    public static String format(double svPerHour) {
        double micro = svPerHour * 1e6 + BACKGROUND;
        if (micro < 1000) return String.format(Locale.ROOT, "%.1f µSv/h", micro);
        if (micro < 1e6) return String.format(Locale.ROOT, "%.1f mSv/h", micro / 1000);
        return String.format(Locale.ROOT, "%.2f Sv/h", micro / 1e6);
    }
}
