package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.machinery.Driveline;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds and operates the two forge structures: a solid 2x2x2 and a hollow 3x3x3.
 *
 * <p>A joined forge has one fire and one set of places, both kept in its controller, the block at
 * the lowest corner. When it forms, whatever lay in the single forges is gathered there; when it
 * falls apart, the controller's contents are handed out again, one fuel and one metal place per
 * block, and only what finds no room is put out on the ground. Nothing is ever lost or doubled.
 */
public final class ForgeMultiblock {
    /** The highest {@link ForgeBlock#PART}: 1-8 are the small forge, 9-35 the large one. */
    public static final int MAX_PART = 35;
    private static final int SMALL_FIRST = 1;
    private static final int LARGE_FIRST = 9;

    private ForgeMultiblock() {}

    public enum Size {
        SMALL(1, 2),
        LARGE(2, 3);

        private final byte id;
        private final int edge;

        Size(int id, int edge) {
            this.id = (byte) id;
            this.edge = edge;
        }

        /** The forge layout this size gives: 1 for the small forge, 2 for the large one. */
        public int layout() {
            return id;
        }

        static Size byId(byte id) {
            return id == LARGE.id ? LARGE : id == SMALL.id ? SMALL : null;
        }
    }

    public record Structure(Size size, BlockPos controller, List<BlockPos> members) {
        public int fuelSlots() {
            return ForgeBlockEntity.fuelSlots(size.layout());
        }

        public int metalSlots() {
            return ForgeBlockEntity.metalSlots(size.layout());
        }
    }

    /** The part number of the block at this offset from the controller, the lowest corner. */
    public static int partIndex(Size size, int x, int y, int z) {
        return size == Size.SMALL ? SMALL_FIRST + x + 2 * z + 4 * y : LARGE_FIRST + x + 3 * z + 9 * y;
    }

    /** Whether a block with this part number shows the bed of coals. */
    public static boolean isTop(int part) {
        if (part <= 0) return true;
        if (part < LARGE_FIRST) return (part - SMALL_FIRST) / 4 == 1;
        return (part - LARGE_FIRST) / 9 == 2;
    }

    /** Gives every block of the structure its part of the big model. Changes nothing when already right. */
    public static void applyParts(Level level, Structure structure) {
        for (BlockPos pos : structure.members) {
            BlockPos offset = pos.subtract(structure.controller);
            setPart(level, pos, partIndex(structure.size, offset.getX(), offset.getY(), offset.getZ()));
        }
    }

    private static void setPart(Level level, BlockPos pos, int part) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof ForgeBlock && state.getValue(ForgeBlock.PART) != part) {
            level.setBlock(pos, state.setValue(ForgeBlock.PART, part), Block.UPDATE_CLIENTS);
        }
    }

    /** Returns an existing link, or forms a complete nearby structure on the server. */
    public static Structure getOrForm(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ForgeBlockEntity forge && forge.isMultiblockPart()) {
            Structure linked = fromLink(forge);
            if (linked != null) return linked;
        }
        if (!(level instanceof ServerLevel server)) return null;
        Structure found = find(server, pos);
        return found != null && form(server, found) ? found : null;
    }

    private static Structure fromLink(ForgeBlockEntity forge) {
        Size size = Size.byId(forge.multiblockSize());
        if (size == null || forge.multiblockController() == null) return null;
        return structure(size, forge.multiblockController());
    }

    /** Detects the 26-block shell first, then the solid 8-block cube. */
    public static Structure find(Level level, BlockPos member) {
        for (Size size : new Size[]{Size.LARGE, Size.SMALL}) {
            for (int x = 0; x < size.edge; x++) {
                for (int y = 0; y < size.edge; y++) {
                    for (int z = 0; z < size.edge; z++) {
                        BlockPos controller = member.offset(-x, -y, -z);
                        Structure candidate = structure(size, controller);
                        if (matches(level, candidate)) return candidate;
                    }
                }
            }
        }
        return null;
    }

    public static boolean matches(Level level, Structure structure) {
        for (BlockPos member : structure.members) {
            if (!(level.getBlockState(member).getBlock() instanceof ForgeBlock)) return false;
        }
        if (structure.size == Size.LARGE) {
            BlockPos center = structure.controller.offset(1, 1, 1);
            if (!level.getBlockState(center).isAir()) return false;
        }
        return true;
    }

    private static Structure structure(Size size, BlockPos controller) {
        List<BlockPos> members = new ArrayList<>(size == Size.SMALL ? 8 : 26);
        for (int y = 0; y < size.edge; y++) {
            for (int z = 0; z < size.edge; z++) {
                for (int x = 0; x < size.edge; x++) {
                    if (size == Size.LARGE && x == 1 && y == 1 && z == 1) continue;
                    members.add(controller.offset(x, y, z));
                }
            }
        }
        return new Structure(size, controller.immutable(), List.copyOf(members));
    }

    private static boolean form(ServerLevel level, Structure structure) {
        List<ForgeBlockEntity> forges = new ArrayList<>();
        for (BlockPos pos : structure.members) {
            if (!(level.getBlockEntity(pos) instanceof ForgeBlockEntity forge) || forge.isMultiblockPart()) return false;
            forges.add(forge);
        }
        List<ItemStack> contents = new ArrayList<>();
        int burn = 0;
        int total = 0;
        double temperature = Heat.AMBIENT;
        for (ForgeBlockEntity forge : forges) {
            contents.addAll(forge.takeAll());
            burn += forge.localBurnTicks();
            total += forge.localBurnTotal();
            temperature = Math.max(temperature, forge.localTemperature());
            forge.setThermalState(0, 0, Heat.AMBIENT);
        }
        for (ForgeBlockEntity forge : forges) forge.link(structure.controller, structure.size.id);
        ForgeBlockEntity controller = controller(level, structure);
        controller.setThermalState(burn, total, temperature);
        for (ItemStack stack : contents) {
            if (!controller.place(stack, structure.size.layout())) drop(level, structure.controller.above(structure.size.edge), stack);
        }
        applyParts(level, structure);
        syncLit(level, structure, burn > 0);
        return true;
    }

    public static ForgeBlockEntity controller(Level level, Structure structure) {
        return level.getBlockEntity(structure.controller) instanceof ForgeBlockEntity forge ? forge : null;
    }

    public static boolean isLined(Level level, Structure structure) {
        for (BlockPos pos : structure.members) {
            if (!level.getBlockState(pos).getValue(ForgeBlock.LINED)) return false;
        }
        return true;
    }

    public static void line(ServerLevel level, Structure structure) {
        for (BlockPos pos : structure.members) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof ForgeBlock && !state.getValue(ForgeBlock.LINED)) {
                level.setBlock(pos, state.setValue(ForgeBlock.LINED, true), Block.UPDATE_ALL);
            }
        }
    }

    public static boolean blown(Level level, Structure structure) {
        for (BlockPos member : structure.members) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = member.relative(side);
                if (level.getBlockState(beside).getBlock() instanceof BellowsBlock
                        && Driveline.isDrivenInto(level, beside)) return true;
            }
        }
        return false;
    }

    public static void syncLit(Level level, Structure structure, boolean lit) {
        for (BlockPos pos : structure.members) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof ForgeBlock && state.getValue(ForgeBlock.LIT) != lit) {
                level.setBlock(pos, state.setValue(ForgeBlock.LIT, lit), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * The structure no longer stands (its middle was filled, say) while every block is still there:
     * each part becomes its own forge again. The fire stays with the controller.
     */
    public static void dissolve(Level level, Structure structure) {
        breakUp(level, structure, null, controller(level, structure));
    }

    /** Break one member and all surviving blocks immediately become independent forges again. */
    public static void beforeRemove(Level level, BlockPos removed, ForgeBlockEntity removedForge) {
        if (!removedForge.isMultiblockPart()) return;
        Structure structure = fromLink(removedForge);
        if (structure == null) return;
        ForgeBlockEntity controller = removed.equals(structure.controller) ? removedForge : controller(level, structure);
        breakUp(level, structure, removed, controller);
    }

    private static void breakUp(Level level, Structure structure, BlockPos removed, ForgeBlockEntity controller) {
        List<ItemStack> contents = controller == null ? List.of() : controller.takeAll();
        List<ForgeBlockEntity> survivors = new ArrayList<>();
        for (BlockPos pos : structure.members) {
            if (level.getBlockEntity(pos) instanceof ForgeBlockEntity forge) {
                forge.unlink();
                if (!pos.equals(removed)) {
                    survivors.add(forge);
                    setPart(level, pos, 0);
                }
            }
        }
        if (controller != null && structure.controller.equals(removed) && !survivors.isEmpty()) {
            // The fire goes on in the block that takes the controller's place.
            survivors.getFirst().setThermalState(controller.localBurnTicks(), controller.localBurnTotal(),
                    controller.localTemperature());
            controller.setThermalState(0, 0, Heat.AMBIENT);
        }
        BlockPos spill = removed != null ? removed : structure.controller.above(structure.size.edge);
        for (ItemStack stack : contents) {
            boolean placed = false;
            for (ForgeBlockEntity survivor : survivors) {
                if (survivor.place(stack, 0)) {
                    placed = true;
                    break;
                }
            }
            if (!placed) drop(level, spill, stack);
        }
    }

    private static void drop(Level level, BlockPos at, ItemStack stack) {
        Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, stack);
    }
}
