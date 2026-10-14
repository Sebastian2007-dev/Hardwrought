package de.ipnats.hardwrought.health;

import de.ipnats.hardwrought.water.WaterBowlItem;
import de.ipnats.hardwrought.water.WaterQuality;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * A bowl of herb tea: an infusion boiled on a campfire, so the water in it is purified, and hot when it
 * comes off the fire like any bowl of boiled water. Drunk, it gives what the herb is good for
 * ({@link Diseases#care}).
 */
public class TeaItem extends WaterBowlItem {
    /** How long a tea works, in ticks: five minutes. */
    public static final int WORKS_TICKS = 6000;

    private final Holder<MobEffect> effect;

    public TeaItem(Holder<MobEffect> effect, Properties properties) {
        super(properties);
        this.effect = effect;
    }

    @Override
    protected WaterQuality drinkQuality(ItemStack stack) {
        return WaterQuality.PURIFIED;
    }

    @Override
    protected void drunk(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(effect, WORKS_TICKS, 0));
    }
}
