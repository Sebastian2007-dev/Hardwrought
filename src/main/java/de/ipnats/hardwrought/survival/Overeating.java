package de.ipnats.hardwrought.survival;

import com.mojang.serialization.Codec;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Eating past full. A player can eat on when the hunger bar is full, up to as much again as the bar
 * holds; what does not fit in the bar is kept as being stuffed, and is digested first — the bar does not
 * fall while there is any of it left.
 *
 * <p>A full belly is felt: a little slower and a little shorter of breath at a quarter of what more fits,
 * more so at half, and at three quarters too heavy to jump or to climb so much as a step. The body shows
 * it too: a stuffed player is drawn broader. The amount is kept on the player and sent to everyone, who
 * need it to draw them.
 */
public final class Overeating {
    /** How much more than a full bar can be eaten, in hunger points: the bar again. */
    public static final double MOST = 20.0;
    /** How much is digested a second while the bar has room for it. */
    static final double DIGESTED_PER_SECOND = 1.0;

    public static final AttachmentType<Double> STUFFED = AttachmentRegistry.create(Hardwrought.id("stuffed"), builder ->
            builder.persistent(Codec.DOUBLE).syncWith(ByteBufCodecs.DOUBLE, AttachmentSyncPredicate.all()));

    private static final Identifier SPEED = Hardwrought.id("stuffed_movement_penalty");
    private static final Identifier JUMP = Hardwrought.id("stuffed_jump_penalty");
    private static final Identifier STEP = Hardwrought.id("stuffed_step_penalty");
    private static final double[] SLOWER = {0, 0.08, 0.18, 0.30};
    private static final double[] SHORTER_OF_BREATH = {1.0, 1.15, 1.35, 1.6};

    private Overeating() { }

    /** Touching the class registers the attachment. */
    public static void initialize() { }

    /** How much is eaten past full, in hunger points. */
    public static double stuffed(Player player) {
        Double value = player.getAttached(STUFFED);
        return value == null ? 0 : value;
    }

    /** How stuffed, 0 for not at all to 1 for as full as a body gets. */
    public static double fullness(Player player) {
        return Math.clamp(stuffed(player) / MOST, 0.0, 1.0);
    }

    static void set(Player player, double stuffed) {
        double value = Math.clamp(stuffed, 0, MOST);
        if (value < 0.01) player.removeAttached(STUFFED);
        else player.setAttached(STUFFED, value);
    }

    /** Whether another mouthful fits, past a full bar. */
    public static boolean roomFor(Player player) {
        return stuffed(player) < MOST;
    }

    /**
     * How full it is felt: 0 not at all, 1 from a quarter of what fits past full, 2 from half, 3 from
     * three quarters — too heavy to jump or to climb a step.
     */
    public static int stage(Player player) {
        double full = fullness(player);
        return full >= 0.75 ? 3 : full >= 0.5 ? 2 : full >= 0.25 ? 1 : 0;
    }

    /** How much harder every effort is on a full belly. */
    public static double staminaFactor(Player player) {
        return SHORTER_OF_BREATH[stage(player)];
    }

    /** Something eaten: what does not fit in the bar is kept. Called before the bar is filled. */
    public static void ate(ServerPlayer player, int nutrition) {
        int over = player.getFoodData().getFoodLevel() + nutrition - 20;
        if (over <= 0) return;
        int before = stage(player);
        set(player, stuffed(player) + over);
        if (stage(player) > before) {
            player.sendOverlayMessage(Component.translatable("message.hardwrought.stuffed." + stage(player)));
        }
    }

    /** One second: what is kept is digested into the bar as the bar has room, and a full belly weighs. */
    public static void tick(ServerPlayer player) {
        double stuffed = stuffed(player);
        if (stuffed > 0) {
            var food = player.getFoodData();
            double digested = Math.min(Math.min(stuffed, DIGESTED_PER_SECOND), 20 - food.getFoodLevel());
            if (digested >= 1) {
                food.setFoodLevel(food.getFoodLevel() + (int) digested);
                set(player, stuffed - (int) digested);
            }
        }
        int stage = stage(player);
        modifier(player, Attributes.MOVEMENT_SPEED, SPEED, -SLOWER[stage], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.JUMP_STRENGTH, JUMP, stage >= 3 ? -1.0 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.STEP_HEIGHT, STEP, stage >= 3 ? -1.0 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** Clears what a full belly does, for a player the survival rules do not touch. */
    public static void clear(ServerPlayer player) {
        modifier(player, Attributes.MOVEMENT_SPEED, SPEED, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.JUMP_STRENGTH, JUMP, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(player, Attributes.STEP_HEIGHT, STEP, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount,
                                 AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier present = instance.getModifier(id);
        if (amount == 0) {
            if (present != null) instance.removeModifier(id);
            return;
        }
        if (present != null && present.amount() == amount) return;
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }
}
