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
            // A silicon blank is sawn into wafers as a log is into boards.
            if (input.is(de.ipnats.hardwrought.materials.Materials.RAW_SILICON)) {
                return new ItemStack(de.ipnats.hardwrought.materials.Materials.SILICON_WAFER, 4);
            }
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
            // Plastic pellets are pressed into sheet as an ingot is rolled into plate.
            if (input.is(de.ipnats.hardwrought.materials.Materials.PLASTIC_PELLETS)) {
                return new ItemStack(de.ipnats.hardwrought.materials.Materials.PLASTIC_SHEET);
            }
            // Electrical steel is rolled thin, for laminations: two plates to the ingot.
            if (input.is(de.ipnats.hardwrought.parts.Parts.ELECTRICAL_STEEL)) {
                return new ItemStack(de.ipnats.hardwrought.parts.Parts.ELECTRICAL_STEEL_PLATE, 2);
            }
            for (MetalStock.StockMetal metal : MetalStock.metals()) {
                if (input.is(metal.ingot().get())) return new ItemStack(MetalStock.plate(metal.material()));
            }
            return ItemStack.EMPTY;
        }
    },
    /** A spindle and a cutter: a steel rod turned into the fine gears a sorting mechanism counts with. */
    LATHE("lathe", 35.0, 120, SoundEvents.GRINDSTONE_USE, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return input.is(MetalStock.rod("steel"))
                    ? new ItemStack(de.ipnats.hardwrought.storage.StorageBlocks.FINE_GEAR) : ItemStack.EMPTY;
        }
    },
    /** Three rollers and a seaming wheel: a bronze plate rolled round into a length of tube. */
    TUBE_MILL("tube_mill", 35.0, 100, SoundEvents.ANVIL_USE, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return input.is(MetalStock.plate("bronze"))
                    ? new ItemStack(de.ipnats.hardwrought.storage.StorageBlocks.TUBE_SECTION) : ItemStack.EMPTY;
        }
    },

    // The faster marks of each, built on the one before with circuits and seals: twice as fast and then
    // four times, taking more current for it; the third crusher gets two powders out of each ore.
    CRUSHER_MK2("basic_crusher_mk2", 30.0, 20, SoundEvents.GRINDSTONE_USE, ParticleTypes.WHITE_ASH) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return CRUSHER.result(level, input);
        }
    },
    CRUSHER_MK3("basic_crusher_mk3", 20.0, 10, SoundEvents.GRINDSTONE_USE, ParticleTypes.WHITE_ASH) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            ItemStack powder = CRUSHER.result(level, input);
            return powder.isEmpty() ? powder : powder.copyWithCount(powder.getCount() * 2);
        }
    },
    FURNACE_MK2("electric_furnace_mk2", 22.0, 50, SoundEvents.FURNACE_FIRE_CRACKLE, ParticleTypes.SMOKE) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return FURNACE.result(level, input);
        }
    },
    FURNACE_MK3("electric_furnace_mk3", 15.0, 25, SoundEvents.FURNACE_FIRE_CRACKLE, ParticleTypes.SMOKE) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return FURNACE.result(level, input);
        }
    },
    SAWMILL_MK2("sawmill_mk2", 30.0, 30, SoundEvents.WOOD_HIT, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return SAWMILL.result(level, input);
        }
    },
    SAWMILL_MK3("sawmill_mk3", 20.0, 15, SoundEvents.WOOD_HIT, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return SAWMILL.result(level, input);
        }
    },
    PRESS_MK2("plate_press_mk2", 22.0, 40, SoundEvents.ANVIL_LAND, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return PRESS.result(level, input);
        }
    },
    PRESS_MK3("plate_press_mk3", 15.0, 20, SoundEvents.ANVIL_LAND, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return PRESS.result(level, input);
        }
    },

    // The fourth marks are wound for the 240-volt line, and on the 60-volt one barely turn: twice as fast
    // as the third again, for two and a half times the power.
    CRUSHER_MK4("basic_crusher_mk4", 120.0, 5, 240.0, SoundEvents.GRINDSTONE_USE, ParticleTypes.WHITE_ASH) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return CRUSHER_MK3.result(level, input);
        }
    },
    FURNACE_MK4("electric_furnace_mk4", 96.0, 12, 240.0, SoundEvents.FURNACE_FIRE_CRACKLE, ParticleTypes.SMOKE) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return FURNACE.result(level, input);
        }
    },
    SAWMILL_MK4("sawmill_mk4", 120.0, 8, 240.0, SoundEvents.WOOD_HIT, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return SAWMILL.result(level, input);
        }
    },
    PRESS_MK4("plate_press_mk4", 96.0, 10, 240.0, SoundEvents.ANVIL_LAND, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return PRESS.result(level, input);
        }
    },

    /** A spindle that lays enamelled wire turn on turn: eight lengths make one winding. */
    COIL_WINDER("coil_winder", 60.0, 100, SoundEvents.CHAIN_PLACE, ParticleTypes.CRIT) {
        @Override
        public ItemStack result(ServerLevel level, ItemStack input) {
            return input.is(de.ipnats.hardwrought.parts.Parts.ENAMELLED_WIRE)
                    ? new ItemStack(de.ipnats.hardwrought.parts.Parts.WINDING) : ItemStack.EMPTY;
        }

        @Override
        public int pieces(ItemStack input) {
            return 8;
        }
    };

    private final String id;
    private final double ohms;
    private final int ticks;
    private final double ratedVolts;
    private final SoundEvent sound;
    private final SimpleParticleType particle;

    Machine(String id, double ohms, int ticks, SoundEvent sound, SimpleParticleType particle) {
        this(id, ohms, ticks, MachineBlockEntity.RATED_VOLTS, sound, particle);
    }

    Machine(String id, double ohms, int ticks, double ratedVolts, SoundEvent sound, SimpleParticleType particle) {
        this.id = id;
        this.ohms = ohms;
        this.ticks = ticks;
        this.ratedVolts = ratedVolts;
        this.sound = sound;
        this.particle = particle;
    }

    /** The voltage it is wound for: 60 for most, 240 for the fourth marks. */
    public double ratedVolts() {
        return ratedVolts;
    }

    /** How many of the input one piece of work takes. */
    public int pieces(ItemStack input) {
        return 1;
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

    /**
     * The machines whose work the book reads from the machine itself, with the name of the work, which
     * names its instruction. The crusher's table is shown as the starter crusher's, and the furnace does
     * what any furnace does; the later marks do what their first mark does.
     */
    private static final java.util.Map<Machine, String> SHOWN = new java.util.LinkedHashMap<>(java.util.Map.of(
            PRESS, "pressing", SAWMILL, "sawing", LATHE, "lathe", TUBE_MILL, "tube_mill", COIL_WINDER, "coil_winder"));
    private static List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> shown;

    /**
     * Every piece of work of these machines, found by offering each of them every item there is. What
     * the book shows is therefore what the machine does: a row added to {@link #result} is in the book
     * with no line written for it. Worked out once; items do not change while the game runs.
     */
    public static List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> worldRecipes() {
        if (shown != null) return shown;
        List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> rows = new java.util.ArrayList<>();
        for (var entry : SHOWN.entrySet()) {
            Machine machine = entry.getKey();
            ItemStack station = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                    de.ipnats.hardwrought.Hardwrought.id(machine.id())));
            for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                ItemStack input = new ItemStack(item);
                ItemStack made;
                try {
                    // No world is to hand: a row that needs one to look a recipe up — boards out of a log —
                    // is the crafting recipe's, and is shown as that.
                    made = machine.result(null, input);
                } catch (RuntimeException needsAWorld) {
                    continue;
                }
                if (made == null || made.isEmpty()) continue;
                input.setCount(machine.pieces(input));
                rows.add(new de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe(
                        de.ipnats.hardwrought.Hardwrought.id(entry.getValue() + "/"
                                + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(made.getItem()).getPath()),
                        de.ipnats.hardwrought.knowledge.WorldRecipes.one(input), made.copy(), station));
            }
        }
        shown = List.copyOf(rows);
        return shown;
    }
}
