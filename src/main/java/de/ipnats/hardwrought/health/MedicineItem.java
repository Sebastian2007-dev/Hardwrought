package de.ipnats.hardwrought.health;

import de.ipnats.hardwrought.core.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Medicine a player uses on themselves: binding a wound, cleaning one, or taking something for what
 * they have. Each takes a moment, as a drink does.
 */
public class MedicineItem extends Item {
    /** What a medicine does. */
    public enum Kind {
        /** Binds a wound: it is closed against dirt for five minutes. */
        BANDAGE(40, ItemUseAnimation.BRUSH, SoundEvents.WOOL_PLACE),
        /** Binds a wound with a bandage soaked in spirit: closed, and some of an infection already in it cleaned out. */
        DISINFECTED_BANDAGE(40, ItemUseAnimation.BRUSH, SoundEvents.WOOL_PLACE),
        /** Spirit poured on a wound: it stings, and cleans it. */
        SPIRIT(20, ItemUseAnimation.BRUSH, SoundEvents.BOTTLE_EMPTY),
        /** Antiseptic on a wound: cleans it, and much of an infection with it. */
        ANTISEPTIC(20, ItemUseAnimation.BRUSH, SoundEvents.BOTTLE_EMPTY),
        /** Pills of willow bark: a fever brought well down for ten minutes. */
        FEVER_PILLS(16, ItemUseAnimation.EAT, SoundEvents.GENERIC_EAT.value()),
        /** Antibiotics: an infection fought four times as well for ten minutes. */
        ANTIBIOTICS(16, ItemUseAnimation.EAT, SoundEvents.GENERIC_EAT.value());

        private final int ticks;
        private final ItemUseAnimation animation;
        private final SoundEvent sound;

        Kind(int ticks, ItemUseAnimation animation, SoundEvent sound) {
            this.ticks = ticks;
            this.animation = animation;
            this.sound = sound;
        }
    }

    /** How long pills work, in ticks: ten minutes. */
    public static final int PILL_TICKS = 12000;
    /** How much of a wound infection cleaning takes away. */
    public static final double SOAKED_BANDAGE_CLEANS = 15;
    public static final double SPIRIT_CLEANS = 10;
    public static final double ANTISEPTIC_CLEANS = 25;

    private final Kind kind;

    public MedicineItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return kind.ticks;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return kind.animation;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) return stack;
        apply(server, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), kind.sound, SoundSource.PLAYERS, 0.8f, 1.0f);
        if (player.getAbilities().instabuild) return stack;
        boolean bottled = kind == Kind.SPIRIT || kind == Kind.ANTISEPTIC;
        stack.shrink(1);
        if (!bottled) return stack;
        if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
        if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) player.spawnAtLocation(server, new ItemStack(Items.GLASS_BOTTLE));
        return stack;
    }

    /** What it does to the one using it. */
    public void apply(ServerLevel level, ServerPlayer player) {
        switch (kind) {
            case BANDAGE -> Wounds.bandage(player);
            case DISINFECTED_BANDAGE -> {
                Wounds.clean(player, SOAKED_BANDAGE_CLEANS);
                Wounds.bandage(player);
            }
            case SPIRIT -> {
                // It stings.
                player.hurtServer(level, level.damageSources().magic(), 1.0f);
                Wounds.clean(player, SPIRIT_CLEANS);
            }
            case ANTISEPTIC -> Wounds.clean(player, ANTISEPTIC_CLEANS);
            case FEVER_PILLS -> player.addEffect(new MobEffectInstance(ModEffects.WILLOW_BARK, PILL_TICKS, 1));
            case ANTIBIOTICS -> player.addEffect(new MobEffectInstance(ModEffects.ANTIBIOTIC, PILL_TICKS, 0));
        }
    }
}
