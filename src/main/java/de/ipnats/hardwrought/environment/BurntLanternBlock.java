package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A lantern whose flame has gone out: the same lantern, standing or hanging, with dark glass. Which
 * lantern it was — iron, soul, or copper at any stage of green — is kept, so it looks the part and
 * lights again as itself. See {@link BurntLight}.
 */
public class BurntLanternBlock extends LanternBlock implements BurntLight {
    public enum Variant implements StringRepresentable {
        LANTERN("lantern"),
        SOUL_LANTERN("soul_lantern"),
        COPPER_LANTERN("copper_lantern"),
        EXPOSED_COPPER_LANTERN("exposed_copper_lantern"),
        WEATHERED_COPPER_LANTERN("weathered_copper_lantern"),
        OXIDIZED_COPPER_LANTERN("oxidized_copper_lantern");

        private final String name;

        Variant(String name) {
            this.name = name;
        }

        /** The look a lantern block has; a waxed one looks as its unwaxed self. */
        public static Variant of(Block lantern) {
            String path = BuiltInRegistries.BLOCK.getKey(lantern).getPath().replace("waxed_", "");
            for (Variant variant : values()) {
                if (variant.name.equals(path)) return variant;
            }
            return LANTERN;
        }

        public Block lit() {
            return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Variant> VARIANT = EnumProperty.create("variant", Variant.class);

    public BurntLanternBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, Variant.LANTERN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT);
    }

    @Override
    public BlockState relit(BlockState burnt) {
        return burnt.getValue(VARIANT).lit().defaultBlockState()
                .setValue(HANGING, burnt.getValue(HANGING)).setValue(WATERLOGGED, burnt.getValue(WATERLOGGED));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        return BurntLight.light(stack, state, level, pos, player, hand);
    }
}
