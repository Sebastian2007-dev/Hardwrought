package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Inscribing a sigil (sigil specification): a drawing of two circles, cast with a wand at the
 * ground, is laid there as a sigil and keeps working by itself.
 *
 * <p>It is judged like a spell, with what sigils add: the figure in the inner circle must carry the
 * instructions in the outer one — every instruction beyond its corners shakes the sigil — and a
 * sigil without any figure has nothing to hold it together at all. What goes wrong goes wrong in the
 * sigil: weakened, turned aside, unstable — it gives way a few seconds later — or, worst, it bursts
 * where it was laid (sections 49 and 50).
 */
public final class Sigils {
    /** How far a sigil can be laid from the one inscribing it, in blocks. */
    static final double REACH = 6;

    private Sigils() { }

    /** Inscribes a sigil where the player looks. */
    static SpellCaster.Result inscribe(ServerPlayer player, ItemStack wand, WandTier tier, SigilDesign design) {
        ServerLevel level = player.level();
        BlockPos at = ground(player);
        if (at == null) {
            player.sendOverlayMessage(Component.translatable("magic.hardwrought.sigil_needs_ground").withStyle(ChatFormatting.GRAY));
            return null;
        }
        // Judged as a spell is: accuracy, wand, overload, and what the caster knows.
        double accuracy = design.accuracy();
        int overload = Math.max(0, design.tier() - tier.tier());
        double stability = accuracy + tier.steadiness() - 0.15 * overload - 0.25 * design.overflow() - 0.08 * design.noise();
        List<Glyph> unknown = new ArrayList<>();
        for (Glyph glyph : design.glyphs()) if (!RuneKnowledge.knows(player, glyph)) unknown.add(glyph);
        stability *= Math.pow(0.8, unknown.size());
        // The figure is what holds a sigil together; without one it barely holds at all.
        if (design.corners() < 3) stability *= 0.5;
        stability = Math.max(0, Math.min(1, stability));
        double power = (0.4 + 0.63 * accuracy) * (1 + 0.35 * design.strength());
        CastQuality.Outcome outcome;
        if (design.element() == null) outcome = CastQuality.Outcome.FIZZLE;
        else if (stability >= CastQuality.SUCCESS) outcome = CastQuality.Outcome.SUCCESS;
        else if (stability >= CastQuality.MINOR) outcome = CastQuality.Outcome.WEAKENED;
        else if (stability >= CastQuality.MEDIUM) outcome = design.overflow() > 0 || design.corners() < 3
                ? CastQuality.Outcome.SURGE : CastQuality.Outcome.ASTRAY;
        else outcome = CastQuality.Outcome.BACKFIRE;

        // The load passes through the wand.
        boolean broke = false;
        if (outcome != CastQuality.Outcome.FIZZLE) {
            int wear = WandTier.wear(overload);
            broke = overload >= WandTier.SHATTER || wand.getDamageValue() + wear >= wand.getMaxDamage();
            InteractionHand hand = player.getMainHandItem() == wand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            wand.hurtAndBreak(broke ? wand.getMaxDamage() : wear, player, hand);
            if (broke && outcome != CastQuality.Outcome.BACKFIRE) outcome = CastQuality.Outcome.SURGE;
        }
        player.getCooldowns().addCooldown(wand, 20 + 4 * design.cost());

        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z);
        forward = forward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 direction = design.directed()
                ? right.scale(design.dirX()).add(forward.scale(-design.dirY())).normalize() : Vec3.ZERO;
        Vec3 center = Vec3.atBottomCenterOf(at);

        switch (outcome) {
            case FIZZLE -> {
                Fx.play(level, FxEffect.SPARKLE, center, center, 0x8A8A8A, 0.6f, null);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
            }
            case BACKFIRE -> {
                // It bursts where it was to lie, and whoever drew it is close enough to feel it.
                SpellEffects.Release r = new SpellEffects.Release(level, null, design.element(), design.inverted(),
                        Spell.Placement.FRONT, new Vec3(0, 1, 0), 0, true, false, false, false, power, 2.5, 1);
                SpellEffects.burst(r, center.add(0, 0.5, 0));
            }
            default -> {
                if (outcome == CastQuality.Outcome.WEAKENED) power *= 0.6;
                // Turned aside: a hovering sigil gains a direction, a directed one turns a quarter round.
                if (outcome == CastQuality.Outcome.ASTRAY) {
                    direction = design.directed() ? new Vec3(-direction.z, 0, direction.x) : right;
                }
                long now = level.getGameTime();
                long unstableAt = outcome == CastQuality.Outcome.SURGE ? now + 60 + level.getRandom().nextInt(80) : -1;
                level.setBlockAndUpdate(at, Magic.SIGIL.defaultBlockState());
                if (level.getBlockEntity(at) instanceof SigilBlockEntity sigil) {
                    sigil.inscribe(design, design.element(), forward, direction, (float) power, player.getUUID(), now, unstableAt);
                }
                Fx.play(level, FxEffect.SPARKLE, center.add(0, 0.3, 0), center, design.element().color(), 1.4f, null);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 1.2f);
            }
        }

        List<Glyph> learned = new ArrayList<>();
        if (outcome != CastQuality.Outcome.FIZZLE && accuracy >= SpellCaster.LEARN) {
            for (Glyph glyph : unknown) if (RuneKnowledge.learn(player, glyph)) learned.add(glyph);
        }
        CastQuality quality = new CastQuality(accuracy, stability, power, outcome, -1);
        SpellCaster.Result result = new SpellCaster.Result(null, quality, learned, broke, design);
        tell(player, design, quality, learned, broke);
        return result;
    }

    /** The block above the ground looked at, if a sigil can lie there. */
    private static BlockPos ground(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(REACH)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos below = hit.getDirection() == Direction.UP ? hit.getBlockPos() : null;
        if (below == null) return null;
        BlockPos at = below.above();
        var level = player.level();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return null;
        if (!level.getBlockState(at).canBeReplaced() && !level.getBlockState(at).is(Magic.SIGIL)) return null;
        return at;
    }

    /** "Sigil: Fire · square 4/4 — accuracy …", and what went wrong. */
    private static void tell(ServerPlayer player, SigilDesign design, CastQuality q, List<Glyph> learned, boolean broke) {
        MutableComponent text = Component.translatable("magic.hardwrought.sigil").copy().append(Component.literal(": "));
        if (design.element() != null) {
            text.append(RuneKnowledge.knows(player, design.element())
                    ? Component.translatable(design.element().translationKey()).withColor(design.element().color())
                    : Component.literal("?"));
        }
        text.append(Component.literal(" · " + design.cost() + "/" + design.capacity()));
        text.append(Component.literal(" — ")).append(Component.translatable("magic.hardwrought.quality",
                Math.round(q.accuracy() * 100), Math.round(q.stability() * 100), Math.round(q.power() * 100)));
        if (q.outcome().failed()) {
            String key = q.outcome() == CastQuality.Outcome.SURGE ? "magic.hardwrought.sigil_unstable" : q.outcome().translationKey();
            text.append(Component.literal(" — ")).append(Component.translatable(key)
                    .withStyle(q.outcome() == CastQuality.Outcome.WEAKENED ? ChatFormatting.YELLOW : ChatFormatting.RED));
        }
        player.sendOverlayMessage(text);
        for (Glyph glyph : learned) {
            player.sendSystemMessage(Component.translatable("magic.hardwrought.rune_answered",
                    Component.translatable(glyph.translationKey()).withColor(glyph.color())).withStyle(ChatFormatting.ITALIC));
        }
        if (broke) player.sendSystemMessage(Component.translatable("magic.hardwrought.wand_broke").withStyle(ChatFormatting.RED));
    }
}
