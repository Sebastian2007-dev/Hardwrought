package de.ipnats.hardwrought.mobs;

import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * § 39.1: the woods a creaking heart can grow in besides pale oak. Pale oak keeps vanilla's own
 * heart; every other heart wears the bark of the trunk it sits in, and so does the creaking it calls.
 */
public enum HeartWood implements StringRepresentable {
    OAK("oak", BlockItemTags.OAK_LOGS.block()),
    SPRUCE("spruce", BlockItemTags.SPRUCE_LOGS.block()),
    BIRCH("birch", BlockItemTags.BIRCH_LOGS.block()),
    JUNGLE("jungle", BlockItemTags.JUNGLE_LOGS.block()),
    ACACIA("acacia", BlockItemTags.ACACIA_LOGS.block()),
    DARK_OAK("dark_oak", BlockItemTags.DARK_OAK_LOGS.block()),
    MANGROVE("mangrove", BlockItemTags.MANGROVE_LOGS.block()),
    CHERRY("cherry", BlockItemTags.CHERRY_LOGS.block()),
    POPLAR("poplar", BlockItemTags.POPLAR_LOGS.block());

    public static final StringRepresentable.EnumCodec<HeartWood> CODEC = StringRepresentable.fromEnum(HeartWood::values);

    private final String name;
    private final TagKey<Block> logs;

    HeartWood(String name, TagKey<Block> logs) {
        this.name = name;
        this.logs = logs;
    }

    public TagKey<Block> logs() {
        return logs;
    }

    /** The wood a log belongs to, or null for pale oak and anything that is not a log. */
    public static @Nullable HeartWood of(BlockState state) {
        for (HeartWood wood : values()) {
            if (state.is(wood.logs)) return wood;
        }
        return null;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
