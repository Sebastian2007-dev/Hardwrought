package de.ipnats.hardwrought.wither;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockPattern;

import com.mojang.serialization.Codec;

/**
 * Soul-charged skulls (see docs/endgame.md): a wither skeleton skull is an empty thing until it has
 * lain in soul fire. Dropped into one, it does not burn; it drinks the fire for {@link #CHARGE_TICKS},
 * and comes out charged — shimmering, and able to raise the Wither. Three empty skulls on soul sand
 * do nothing but smoke.
 *
 * <p>The charge is a component on the item, and a placed skull keeps it on its block, so it survives
 * being set down, broken and picked up again.
 */
public final class SoulSkulls {
    /** Half a minute in soul fire. */
    public static final int CHARGE_TICKS = 30 * 20;
    static final int STEP = 10;

    /** How far a skull has drunk the fire, in ticks. */
    public static final DataComponentType<Integer> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("soul_charge"), DataComponentType.<Integer>builder().persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** Charged: the skull can raise the Wither. */
    public static final DataComponentType<Unit> CHARGED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("soul_charged"), DataComponentType.<Unit>builder().persistent(Unit.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(Unit.INSTANCE)).build());

    private SoulSkulls() { }

    public static void initialize() {
        // Touching the class registers the components.
    }

    public static boolean charged(ItemStack stack) {
        return stack.has(CHARGED);
    }

    /** Whether this item, lying where it lies, is a skull being kept from the fire it is charging in. */
    public static boolean shieldedFromFire(ItemEntity entity) {
        return entity.getItem().is(Items.WITHER_SKELETON_SKULL)
                && entity.level().getBlockState(entity.blockPosition()).is(Blocks.SOUL_FIRE);
    }

    /** A dropped skull in soul fire drinks it; called from the item's tick. */
    public static void charge(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % STEP != 0) return;
        ItemStack stack = entity.getItem();
        if (!stack.is(Items.WITHER_SKELETON_SKULL) || charged(stack) || !shieldedFromFire(entity)) return;
        ItemStack charging = stack.copy();
        int charge = charging.getOrDefault(CHARGE, 0) + STEP;
        double x = entity.getX(), y = entity.getY() + 0.3, z = entity.getZ();
        if (charge < CHARGE_TICKS) {
            charging.set(CHARGE, charge);
            entity.setItem(charging);
            // The fire is seen going into it: souls drawn in, more of them the fuller it gets.
            level.sendParticles(ParticleTypes.SOUL, x, y + 0.4, z, 1 + charge * 3 / CHARGE_TICKS, 0.3, 0.3, 0.3, 0.01);
            return;
        }
        charging.remove(CHARGE);
        charging.set(CHARGED, Unit.INSTANCE);
        charging.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        entity.setItem(charging);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 30, 0.3, 0.4, 0.3, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 12, 0.2, 0.3, 0.2, 0.05);
        level.playSound(null, entity.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0f, 0.6f);
    }

    /**
     * Whether the Wither pattern at {@code pos}, if there is one, may rise: all three skulls charged.
     * Where they are not, the empty ones smoke and anyone near is told why.
     */
    public static boolean mayRise(Level level, BlockPos pos, BlockPattern pattern) {
        BlockPattern.BlockPatternMatch match = pattern.find(level, pos);
        if (match == null) return true;
        boolean all = true;
        for (int i = 0; i < 3; i++) {
            BlockPos skull = match.getBlock(i, 0, 0).getPos();
            BlockEntity entity = level.getBlockEntity(skull);
            if (entity != null && entity.components().has(CHARGED)) continue;
            all = false;
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.LARGE_SMOKE, skull.getX() + 0.5, skull.getY() + 0.5, skull.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.01);
            }
        }
        if (!all && level instanceof ServerLevel server) {
            server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.5f);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(pos).inflate(16))) {
                player.sendSystemMessage(Component.translatable("message.hardwrought.wither.empty_skulls")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }
        return all;
    }
}
