package de.ipnats.hardwrought.ritual;

import de.ipnats.hardwrought.end.dragon.DragonHeart;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The altar in the middle of a temple's ritual chamber. Laid on it, the dragon's heart begins the ritual
 * — but only once every pedestal round it carries its offering. Afterwards it is quiet, the heart hanging
 * over it for good.
 */
public class RitualAltar extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(2, 4, 2, 14, 12, 14), Block.box(0, 12, 0, 16, 16, 16));

    public RitualAltar(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, RitualContent.ALTAR_ENTITY, (l, pos, s, altar) -> altar.tick((ServerLevel) l));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(DragonHeart.HEART) || !(level.getBlockEntity(pos) instanceof Entity altar)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (altar.done || Ritual.running(server, pos)) return InteractionResult.FAIL;
        if (!altar.temple(server, player)) return InteractionResult.FAIL;
        List<Integer> missing = altar.missing(server);
        if (!missing.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.ritual.missing", missing.size(),
                    RitualContent.offering(missing.getFirst()).getDefaultInstance().getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE));
            server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.6f);
            return InteractionResult.FAIL;
        }
        altar.heart = stack.consumeAndReturn(1, player);
        altar.showing = false;
        altar.setChanged();
        Ritual.begin(server, altar);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity altar) || !(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (altar.done) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.ritual.done").withStyle(ChatFormatting.LIGHT_PURPLE));
            return InteractionResult.SUCCESS;
        }
        // The heart already laid, its ritual cut short (the world was left): it begins again.
        if (!altar.heart.isEmpty() && !Ritual.running(server, pos) && altar.missing(server).isEmpty() && altar.temple(server, player)) {
            Ritual.begin(server, altar);
            return InteractionResult.SUCCESS;
        }
        Temple.Survey survey = Temple.survey(server, pos);
        if (!survey.stands()) {
            altar.temple(server, player);
            return InteractionResult.SUCCESS;
        }
        List<Integer> missing = altar.missing(server);
        player.sendOverlayMessage(missing.isEmpty()
                ? Component.translatable("message.hardwrought.ritual.ready").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.translatable("message.hardwrought.ritual.missing", missing.size(),
                        RitualContent.offering(missing.getFirst()).getDefaultInstance().getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        return InteractionResult.SUCCESS;
    }

    /** The heart, whether the ritual was done, and the temple it stands in. */
    public static class Entity extends BlockEntity {
        ItemStack heart = ItemStack.EMPTY;
        boolean done, showing;
        UUID shown;
        int theme;

        public Entity(BlockPos pos, BlockState state) {
            super(RitualContent.ALTAR_ENTITY, pos, state);
        }

        public boolean done() {
            return done;
        }

        public Temple.Theme theme() {
            return Temple.Theme.values()[Math.clamp(theme, 0, Temple.Theme.values().length - 1)];
        }

        /**
         * Whether one of the temples stands round it, telling the player how far it is along where it does
         * not, and the nearest block still wrong; and which temple it is.
         */
        boolean temple(ServerLevel level, Player player) {
            Temple.Survey survey = Temple.survey(level, getBlockPos());
            if (survey.stands()) {
                theme = survey.theme().ordinal();
                setChanged();
                // The few strays a standing temple may have are not worth marking: whatever was marked is cleared.
                if (player instanceof net.minecraft.server.level.ServerPlayer asking
                        && ServerPlayNetworking.canSend(asking, TempleFaults.Faults.TYPE)) {
                    ServerPlayNetworking.send(asking, new TempleFaults.Faults(getBlockPos(), theme, List.of()));
                }
                return true;
            }
            BlockPos wrong = survey.nearestWrong();
            player.sendOverlayMessage(Component.translatable("message.hardwrought.ritual.temple",
                    Component.translatable("multiblock.hardwrought." + survey.theme().id() + "_temple"), (int) Math.floor(survey.share() * 100),
                    survey.wrong(), wrong == null ? "-" : wrong.toShortString()).withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.6f);
            // And every block still wrong marked red in the world for them.
            if (player instanceof net.minecraft.server.level.ServerPlayer asking) TempleFaults.show(level, getBlockPos(), survey.theme(), asking);
            return false;
        }

        List<RitualPedestal.Entity> pedestals(ServerLevel level) {
            List<RitualPedestal.Entity> found = new ArrayList<>();
            for (BlockPos at : Temple.pedestals(getBlockPos())) {
                if (level.getBlockEntity(at) instanceof RitualPedestal.Entity pedestal) found.add(pedestal);
            }
            return found;
        }

        /** The offerings still wanted, by their index. */
        List<Integer> missing(ServerLevel level) {
            List<Integer> missing = new ArrayList<>();
            List<RitualPedestal.Entity> found = pedestals(level);
            for (int i = 0; i < RitualContent.OFFERINGS.size(); i++) {
                int index = i;
                if (found.stream().noneMatch(p -> p.index == index && p.filled())) missing.add(i);
            }
            return missing;
        }

        void finish() {
            done = true;
            setChanged();
        }

        /** Set into its level, placed or loaded, it holds its temple up from then on. */
        @Override
        public void setLevel(Level level) {
            super.setLevel(level);
            if (level instanceof ServerLevel server) TempleSupport.standing(server, getBlockPos());
        }

        void tick(ServerLevel level) {
            if (showing || heart.isEmpty()) return;
            showing = true;
            Showcase.clear(level, getBlockPos());
            Showcase.remove(level, shown);
            shown = Showcase.show(level, getBlockPos(), done ? 2.4 : 1.6, heart, done ? 1.2f : 0.9f);
            setChanged();
        }

        /** The heart lifted to where it hangs at the height of the ritual. */
        void raise(ServerLevel level, double height, float scale) {
            Showcase.remove(level, shown);
            Showcase.clear(level, getBlockPos());
            shown = Showcase.show(level, getBlockPos(), height, heart, scale);
        }

        /** Lays the heart on it and begins the ritual, as a player would; false where it may not begin; for tests. */
        public boolean beginForTesting(ServerLevel level) {
            Temple.Survey survey = Temple.survey(level, getBlockPos());
            if (!survey.stands() || !missing(level).isEmpty()) return false;
            theme = survey.theme().ordinal();
            heart = new ItemStack(DragonHeart.HEART);
            Ritual.begin(level, this);
            return true;
        }

        /** Broken before its ritual was done: the heart falls where it stood. */
        @Override
        public void preRemoveSideEffects(BlockPos pos, BlockState state) {
            if (level != null && !heart.isEmpty() && !done) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, heart);
                heart = ItemStack.EMPTY;
            }
            super.preRemoveSideEffects(pos, state);
        }

        @Override
        public void setRemoved() {
            if (level instanceof ServerLevel server) TempleSupport.gone(server, getBlockPos());
            if (level instanceof ServerLevel server) Showcase.remove(server, shown);
            super.setRemoved();
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            if (!heart.isEmpty()) output.store("heart", ItemStack.CODEC, heart);
            output.putBoolean("done", done);
            output.putInt("theme", theme);
            if (shown != null) output.store("shown", UUIDUtil.CODEC, shown);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            heart = input.read("heart", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            done = input.getBooleanOr("done", false);
            theme = input.getIntOr("theme", 0);
            shown = input.read("shown", UUIDUtil.CODEC).orElse(null);
            showing = false;
        }
    }
}
