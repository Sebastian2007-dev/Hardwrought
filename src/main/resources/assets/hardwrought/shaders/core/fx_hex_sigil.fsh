#version 330
#extension GL_ARB_separate_shader_objects : require

// The Witch King's sigil: not a scholar's circle but a crown of thorns. An outer ring whose thorns point
// inwards, a seven-pointed star drawn in one stroke ({7/3}), seven small eyes riding a ring between them,
// and in the middle a slit of an eye that opens as the sigil fills. Lightning crawls along the star.
// params.x: rotation in radians. params.y: seed in its whole part, how far the sigil is drawn (0..1) in
// its fraction — the strokes run in round the circle as it is drawn.

#include <hardwrought:fx.glsl>

vec2 rot(vec2 p, float a) {
    float c = cos(a), s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

vec2 corner(int i, float r) {
    float a = FX_TAU * float(i) / 7.0 + FX_TAU * 0.25;
    return vec2(cos(a), sin(a)) * r;
}

void main() {
    float r = length(local);
    if (r >= 1.0) discard;
    float seed = floor(params.y);
    float drawn = fract(params.y);
    float spin = params.x;
    float t = fx_time();
    float angle = atan(local.y, local.x);
    float around = fract(angle / FX_TAU + 0.25);
    // How much of the sigil is laid down: strokes appear round the circle as it is drawn.
    float laid = smoothstep(around - 0.04, around, drawn) + step(0.998, drawn);
    laid = clamp(laid, 0.0, 1.0);

    float light = 0.0;
    float hot = 0.0;

    // The outer ring, double, and its thorns pointing in.
    light += fx_line(r - 0.96, 0.012) + fx_line(r - 0.9, 0.006);
    vec2 q = rot(local, spin * 0.6);
    float thornAngle = atan(q.y, q.x);
    float cells = 28.0;
    float cell = fract(thornAngle / FX_TAU * cells) - 0.5;
    // A thorn: a wedge, wide where it grows from the ring and coming to a point inwards. Its edges glow.
    float width = clamp((r - 0.76) / 0.14, 0.0, 1.0) * 0.32;
    float inThorn = r < 0.9 && r > 0.76 ? 1.0 : 0.0;
    light += inThorn * (fx_line(abs(cell) - width, 0.03) * 0.8 + (1.0 - step(width, abs(cell))) * 0.18);

    // The ring of eyes, counter-turning.
    vec2 e = rot(local, -spin * 0.9);
    light += fx_line(length(e) - 0.66, 0.004) * 0.7;
    for (int i = 0; i < 7; i++) {
        vec2 c = corner(i, 0.66);
        vec2 d = e - c;
        float eye = length(d * vec2(1.0, 1.9));
        light += fx_line(eye - 0.055, 0.006);
        float blink = step(0.06, fract(t * 0.23 + float(i) * 0.37 + seed * 0.11));
        hot += (1.0 - smoothstep(0.0, 0.022, length(d))) * blink;
    }

    // The seven-pointed star, {7/3}, and lightning crawling its lines.
    vec2 s = rot(local, spin * 0.35);
    float star = 10.0;
    for (int i = 0; i < 7; i++) {
        star = min(star, segment(s, corner(i, 0.6), corner((i + 3) % 7, 0.6)));
    }
    float crawl = fx_noise(vec2(atan(s.y, s.x) * 6.0 + t * 7.0, r * 18.0 - t * 5.0));
    light += fx_line(star, 0.006 + 0.004 * crawl);
    hot += fx_line(star, 0.0025) * (0.4 + 0.6 * step(0.72, crawl));

    // Inner rings, and between them a band of jagged script.
    light += fx_line(r - 0.36, 0.005) + fx_line(r - 0.3, 0.003);
    float band = r > 0.3 && r < 0.36 ? 1.0 : 0.0;
    float script = fx_noise(vec2(around * 90.0 + seed, floor(t * 2.0)));
    light += band * step(0.55, script) * 0.6;

    // The slit of an eye in the middle, opening as the sigil fills; its pupil a hot point.
    float open = smoothstep(0.6, 1.0, drawn) * (0.75 + 0.25 * sin(t * 1.7));
    vec2 m = local / 0.2;
    float lid = abs(m.y) - (1.0 - m.x * m.x) * 0.55 * open;
    float inEye = (1.0 - smoothstep(-0.02, 0.02, lid)) * step(abs(m.x), 1.0);
    light += fx_line(lid, 0.02) * step(abs(m.x), 1.0) * 1.2;
    hot += inEye * (1.0 - smoothstep(0.0, 0.35, length(m))) * 1.5;
    light += inEye * 0.25;

    // A faint fill, so the sigil reads as a surface of power and not only its lines.
    float fill = (1.0 - smoothstep(0.0, 0.96, r)) * 0.08 * (0.7 + 0.3 * fx_fbm(local * 3.0 + vec2(t * 0.2, -t * 0.15)));

    float intensity = ((light + fill) * laid + hot * laid) * (0.85 + 0.15 * sin(t * 5.0 + seed));
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0, 0.95, 1.0), clamp(hot * 0.8 + light * 0.15, 0.0, 1.0));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
