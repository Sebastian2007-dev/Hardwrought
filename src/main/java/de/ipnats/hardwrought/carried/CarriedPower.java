package de.ipnats.hardwrought.carried;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.survival.Thermal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * What worn devices do, once a second, and what it costs their cells (stage E4).
 *
 * <ul>
 *   <li>A <b>headlamp</b>, on and charged, is a carried light on the head ({@link #HEADLAMP_LIGHT}): it
 *       lights the way and, as any light does, shows its wearer to what is watching. {@link #HEADLAMP_WATTS}.</li>
 *   <li><b>Night vision goggles</b> see in true darkness at once, without waiting for the eyes to adapt;
 *       in bright light they blind. {@link #GOGGLES_WATTS}.</li>
 *   <li>An <b>exoskeleton</b> takes the weight: its wearer carries {@link #EXO_CARRY} times as much before
 *       they are slowed, a wound slows them half as much, and they break blocks a little faster. Flat,
 *       it is a heavy frame and they carry less. {@link #EXO_WATTS} standing, more walking.</li>
 *   <li>A <b>lining</b> in a chestplate pulls what its wearer feels toward comfort, the heated one
 *       when it is cold and the cooled one when it is hot, by up to {@link #LINING_DEGREES}, at
 *       {@link #LINING_WATTS_PER_DEGREE} for every degree it makes up.</li>
 * </ul>
 */
public final class CarriedPower {
    public static final int HEADLAMP_LIGHT = 13;
    public static final double HEADLAMP_WATTS = 5.0, GOGGLES_WATTS = 8.0, EXO_WATTS = 15.0, EXO_WALKING_WATTS = 35.0;
    public static final double EXO_CARRY = 3.0, EXO_FLAT_CARRY = 0.85, EXO_WOUND = 0.5, EXO_MINING = 0.15;
    public static final double LINING_DEGREES = 15.0, LINING_WATTS_PER_DEGREE = 3.0;
    /** How bright it has to be for goggles to blind. */
    public static final int GOGGLES_BLIND_AT = 12;
    private static final Identifier EXO_MINING_MODIFIER = Hardwrought.id("exoskeleton_mining");

    private CarriedPower() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(CarriedPower::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            second(player);
        }
    }

    /** One second of whatever the player wears. For tests too. */
    public static void second(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.is(CarriedItems.HEADLAMP) && Cells.on(head)) Cells.draw(head, HEADLAMP_WATTS);
        if (head.is(CarriedItems.NIGHT_VISION_GOGGLES) && Cells.on(head) && Cells.draw(head, GOGGLES_WATTS)) {
            int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
            if (light >= GOGGLES_BLIND_AT) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false));
            }
        }
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        boolean exo = false;
        if (legs.is(CarriedItems.EXOSKELETON) && Cells.on(legs)) {
            boolean walking = player.getDeltaMovement().horizontalDistanceSqr() > 0.001;
            exo = Cells.draw(legs, walking ? EXO_WALKING_WATTS : EXO_WATTS);
        }
        AttributeInstance mining = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (mining != null) {
            mining.removeModifier(EXO_MINING_MODIFIER);
            if (exo) mining.addTransientModifier(new AttributeModifier(EXO_MINING_MODIFIER, EXO_MINING,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Whether an exoskeleton on these legs is on and has charge. */
    public static boolean exoskeletonWorking(LivingEntity wearer) {
        ItemStack legs = wearer.getItemBySlot(EquipmentSlot.LEGS);
        return legs.is(CarriedItems.EXOSKELETON) && Cells.on(legs) && Cells.charged(legs);
    }

    /** How much more the wearer carries before they are slowed: more in a working exoskeleton, less in a flat one. */
    public static double carryFactor(LivingEntity wearer) {
        if (!wearer.getItemBySlot(EquipmentSlot.LEGS).is(CarriedItems.EXOSKELETON)) return 1.0;
        return exoskeletonWorking(wearer) ? EXO_CARRY : EXO_FLAT_CARRY;
    }

    /** How much of a wound's slowing the wearer feels. */
    public static double woundFactor(LivingEntity wearer) {
        return exoskeletonWorking(wearer) ? EXO_WOUND : 1.0;
    }

    /** Whether this head carries a lit headlamp. Client and server alike: its components are synced. */
    public static boolean headlampLit(LivingEntity wearer) {
        ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
        return head.is(CarriedItems.HEADLAMP) && Cells.on(head) && Cells.charged(head);
    }

    /**
     * What the wearer of a lined chestplate feels instead of this: pulled toward comfort by the lining,
     * at its cell's expense. Called once a second by the survival system.
     */
    public static double lined(ServerPlayer player, double felt, boolean immersed) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack lining = chest.get(Cells.LINING);
        if (lining == null || !(lining.getItem() instanceof LiningItem item)) return felt;
        double comfort = Thermal.comfort(immersed);
        double gap = item.heats() ? (comfort - 2.0) - felt : felt - (comfort + 4.0);
        if (gap <= 0.0) return felt;
        double made = Math.min(gap, LINING_DEGREES);
        if (!Cells.draw(chest, made * LINING_WATTS_PER_DEGREE)) return felt;
        return item.heats() ? felt + made : felt - made;
    }
}
