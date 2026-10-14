package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.survival.Injuries;
import de.ipnats.hardwrought.survival.WaterskinItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The first way to clean water: a wooden bowl of it, boiled over a fire and left to cool.
 *
 * <p>An empty bowl dipped into water takes {@value #MILLIBUCKETS} millibuckets of it, of whatever kind
 * it is. Set on a campfire like food it comes to the boil ({@code recipe/water_bowl_from_campfire}), and
 * boiling purifies anything but seawater — which the campfire will not take, since boiling only makes it
 * saltier ({@code CampfireWaterBowlMixin}). Boiled water comes off the fire hot: it cools over
 * {@value #COOL_SECONDS} seconds in a pack, and the bar under it is the heat going out of it. It can be
 * drunk hot, but water hotter than a third of boiling burns, and leaves a scald that may become
 * inflamed (see {@link Injuries}).
 */
public class WaterBowlItem extends Item {
    /** What a bowl holds. */
    public static final int MILLIBUCKETS = 250;
    /** How long boiled water takes to cool, in seconds. Its heat is the item's wear. */
    public static final int COOL_SECONDS = 30;
    /** What one bowl is worth to a thirsty body, before the water's quality counts. */
    public static final double DRINK_HYDRATION = 22.0;
    /** The orange of something too hot to touch, for the cooling bar. */
    private static final int HOT_BAR = 0xFF8A3D;

    public WaterBowlItem(Properties properties) {
        super(properties);
    }

    /** A bowl of water of this kind, cool. */
    public static ItemStack of(Item bowl, WaterQuality quality) {
        ItemStack stack = new ItemStack(bowl);
        stack.set(ModDataComponents.WATER_QUALITY, quality == null ? WaterQuality.FRESH : quality);
        return stack;
    }

    public static WaterQuality quality(ItemStack stack) {
        return WaterEvents.qualityOf(stack);
    }

    public static boolean hot(ItemStack stack) {
        return stack.getDamageValue() > 0;
    }

    /** How hot it is, 0 for cool to 1 for just off the boil. */
    public static double heat(ItemStack stack) {
        return Math.clamp(stack.getDamageValue() / (double) COOL_SECONDS, 0.0, 1.0);
    }

    /**
     * A bowl goes onto a campfire as food does, by the campfire itself. Only seawater is turned away
     * there, and saying why is left to the bowl.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !WaterskinItem.isLitFire(level.getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (quality(context.getItemInHand()) != WaterQuality.SALT) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.water_bowl.boil_useless")
                    .withStyle(ChatFormatting.GOLD));
        }
        return InteractionResult.CONSUME;
    }

    /** Held to the mouth, it is drunk, hot or not. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Boiling.DRINK_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) return stack;
        var runtime = CoreLifecycle.find(server.getServer());
        if (runtime != null) runtime.survival().drink(player, DRINK_HYDRATION, drinkQuality(stack));
        Injuries.drankHot(server, player, heat(stack));
        drunk(player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(),
                SoundSource.PLAYERS, 0.8f, 1.0f);
        return player.getAbilities().instabuild ? stack : new ItemStack(Items.BOWL);
    }

    /** What the water in it is, as a drink. */
    protected WaterQuality drinkQuality(ItemStack stack) {
        return quality(stack);
    }

    /** Whatever else the drink does, once drunk. Nothing, for water. */
    protected void drunk(ServerPlayer player) {
    }

    /** It cools a step every second it is carried. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
        if (!hot(stack) || level.getGameTime() % 20 != 0) return;
        // Not while it is being drunk: the drink would be cut short by the bowl changing in the hand.
        if (owner instanceof LivingEntity living && living.isUsingItem() && living.getUseItem() == stack) return;
        stack.setDamageValue(stack.getDamageValue() - 1);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return HOT_BAR;
    }
}
