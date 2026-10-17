package de.ipnats.hardwrought.end.dragon;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;

/**
 * The dragon's heart: what only a dragon raised again leaves behind when it dies, and what the ritual at
 * the end of the game wants (endgame § 5). It falls where the portal stands when the dying is done.
 */
public final class DragonHeart {
    public static final Item HEART = register();
    /** How long vanilla's dragon takes to die. */
    static final int DYING_TICKS = 200;

    private DragonHeart() { }

    private static Item register() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id("dragon_heart"));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC)
                .fireResistant()));
    }

    static void initialize() { }

    /** A dragon has died: if it was raised again, its heart falls over the portal once it is gone. */
    static void died(ServerLevel level, EnderDragon dragon) {
        if (!VoidDragon.risen(dragon)) return;
        Vec3 at = VoidDragon.middle(dragon).add(0, 6, 0);
        DragonHazards.later(level, DYING_TICKS + 5, l -> {
            ItemEntity heart = new ItemEntity(l, at.x, at.y, at.z, new ItemStack(HEART));
            heart.setDeltaMovement(Vec3.ZERO);
            heart.setPermanentlyInvulnerable(true);
            heart.setUnlimitedLifetime();
            heart.setGlowingTag(true);
            l.addFreshEntity(heart);
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 120, 0.6, 0.6, 0.6, 0.4);
            l.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 4.0f, 0.5f);
        });
    }
}
