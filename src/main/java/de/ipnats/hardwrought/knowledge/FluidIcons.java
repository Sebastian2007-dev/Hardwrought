package de.ipnats.hardwrought.knowledge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasLines;
import de.ipnats.hardwrought.refining.RefineryFluids;
import de.ipnats.hardwrought.refining.RefineryProduct;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Liquids and gases as the compendium shows them in a recipe: as themselves, not in the bucket or
 * canister they happen to be carried in, with how much in the tooltip — the way recipe viewers do it.
 *
 * <p>The book's recipes are made of item stacks, so a liquid travels as a stack of an item that is never
 * held, carrying what it stands for and how much. The book draws it as the liquid, and a click on it
 * opens the page of its bucket or canister, where the liquid's own recipes are.
 */
public final class FluidIcons {
    /** A liquid by its fluid id in millibuckets, or a gas by its name in units. */
    public record Ingredient(boolean gas, String id, int amount) {
        public static final Codec<Ingredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("gas").forGetter(Ingredient::gas),
                Codec.STRING.fieldOf("id").forGetter(Ingredient::id),
                Codec.INT.fieldOf("amount").forGetter(Ingredient::amount)).apply(instance, Ingredient::new));
        public static final StreamCodec<ByteBuf, Ingredient> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Ingredient::gas, ByteBufCodecs.STRING_UTF8, Ingredient::id,
                ByteBufCodecs.VAR_INT, Ingredient::amount, Ingredient::new);

        /** The liquid, or null for a gas or one that no longer exists. */
        public Fluid fluid() {
            if (gas) return null;
            Identifier key = Identifier.tryParse(id);
            return key == null ? null : BuiltInRegistries.FLUID.getValue(key);
        }

        /** The gas, or null for a liquid. */
        public GasKind gasKind() {
            if (!gas) return null;
            for (GasKind kind : GasKind.values()) if (kind.getSerializedName().equals(id)) return kind;
            return null;
        }

        public Component name() {
            if (gas) {
                GasKind kind = gasKind();
                return kind == null ? Component.literal(id) : Component.translatable(kind.translationKey());
            }
            Fluid fluid = fluid();
            if (fluid == Fluids.WATER) return Component.translatable("block.minecraft.water");
            if (fluid == Fluids.LAVA) return Component.translatable("block.minecraft.lava");
            for (RefineryProduct product : RefineryProduct.values()) {
                if (product.source() == fluid) return Component.translatable("fluid.hardwrought." + product.getSerializedName());
            }
            if (fluid == de.ipnats.hardwrought.oil.ModFluids.CRUDE_OIL) return Component.translatable("block.hardwrought.crude_oil");
            return Component.literal(id);
        }

        /** How much, as the tooltip says it. */
        public Component quantity() {
            return Component.translatable(gas ? "gui.hardwrought.compendium.gas_amount" : "gui.hardwrought.compendium.fluid_amount", amount);
        }
    }

    public static final DataComponentType<Ingredient> INGREDIENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("fluid_ingredient"), DataComponentType.<Ingredient>builder()
                    .persistent(Ingredient.CODEC).networkSynchronized(Ingredient.STREAM_CODEC).build());

    /** The item a liquid in a recipe travels as. Never held, and in no creative tab. */
    public static final Item ICON;

    static {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id("fluid_icon"));
        ICON = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    private FluidIcons() { }

    public static void initialize() { }

    public static ItemStack liquid(Fluid fluid, int millibuckets) {
        ItemStack stack = new ItemStack(ICON);
        stack.set(INGREDIENT, new Ingredient(false, BuiltInRegistries.FLUID.getKey(fluid).toString(), millibuckets));
        return stack;
    }

    public static ItemStack gas(GasKind kind, double units) {
        ItemStack stack = new ItemStack(ICON);
        stack.set(INGREDIENT, new Ingredient(true, kind.getSerializedName(), (int) Math.round(units)));
        return stack;
    }

    /** What a stack stands for, where it is a liquid or a gas; null for a plain item. */
    public static Ingredient of(ItemStack stack) {
        return stack.is(ICON) ? stack.get(INGREDIENT) : null;
    }

    /** What the liquid or gas is carried in, whose page the book opens for it; null where there is none. */
    public static Item container(Ingredient ingredient) {
        if (ingredient == null) return null;
        if (ingredient.gas()) {
            GasKind kind = ingredient.gasKind();
            return kind == null ? null : GasLines.canister(kind);
        }
        Fluid fluid = ingredient.fluid();
        if (fluid == Fluids.WATER) return Items.WATER_BUCKET;
        if (fluid == Fluids.LAVA) return Items.LAVA_BUCKET;
        if (fluid == de.ipnats.hardwrought.oil.ModFluids.CRUDE_OIL) return de.ipnats.hardwrought.core.registry.ModItems.CRUDE_OIL_BUCKET;
        for (RefineryProduct product : RefineryProduct.values()) {
            if (product.source() == fluid) return product.bucket();
        }
        return null;
    }

    /**
     * Whether a recipe's stack stands for this item: the item itself, or a liquid or gas the item
     * carries — a bucket or bottle of it, a canister of it.
     */
    public static boolean matches(ItemStack option, Item item) {
        if (option.is(item)) return !option.is(ICON);
        Ingredient ingredient = of(option);
        if (ingredient == null) return false;
        if (container(ingredient) == item) return true;
        if (!ingredient.gas()) {
            for (RefineryProduct product : RefineryProduct.values()) {
                if (product.source() == ingredient.fluid() && RefineryFluids.bottleOf(product) == item) return true;
            }
        }
        return false;
    }
}
