package de.ipnats.hardwrought.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * What a player wears that is not armour and not held: the pack on their back, the lamp on their
 * belt, and the leather gloves that let them hold hot metal.
 *
 * <p>Kept apart from the inventory on purpose, and that is the whole reason this type exists. A pack
 * that lives in the inventory is dropped on death with everything else, and a player who has just
 * lost their pack has also lost the inventory it was unlocking — every slot they own at once, with
 * nothing they could do about it. Held here, it is world data keyed by player rather than something
 * the corpse carries, so death does not touch it and no code has to remember to put it back.
 */
public record PlayerEquipment(ItemStack backpack, ItemStack lamp, ItemStack gloves) {
    public static final Codec<PlayerEquipment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("backpack", ItemStack.EMPTY)
                    .forGetter(PlayerEquipment::backpack),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("lamp", ItemStack.EMPTY)
                    .forGetter(PlayerEquipment::lamp),
            // Optional with a default, so a world saved before gloves existed still loads.
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("gloves", ItemStack.EMPTY)
                    .forGetter(PlayerEquipment::gloves)
    ).apply(instance, PlayerEquipment::new));

    public PlayerEquipment {
        if (backpack == null) backpack = ItemStack.EMPTY;
        if (lamp == null) lamp = ItemStack.EMPTY;
        if (gloves == null) gloves = ItemStack.EMPTY;
    }

    /** Pack and lamp only: what everything worn looked like before gloves. */
    public PlayerEquipment(ItemStack backpack, ItemStack lamp) {
        this(backpack, lamp, ItemStack.EMPTY);
    }

    public static PlayerEquipment empty() {
        return new PlayerEquipment(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    public PlayerEquipment withBackpack(ItemStack value) {
        return new PlayerEquipment(value, lamp, gloves);
    }

    public PlayerEquipment withLamp(ItemStack value) {
        return new PlayerEquipment(backpack, value, gloves);
    }

    public PlayerEquipment withGloves(ItemStack value) {
        return new PlayerEquipment(backpack, lamp, value);
    }

    /** What the worn pack is, or null where nothing is worn. */
    public BackpackTier tier() {
        return BackpackItem.tierOf(backpack);
    }

    /** A copy, so that handing this out cannot let anything edit the saved stacks in place. */
    public PlayerEquipment copy() {
        return new PlayerEquipment(backpack.copy(), lamp.copy(), gloves.copy());
    }
}
