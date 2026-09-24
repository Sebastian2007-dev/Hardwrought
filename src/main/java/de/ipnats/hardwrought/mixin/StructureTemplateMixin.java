package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import de.ipnats.hardwrought.progression.StructureLoot;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * Every structure built from a template — villages, igloos, shipwrecks, ruins — goes through this
 * one list of blocks on its way into the world. The furnishings {@link StructureLoot} names are
 * taken out of it here.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
    @ModifyReturnValue(method = "processBlockInfos", at = @At("RETURN"))
    private static List<StructureTemplate.StructureBlockInfo> hardwrought$unfurnished(
            List<StructureTemplate.StructureBlockInfo> infos) {
        boolean any = false;
        for (StructureTemplate.StructureBlockInfo info : infos) {
            if (StructureLoot.stripped(info.state())) {
                any = true;
                break;
            }
        }
        if (!any) return infos;
        List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(infos.size());
        for (StructureTemplate.StructureBlockInfo info : infos) {
            result.add(StructureLoot.stripped(info.state())
                    ? new StructureTemplate.StructureBlockInfo(info.pos(), StructureLoot.replacement(info.state()), null)
                    : info);
        }
        return result;
    }
}
