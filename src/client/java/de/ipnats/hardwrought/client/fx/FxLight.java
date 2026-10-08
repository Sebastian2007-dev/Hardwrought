package de.ipnats.hardwrought.client.fx;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A coloured point light cast by an effect. It is added to the world's lighting in the shaders, per
 * pixel on terrain, so it moves smoothly, lights slabs and grass alike, and needs no light blocks.
 */
public final class FxLight {
    Vec3 pos;
    Vec3 prevPos;
    int age;
    int life;
    int color;
    float radius;
    float intensity = 1.4f;
    float flicker = 0.0f;
    /** Share of the life after which the light starts to fade. */
    float fadeFrom = 0.3f;
    final float seed;

    FxLight(Vec3 pos, int color, float radius, int life, float seed) {
        this.pos = pos;
        this.prevPos = pos;
        this.color = color;
        this.radius = radius;
        this.life = Math.max(1, life);
        this.seed = seed;
    }

    public FxLight fadeFrom(float share) {
        this.fadeFrom = share;
        return this;
    }

    /** How bright it is at its centre; around 1 matches full daylight on a white surface. */
    public FxLight intensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    /** How strongly it flickers, as fire does; 0 to 1. */
    public FxLight flicker(float flicker) {
        this.flicker = flicker;
        return this;
    }

    public FxLight moveTo(Vec3 pos) {
        this.pos = pos;
        return this;
    }

    /** Keep the light burning for at least this many more ticks. */
    public FxLight extend(int ticks) {
        this.life = Math.max(this.life, this.age + ticks);
        return this;
    }

    public void kill() {
        this.life = Math.min(this.life, this.age + 1);
    }

    Vec3 position(float partial) {
        return prevPos.lerp(pos, partial);
    }

    /** The intensity at this moment, with the fade-out and the flicker. */
    float currentIntensity(float partial, float seconds) {
        float t = Mth.clamp((age + partial) / life, 0, 1);
        float fade = t <= fadeFrom ? 1 : 1 - (t - fadeFrom) / (1 - fadeFrom);
        // A fast rise, so a light that appears with a flash does not pop in at full strength.
        float rise = Math.min(1, (age + partial) / 1.5f);
        float wobble = 1;
        if (flicker > 0) {
            wobble = 1 - flicker * (0.5f + 0.25f * Mth.sin(seconds * 23 + seed) + 0.25f * Mth.sin(seconds * 37 + seed * 3));
        }
        return intensity * fade * fade * rise * wobble;
    }
}
