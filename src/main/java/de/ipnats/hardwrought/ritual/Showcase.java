package de.ipnats.hardwrought.ritual;

import com.mojang.math.Transformation;
import de.ipnats.hardwrought.mixin.DisplayInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * An item shown hanging over a pedestal or the altar: vanilla's item display, turned always to face
 * whoever looks at it, lit as if by its own light. Large and whole for what lies there, small for what is
 * wanted there.
 */
final class Showcase {
    private Showcase() { }

    static UUID show(ServerLevel level, BlockPos over, double height, ItemStack stack, float scale) {
        Display.ItemDisplay display = new Display.ItemDisplay(net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY, level);
        display.snapTo(over.getX() + 0.5, over.getY() + height, over.getZ() + 0.5, 0, 0);
        display.getSlot(0).set(stack.copyWithCount(1));
        DisplayInvoker invoker = (DisplayInvoker) display;
        invoker.hardwrought$setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
        invoker.hardwrought$setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
        invoker.hardwrought$setBrightnessOverride(Brightness.FULL_BRIGHT);
        invoker.hardwrought$setViewRange(1.5f);
        display.addTag("hardwrought.showcase");
        level.addFreshEntity(display);
        return display.getUUID();
    }

    /** Every display over this block, whoever made it: what an earlier session left, before showing anew. */
    static void clear(ServerLevel level, BlockPos over) {
        for (Display.ItemDisplay display : level.getEntitiesOfClass(Display.ItemDisplay.class, new net.minecraft.world.phys.AABB(over).expandTowards(0, 2, 0),
                d -> d.entityTags().contains("hardwrought.showcase"))) {
            display.discard();
        }
    }

    static void remove(ServerLevel level, UUID id) {
        if (id == null) return;
        Entity entity = level.getEntity(id);
        if (entity != null) entity.discard();
    }
}
