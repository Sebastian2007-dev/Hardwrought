package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.metallurgy.Crushing;
import de.ipnats.hardwrought.smithing.MetalStock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import java.util.List;

/**
 * Sections 76 and 77: the machines that run on current. Each takes one kind of thing, a piece at a
 * time, and makes another of it; they differ in what they take, how much current, and how long.
 * How any of them behaves on its voltage is the same for all — see {@link MachineBlockEntity}.
 */
public enum Machine {
    /** Iron jaws on a motor: ore to powder, as the starter crusher does on a shaft. */
    CRUSHER("basic_crusher", 40.0, 40, SoundEvents.GRINDSTONE_USE, ParticleTypes.WHITE_ASH) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            Item powder = Crushing.result(input);
            return powder == null ? ItemStack.EMPTY : new ItemStack(powder);
        }
    },
    /** A heating coil in a brick chamber: whatever a furnace smelts, in half the time and with no fuel. */
    FURNACE("electric_furnace", 30.0, 100, SoundEvents.FURNACE_FIRE_CRACKLE, ParticleTypes.SMOKE) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            SingleRecipeInput single = new SingleRecipeInput(input);
            return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, single, level)
                    .map(recipe -> recipe.value().assemble(single)).orElse(ItemStack.EMPTY);
        }
    },
    /** A blade on a motor: it wastes less of a log than an axe, and gives half as many boards again. */
    SAWMILL("sawmill", 40.0, 60, SoundEvents.WOOD_HIT, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            if (!input.is(ItemTags.LOGS)) return ItemStack.EMPTY;
            CraftingInput alone = CraftingInput.of(1, 1, List.of(input.copyWithCount(1)));
            ItemStack boards = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, alone, level)
                    .map(recipe -> recipe.value().assemble(alone)).orElse(ItemStack.EMPTY);
            if (boards.isEmpty()) return ItemStack.EMPTY;
            boards.setCount(Math.min(boards.getMaxStackSize(), boards.getCount() + boards.getCount() / 2));
            return boards;
        }
    },
    /** Two rollers and a heavy screw: an ingot goes in, a plate comes out, with no hammer and no heat. */
    PRESS("plate_press", 30.0, 80, SoundEvents.ANVIL_LAND, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            for (MetalStock.StockMetal metal : MetalStock.metals()) {
                if (input.is(metal.ingot().get())) return new ItemStack(MetalStock.plate(metal.material()));
            }
            return ItemStack.EMPTY;
        }
    };

    private final String id;
    private final double ohms;
    private final int ticks;
    private final SoundEvent sound;
    private final SimpleParticleType particle;

    Machine(String id, double ohms, int ticks, SoundEvent sound, SimpleParticleType particle) {
        this.id = id;
        this.ohms = ohms;
        this.ticks = ticks;
        this.sound = sound;
        this.particle = particle;
    }

    /** The name of its block. */
    public String id() {
        return id;
    }

    /** What it is between the wire and the ground while it works. */
    public double ohms() {
        return ohms;
    }

    /** Ticks one piece takes at the rated voltage. */
    public int ticks() {
        return ticks;
    }

    public SoundEvent sound() {
        return sound;
    }

    public SimpleParticleType particle() {
        return particle;
    }

    /** What one piece of this becomes in the machine; empty where it takes no such thing. */
    public abstract ItemStack result(ServerLevel level, ItemStack input);
}
