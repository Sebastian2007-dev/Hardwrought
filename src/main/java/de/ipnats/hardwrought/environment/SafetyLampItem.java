package de.ipnats.hardwrought.environment;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * The primitive countermeasure of specification section 18.3. Carbon dioxide and a thin atmosphere
 * give no warning of their own, so without an instrument a player only ever sees symptoms. Carrying
 * this lamp turns the warnings on in the HUD, and using it reports what the air around the player's
 * head is made of.
 *
 * <p>It is deliberately the simplest step of the progression the section describes. Fuel, a gauze
 * flame that reacts to methane on its own, and the later detectors belong to the technology
 * milestones, not here.
 */
public final class SafetyLampItem extends Item {
    public SafetyLampItem(Properties properties) {
        super(properties);
    }

    /** True when the player has a lamp they could read: on the belt, in the inventory, or in hand. */
    public static boolean carriedBy(ServerPlayer player) {
        var runtime = CoreLifecycle.find(player.level().getServer());
        if (runtime != null && runtime.equipment().lamp(player).getItem() instanceof SafetyLampItem) {
            return true;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).getItem() instanceof SafetyLampItem) return true;
        }
        return player.getOffhandItem().getItem() instanceof SafetyLampItem;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            var runtime = CoreLifecycle.find(serverPlayer.level().getServer());
            if (runtime == null) return InteractionResult.PASS;
            EnvironmentReading reading = runtime.environment().reading(serverPlayer);
            GasMixture gases = reading.gases();
            serverPlayer.sendSystemMessage(Component.translatable("message.hardwrought.safety_lamp_reading",
                    String.format(Locale.ROOT, "%.1f", gases.oxygen() * 100),
                    String.format(Locale.ROOT, "%.2f", gases.carbonDioxide() * 100),
                    String.format(Locale.ROOT, "%.2f", gases.methane() * 100),
                    String.format(Locale.ROOT, "%.0f", gases.carbonMonoxide() * 1_000_000)));
            if (gases.explosive()) {
                serverPlayer.sendSystemMessage(Component.translatable("message.hardwrought.safety_lamp_firedamp")
                        .withStyle(ChatFormatting.RED));
            } else if (gases.oxygen() < GasMixture.OXYGEN_DANGEROUS
                    || gases.carbonDioxide() > GasMixture.CARBON_DIOXIDE_SEVERE
                    || gases.carbonMonoxide() >= GasMixture.CARBON_MONOXIDE_SEVERE) {
                serverPlayer.sendSystemMessage(Component.translatable("message.hardwrought.safety_lamp_bad_air")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
