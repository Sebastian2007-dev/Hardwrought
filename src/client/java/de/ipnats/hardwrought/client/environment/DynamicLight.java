package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.environment.SafetyLampItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Section 20: a carried light source should actually light the way — for everyone who can see it.
 * Every client lights the torches it can see being carried, its own player's and everyone else's,
 * so the light is shared without a single packet.
 *
 * <p>The light is drawn by the shaders, like the light of spells (see {@code FxLighting}): it follows
 * the hand smoothly between ticks, flickers like a flame, and lights the surroundings whatever the
 * carrier stands in — tall grass, a slab, water. Nothing is placed in the world.
 *
 * <p>The brightness comes from the item itself — a block item lights as brightly as its block — so
 * no table has to be synchronized to the client.
 */
public final class DynamicLight {
    private static final int SAFETY_LAMP_LIGHT = 12;
    /** Carriers farther away than this are not lit; beyond it their light barely reaches the viewer. */
    private static final double RANGE = 48;
    /** Lights are cheap in the shader, but the shader takes only so many; past this many, the nearest win. */
    private static final int MAX_LIGHTS = 16;

    static final int FLAME = 0xFFC48A;
    static final int SOUL_FLAME = 0x73DCFF;
    static final int LAMP = 0xFFD890;

    /** One carried light: who carries it, how bright it is and its colour. */
    public record Carried(LivingEntity carrier, int level, int color, boolean flame) {
        /** Where the light is at this moment: in the carrier's hand, between the last tick and this one. */
        public Vec3 position(float partial) {
            Vec3 feet = carrier.getPosition(partial);
            float yaw = carrier.getPreciseBodyRotation(partial) * ((float) Math.PI / 180f);
            boolean right = handOf(carrier) == HumanoidArm.RIGHT;
            double side = right ? -0.38 : 0.38;
            double height = carrier.getBbHeight() * 0.62;
            return feet.add(Math.cos(yaw) * side - Math.sin(yaw) * 0.25, height, Math.sin(yaw) * side + Math.cos(yaw) * 0.25);
        }
    }

    private static final List<Carried> carried = new ArrayList<>();
    private static int ownLevel;

    private DynamicLight() { }

    /** Every carried light this client sees, nearest first. */
    public static List<Carried> carried() {
        return carried;
    }

    /** How brightly the local player's own carried light shines, 0 when none. */
    public static int ownLevel() {
        return ownLevel;
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(DynamicLight::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            carried.clear();
            ownLevel = 0;
        });
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        carried.clear();
        ownLevel = 0;
        if (level == null || player == null || !Hardwrought.config().dynamicLight()) return;
        List<LivingEntity> carriers = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE),
                entity -> entity == player || !entity.isInvisible() && !entity.isSpectator());
        carriers.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
        for (LivingEntity carrier : carriers) {
            if (carried.size() >= MAX_LIGHTS) break;
            ItemStack held = brighter(carrier.getMainHandItem(), carrier.getOffhandItem());
            int light = lightOf(held);
            if (light <= 0) continue;
            carried.add(new Carried(carrier, light, colorOf(held), flickers(held)));
            if (carrier == player) ownLevel = light;
        }
    }

    private static ItemStack brighter(ItemStack a, ItemStack b) {
        return lightOf(b) > lightOf(a) ? b : a;
    }

    private static HumanoidArm handOf(LivingEntity carrier) {
        boolean main = lightOf(carrier.getMainHandItem()) >= lightOf(carrier.getOffhandItem());
        HumanoidArm arm = carrier.getMainArm();
        return main ? arm : arm.getOpposite();
    }

    public static int heldLight(LivingEntity carrier) {
        return Math.max(lightOf(carrier.getMainHandItem()), lightOf(carrier.getOffhandItem()));
    }

    public static int lightOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.getItem() instanceof SafetyLampItem) return SAFETY_LAMP_LIGHT;
        if (stack.getItem() instanceof BlockItem blockItem) {
            return Math.min(LightBlock.MAX_LEVEL, blockItem.getBlock().defaultBlockState().getLightEmission());
        }
        return 0;
    }

    /** The colour of the light an item gives: like the block it places (see {@link PlacedLights#colorOf}). */
    public static int colorOf(ItemStack stack) {
        if (stack.getItem() instanceof SafetyLampItem) return LAMP;
        return PlacedLights.colorOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    private static boolean flickers(ItemStack stack) {
        if (stack.getItem() instanceof SafetyLampItem) return false;
        return PlacedLights.flickers(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }
}
