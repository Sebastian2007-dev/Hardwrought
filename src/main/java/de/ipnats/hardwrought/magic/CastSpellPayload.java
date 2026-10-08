package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * A drawing sent to be cast: the strokes as the hand drew them, not what the client thought they
 * were. The server reads them itself.
 *
 * @param strokes every rune, in order
 * @param size the side of the surface it was drawn on, which an arrow's length is measured against
 * @param action whether it was drawn in action casting (section 11) rather than on the surface
 */
public record CastSpellPayload(List<Stroke> strokes, float size, boolean action) implements CustomPacketPayload {
    /** No stroke carries more points than this; the recognizer resamples anyway. */
    public static final int MAX_POINTS = 256;

    public static final Type<CastSpellPayload> TYPE = new Type<>(Hardwrought.id("cast_spell_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CastSpellPayload> CODEC = new StreamCodec<>() {
        @Override
        public CastSpellPayload decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > Spell.MAX_STROKES) throw new io.netty.handler.codec.DecoderException("Too many runes: " + count);
            List<Stroke> strokes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int points = buffer.readVarInt();
                if (points < 0 || points > MAX_POINTS) throw new io.netty.handler.codec.DecoderException("Too many points: " + points);
                float[] xs = new float[points], ys = new float[points];
                for (int p = 0; p < points; p++) {
                    xs[p] = buffer.readFloat();
                    ys[p] = buffer.readFloat();
                }
                strokes.add(new Stroke(xs, ys, buffer.readVarInt()));
            }
            return new CastSpellPayload(strokes, buffer.readFloat(), buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, CastSpellPayload payload) {
            int count = Math.min(payload.strokes().size(), Spell.MAX_STROKES);
            buffer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                Stroke stroke = payload.strokes().get(i);
                int points = Math.min(stroke.xs().length, MAX_POINTS);
                buffer.writeVarInt(points);
                for (int p = 0; p < points; p++) {
                    buffer.writeFloat(stroke.xs()[p]);
                    buffer.writeFloat(stroke.ys()[p]);
                }
                buffer.writeVarInt(stroke.millis());
            }
            buffer.writeFloat(payload.size());
            buffer.writeBoolean(payload.action());
        }
    };

    /** One stroke: its points, y downward, and how long it took to draw. */
    public record Stroke(float[] xs, float[] ys, int millis) { }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
