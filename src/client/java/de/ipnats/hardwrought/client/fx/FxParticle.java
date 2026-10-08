package de.ipnats.hardwrought.client.fx;

/**
 * One particle of the FX engine. Unlike a vanilla particle it has no texture and no class of its
 * own: how it looks and moves is set on it with the fluent setters, and the engine does the rest.
 *
 * <p>All times are in ticks, all speeds in blocks per tick.
 */
public final class FxParticle {
    public enum Shape {
        /** A soft point of light with a hot core. */
        GLOW,
        /** A point of light stretched along its motion. */
        SPARK,
        /** A soft puff that covers instead of shining. */
        SMOKE,
        /** Invisible; the air shimmers where it is, as above a fire. */
        HEAT
    }

    double x, y, z;
    double prevX, prevY, prevZ;
    double vx, vy, vz;
    int age;
    int life = 20;

    Shape shape = Shape.GLOW;
    float sizeStart = 0.2f, sizeEnd = 0.0f;
    int colorStart = 0xFFFFFFFF, colorEnd = 0xFFFFFFFF;
    float alpha = 1.0f;
    float fadeIn = 0.0f, fadeOut = 0.5f;
    float hot = 0.6f;
    float glint = 0.0f;
    float twinkle = 0.0f;

    float drag = 0.96f;
    float gravity = 0.0f;
    float turbulence = 0.0f;
    boolean collide = false;
    float bounce = 0.4f;

    boolean attracted = false;
    double attractX, attractY, attractZ;
    float attract = 0.0f;
    float swirl = 0.0f;

    float stretch = 2.0f;
    float seed;
    /** How brightly the surroundings light this particle, for smoke; refreshed every few ticks. */
    float brightness = 1.0f;
    /** Smoke: how brightly it is lit from within at birth, fading over its life, as fire lights its smoke. */
    float emissive = 0.0f;

    FxParticle(double x, double y, double z, float seed) {
        this.x = this.prevX = x;
        this.y = this.prevY = y;
        this.z = this.prevZ = z;
        this.seed = seed;
    }

    public FxParticle velocity(double vx, double vy, double vz) {
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        return this;
    }

    public FxParticle life(int ticks) {
        this.life = Math.max(1, ticks);
        return this;
    }

    public FxParticle shape(Shape shape) {
        this.shape = shape;
        return this;
    }

    public FxParticle size(float start, float end) {
        this.sizeStart = start;
        this.sizeEnd = end;
        return this;
    }

    public FxParticle size(float size) {
        return size(size, size);
    }

    /** Colour at birth and at death, as ARGB; the alpha channel is ignored in favour of {@link #alpha}. */
    public FxParticle color(int start, int end) {
        this.colorStart = start;
        this.colorEnd = end;
        return this;
    }

    public FxParticle color(int color) {
        return color(color, color);
    }

    public FxParticle alpha(float alpha) {
        this.alpha = alpha;
        return this;
    }

    /** Share of the life spent fading in, and the share of it after which it fades out. */
    public FxParticle fade(float in, float outFrom) {
        this.fadeIn = in;
        this.fadeOut = outFrom;
        return this;
    }

    /** How white-hot the core burns, 0 to about 1.5. */
    public FxParticle hot(float hot) {
        this.hot = hot;
        return this;
    }

    /** How strongly the four-pointed glint shows, for sparkles. */
    public FxParticle glint(float glint) {
        this.glint = glint;
        return this;
    }

    /** How strongly the brightness flickers from tick to tick. */
    public FxParticle twinkle(float twinkle) {
        this.twinkle = twinkle;
        return this;
    }

    /** Share of the velocity kept each tick. */
    public FxParticle drag(float drag) {
        this.drag = drag;
        return this;
    }

    public FxParticle gravity(float gravity) {
        this.gravity = gravity;
        return this;
    }

    public FxParticle turbulence(float turbulence) {
        this.turbulence = turbulence;
        return this;
    }

    /** Bounce off solid blocks, keeping the given share of the speed. */
    public FxParticle collide(float bounce) {
        this.collide = true;
        this.bounce = bounce;
        return this;
    }

    /** Pull towards a point, and optionally circle around the vertical axis through it. */
    public FxParticle attract(double x, double y, double z, float strength, float swirl) {
        this.attracted = true;
        this.attractX = x;
        this.attractY = y;
        this.attractZ = z;
        this.attract = strength;
        this.swirl = swirl;
        return this;
    }

    /** Makes it shimmering heat of the given strength instead of something seen. */
    public FxParticle heat(float strength) {
        this.shape = Shape.HEAT;
        this.hot = strength;
        return this;
    }

    /** For smoke: lit from within at birth, fading over its life. */
    public FxParticle emissive(float emissive) {
        this.emissive = emissive;
        return this;
    }

    /** For sparks: how many ticks of motion the streak trails behind it. */
    public FxParticle stretch(float ticks) {
        this.stretch = ticks;
        return this;
    }

    public boolean alive() {
        return age < life;
    }
}
