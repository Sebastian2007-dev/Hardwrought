package de.ipnats.hardwrought.health;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModEffects;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What can be seen of a player's health from outside, by anyone looking at them: a wound, a bandage
 * over it, a sick face. Worked out on the server once a second and sent to everyone, who draw it on the
 * player; nothing more of what they have is given away.
 */
public record Appearance(boolean wound, boolean bandaged, boolean sick) {
    public static final Appearance WELL = new Appearance(false, false, false);

    public static final StreamCodec<ByteBuf, Appearance> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, Appearance::wound, ByteBufCodecs.BOOL, Appearance::bandaged,
            ByteBufCodecs.BOOL, Appearance::sick, Appearance::new);

    public static final AttachmentType<Appearance> SHOWN = AttachmentRegistry.create(Hardwrought.id("appearance"),
            builder -> builder.syncWith(STREAM_CODEC, AttachmentSyncPredicate.all()));

    public static Appearance of(Player player) {
        Appearance shown = player.getAttached(SHOWN);
        return shown == null ? WELL : shown;
    }

    /** What the player looks like now: an open or infected wound, a bandage, a sick face. */
    static void update(ServerPlayer player) {
        Health health = Diseases.health(player);
        Health.Infection wound = health.infection(Disease.WOUND);
        Appearance now = new Appearance(player.hasEffect(ModEffects.OPEN_WOUND) || wound != null && wound.felt(),
                player.hasEffect(ModEffects.BANDAGED), health.sick());
        if (now.equals(WELL)) {
            if (player.getAttached(SHOWN) != null) player.removeAttached(SHOWN);
        } else if (!now.equals(player.getAttached(SHOWN))) {
            player.setAttached(SHOWN, now);
        }
    }
}
