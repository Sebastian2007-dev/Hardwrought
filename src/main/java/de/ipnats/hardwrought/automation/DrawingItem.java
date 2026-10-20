package de.ipnats.hardwrought.automation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.carried.AssemblyBenchBlock;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.knowledge.KnowledgeLevel;
import de.ipnats.hardwrought.progression.KeptGridBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A drawing (stage E5): what an assembler makes, and from what. A blank one used on an assembly bench
 * copies the work laid out on the bench — but only of a thing its maker has studied: a drawing is drawn
 * from knowing how a thing goes together, not from looking at the parts. One drawing, one thing.
 */
public class DrawingItem extends Item {
    /** The recipe drawn and the thing it makes. */
    public record Plan(Identifier recipe, ItemStack result) {
        public static final Codec<Plan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("recipe").forGetter(Plan::recipe),
                ItemStack.CODEC.fieldOf("result").forGetter(Plan::result)).apply(instance, Plan::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Plan> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Plan::recipe, ItemStack.STREAM_CODEC, Plan::result, Plan::new);
    }

    public static final DataComponentType<Plan> PLAN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("drawing"), DataComponentType.<Plan>builder().persistent(Plan.CODEC).networkSynchronized(Plan.STREAM_CODEC).build());

    public DrawingItem(Properties properties) {
        super(properties);
    }

    /** The plan drawn on a drawing, or null for a blank one. */
    public static Plan plan(ItemStack stack) {
        return stack.get(PLAN);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (stack.has(PLAN) || !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof AssemblyBenchBlock)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof KeptGridBlockEntity grid)) return InteractionResult.FAIL;
        CraftingInput input = CraftingInput.of(3, 3, grid.contents());
        Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (found.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.drawing.nothing"));
            return InteractionResult.FAIL;
        }
        ItemStack result = found.get().value().assemble(input);
        if (CoreLifecycle.require(level.getServer()).knowledge().level(player, result.getItem()) != KnowledgeLevel.STUDIED) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.drawing.unknown", result.getHoverName()));
            return InteractionResult.FAIL;
        }
        ItemStack drawing = stack.split(1);
        drawing.set(PLAN, new Plan(found.get().id().identifier(), result.copy()));
        if (!player.getInventory().add(drawing)) player.spawnAtLocation(level, drawing);
        level.playSound(null, context.getClickedPos(), SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        player.sendOverlayMessage(Component.translatable("message.hardwrought.drawing.drawn", result.getHoverName()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Plan plan = plan(stack);
        builder.accept(plan == null ? Component.translatable("tooltip.hardwrought.drawing.blank").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.hardwrought.drawing.of", plan.result().getCount(), plan.result().getHoverName())
                        .withStyle(ChatFormatting.AQUA));
    }
}
