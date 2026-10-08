package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.core.networking.GrindingPayloads;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Working a part over at a grindstone: the last of the craft, after the hammer or the cast.
 *
 * <p>A part can be put to the stone {@link #PASSES} times, and then there is no more metal to take
 * off. Each pass is one moment of judgement — the piece has to meet the stone at the right instant
 * (see the grinding screen) — and how well it was judged is what the pass is worth: a clean pass
 * polishes, a clumsy one hardly does, and a bad one scratches what earlier passes had done.
 *
 * <p>The polish is its own number, added to what the smith made of the piece at the anvil, and it
 * can carry a part beyond the best the anvil gives: a perfectly forged part, perfectly ground, stands
 * at {@code 100 % + } {@link #MAX_POLISH}. A part out of a cast starts rough and ends, at best, a
 * good ordinary part.
 */
public final class Grinding {
    /** What a pass was worth. */
    public enum Outcome {
        /** Off the mark: a scratch, which costs polish. */
        MISS(-0.02f),
        /** Near the mark. */
        POOR(0.01f),
        /** On the mark. */
        GOOD(0.03f),
        /** Dead on. */
        PERFECT(0.05f);

        private final float gain;

        Outcome(float gain) {
            this.gain = gain;
        }

        /** The polish this pass adds, or takes. */
        public float gain() {
            return gain;
        }
    }

    /** How often one part can be put to the stone. */
    public static final int PASSES = 8;
    /** The most polish a part can have: every pass dead on. */
    public static final float MAX_POLISH = PASSES * Outcome.PERFECT.gain;
    /** How far from the stone a pass still counts. */
    private static final double REACH = 6.0;

    private Grinding() { }

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !level.getBlockState(hit.getBlockPos()).is(Blocks.GRINDSTONE)) {
                return InteractionResult.PASS;
            }
            ItemStack stack = player.getMainHandItem();
            // Rust comes off first, in its own pass (see Rust).
            if (!isPart(stack) || Rust.of(stack) > 0) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer grinder)) return InteractionResult.SUCCESS;
            if (passesLeft(stack) <= 0) {
                grinder.sendOverlayMessage(Component.translatable("message.hardwrought.ground_out"));
            } else if (ServerPlayNetworking.canSend(grinder, GrindingPayloads.Open.TYPE)) {
                ServerPlayNetworking.send(grinder, new GrindingPayloads.Open(hit.getBlockPos()));
            }
            return InteractionResult.SUCCESS;
        });
        ServerPlayNetworking.registerGlobalReceiver(GrindingPayloads.Pass.TYPE,
                (payload, context) -> pass(context.player(), payload.grindstone(), payload.outcome()));
    }

    /** A forged or cast part: the only thing a grindstone works over. */
    public static boolean isPart(ItemStack stack) {
        return ToolParts.metalOf(stack.getItem()) != null && ForgeQuality.of(stack) != null;
    }

    /** How often this piece can still be put to the stone. */
    public static int passesLeft(ItemStack stack) {
        ForgeQuality quality = ForgeQuality.of(stack);
        return quality == null ? 0 : Math.max(0, PASSES - quality.passes());
    }

    /** One pass of the part in the player's hand over this grindstone, judged by the player's own screen. */
    static void pass(ServerPlayer player, BlockPos grindstone, Outcome outcome) {
        ServerLevel level = (ServerLevel) player.level();
        ItemStack stack = player.getMainHandItem();
        if (!isPart(stack) || !level.getBlockState(grindstone).is(Blocks.GRINDSTONE)
                || player.distanceToSqr(grindstone.getX() + 0.5, grindstone.getY() + 0.5, grindstone.getZ() + 0.5) > REACH * REACH
                || !grind(stack, outcome)) {
            return;
        }
        float pitch = switch (outcome) {
            case MISS -> 0.6f;
            case POOR -> 0.9f;
            case GOOD -> 1.15f;
            case PERFECT -> 1.4f;
        };
        level.playSound(null, grindstone, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8f, pitch);
        level.sendParticles(outcome == Outcome.MISS ? ParticleTypes.SMOKE : ParticleTypes.CRIT, grindstone.getX() + 0.5,
                grindstone.getY() + 0.9, grindstone.getZ() + 0.5, outcome == Outcome.PERFECT ? 14 : 6, 0.2, 0.1, 0.2, 0.15);
    }

    /** One pass, worth this much. False where the piece cannot be ground, or not any more. */
    public static boolean grind(ItemStack stack, Outcome outcome) {
        ForgeQuality quality = ForgeQuality.of(stack);
        if (quality == null || quality.passes() >= PASSES) return false;
        stack.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(quality.craftsmanship(), quality.treatment(),
                Math.clamp(quality.polish() + outcome.gain(), 0f, MAX_POLISH), quality.passes() + 1));
        return true;
    }
}
