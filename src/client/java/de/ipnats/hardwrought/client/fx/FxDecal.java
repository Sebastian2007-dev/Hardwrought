package de.ipnats.hardwrought.client.fx;

import net.minecraft.world.phys.Vec3;

/**
 * A single large primitive: a shockwave ring, a magic circle, a shield or a flash of light. It lies
 * flat on the plane given by its normal, or faces the camera when it has none.
 */
public final class FxDecal {
    public enum Kind {
        RING, RUNE, SHIELD, GLOW,
        /** A true sphere, built of quads all round, so it is seen from inside as well as out. */
        SPHERE,
        /** The Witch King's sigil: a crown of thorns, a seven-pointed star, eyes. Flat, spins, is drawn in. */
        HEX_SIGIL,
        /** His shockwave's front, flat on the ground; param unused, its life drives it. */
        HEX_WAVE,
        /** His ward, a cell-shelled sphere facing the camera; param is how cracked it is. */
        HEX_WARD,
        /** A rift standing upright, turned to face the camera; it opens and closes over its life. */
        HEX_RIFT,
        /** A spectral sword along {@link FxDecal#axis}, turned about it to face the camera; param is its strength. */
        HEX_BLADE,
        /** Bends the view instead of shining: a shockwave's ring of refraction. */
        WARP_RING,
        /** Shimmering heat. */
        WARP_HEAT,
        /** The air twisted around the centre. */
        WARP_SWIRL,
        /** The view pulled in towards the centre. */
        WARP_LENS;

        boolean warps() {
            return ordinal() >= WARP_RING.ordinal();
        }
    }

    final Kind kind;
    /** HEX_BLADE: the direction the sword points. */
    Vec3 axis;
    Vec3 center;
    /** Where the centre was last tick, so a moving decal is drawn gliding rather than jumping. */
    Vec3 prevCenter;
    Vec3 normal;
    int age;
    int life = 20;
    int color = 0xFFFFFFFF;
    float alpha = 1.0f;
    float radiusStart = 1.0f, radiusEnd = 1.0f;
    /** Ease the radius out, so a shockwave bursts fast and slows. */
    boolean easeOut = true;
    /** Ticks the radius takes to grow; 0 grows over the whole life. */
    int grow = 0;
    float fadeIn = 0.0f, fadeOut = 0.6f;
    /** RING: band width. SHIELD: lattice strength. GLOW: core heat. WARP_*: how strongly it bends the view. */
    float param = 0.2f;
    float spin = 0.0f;
    /** RUNE: how many ticks it takes to draw the circle. */
    int reveal = 0;
    int seed;

    FxDecal(Kind kind, Vec3 center, Vec3 normal) {
        this.kind = kind;
        this.center = center;
        this.prevCenter = center;
        this.normal = normal == null ? null : normal.normalize();
    }

    public FxDecal life(int ticks) {
        this.life = Math.max(1, ticks);
        return this;
    }

    public FxDecal color(int color) {
        this.color = color;
        return this;
    }

    public FxDecal alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    public FxDecal radius(float start, float end) {
        this.radiusStart = start;
        this.radiusEnd = end;
        return this;
    }

    public FxDecal radius(float radius) {
        return radius(radius, radius);
    }

    /** Reach the end radius after this many ticks and stay there. */
    public FxDecal grow(int ticks) {
        this.grow = ticks;
        return this;
    }

    public FxDecal linear() {
        this.easeOut = false;
        return this;
    }

    public FxDecal fade(float in, float outFrom) {
        this.fadeIn = in;
        this.fadeOut = outFrom;
        return this;
    }

    public FxDecal param(float param) {
        this.param = param;
        return this;
    }

    /** Radians per tick. */
    public FxDecal spin(float spin) {
        this.spin = spin;
        return this;
    }

    public FxDecal reveal(int ticks) {
        this.reveal = ticks;
        return this;
    }

    public FxDecal seed(int seed) {
        this.seed = seed;
        return this;
    }

    public FxDecal center(Vec3 center) {
        this.center = center;
        return this;
    }

    /** Place it without gliding there from where it was. */
    public FxDecal teleport(Vec3 center) {
        this.center = center;
        this.prevCenter = center;
        return this;
    }

    /** HEX_BLADE: the direction the sword points. */
    public FxDecal axis(Vec3 axis) {
        this.axis = axis.normalize();
        return this;
    }

    /** Keeps it alive for at least this many more ticks: for a decal that follows something for as long as it lasts. */
    public FxDecal keep(int ticks) {
        this.life = Math.max(this.life, this.age + ticks);
        return this;
    }

    public boolean alive() {
        return age < life;
    }

    /** Let it disappear after this tick. */
    public void kill() {
        this.life = Math.min(this.life, this.age + 1);
    }
}
