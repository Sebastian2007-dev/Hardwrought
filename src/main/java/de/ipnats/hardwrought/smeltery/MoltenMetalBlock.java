package de.ipnats.hardwrought.smeltery;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The look of molten metal: one state per metal of {@link MoltenMetals#ORDER}. Never placed in the
 * world — the smeltery, the faucet and the casting table draw it, scaled to how much there is.
 */
public class MoltenMetalBlock extends Block {
    public static final IntegerProperty METAL = IntegerProperty.create("metal", 0, MoltenMetals.ORDER.size() - 1);

    public MoltenMetalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(METAL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(METAL);
    }

    public static BlockState of(String material) {
        return SmelteryBlocks.MOLTEN_METAL.defaultBlockState().setValue(METAL, MoltenMetals.index(material));
    }
}
