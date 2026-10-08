#version 330
#extension GL_ARB_separate_shader_objects : require

// A magic circle drawn in light: rings, a band of glyphs, a hexagram and a small inner triangle,
// each turning at its own pace. params.x is the rotation in radians; params.y carries the glyph
// seed in its whole part and how much of the circle has been drawn yet (0..1) in its fraction.

#include <hardwrought:fx.glsl>

vec2 fx_rotate(vec2 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float fx_segment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

vec2 fx_vertex(int i, int n, float r) {
    float a = FX_TAU * float(i) / float(n) + FX_TAU * 0.25;
    return vec2(cos(a), sin(a)) * r;
}

// One glyph in a cell of the band, made of strokes between the points of a 3x3 lattice.
float fx_glyph(vec2 g, float seed) {
    float dist = 10.0;
    vec2 pts[8] = vec2[8](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0),
            vec2(0.0, -1.0), vec2(1.0, 0.0), vec2(0.0, 1.0), vec2(-1.0, 0.0));
    for (int i = 0; i < 8; i++) {
        float h = fx_hash(vec2(seed, float(i) * 7.13));
        if (h < 0.42) {
            int j = int(fx_hash(vec2(seed * 1.7, float(i) + 3.1)) * 8.0);
            if (j == i) j = (i + 3) % 8;
            dist = min(dist, fx_segment(g, pts[i], pts[j]));
        }
    }
    dist = min(dist, fx_segment(g, vec2(0.0, -1.0), vec2(0.0, 1.0)) + step(0.5, fx_hash(vec2(seed, 91.0))) * 10.0);
    return dist;
}

void main() {
    float d = length(local);
    if (d >= 1.0) {
        discard;
    }
    float rotation = params.x;
    float seed = floor(params.y);
    float reveal = fract(params.y) > 0.998 ? 1.0 : fract(params.y);
    float angle01 = fract(atan(local.y, local.x) / FX_TAU + 0.25);
    if (angle01 > reveal) {
        discard;
    }

    float lines = 0.0;
    float nearest = 10.0;

    // The rings.
    float r1 = abs(d - 0.955);
    float r2 = abs(d - 0.925);
    float r3 = abs(d - 0.815);
    float r4 = abs(d - 0.42);
    lines += fx_line(d - 0.955, 0.009) + fx_line(d - 0.925, 0.004) + fx_line(d - 0.815, 0.006)
            + fx_line(d - 0.42, 0.005);
    nearest = min(min(r1, r2), min(r3, r4));

    // The glyph band between the outer rings, turning with the circle.
    vec2 band = fx_rotate(local, rotation);
    float bandAngle = fract(atan(band.y, band.x) / FX_TAU);
    const float CELLS = 22.0;
    float cell = floor(bandAngle * CELLS);
    float u = fract(bandAngle * CELLS) - 0.5;
    float v = (d - 0.868) / 0.034;
    vec2 g = vec2(u * FX_TAU * 0.868 / CELLS / 0.034, v);
    if (abs(g.x) < 1.4 && abs(g.y) < 1.4) {
        float glyph = fx_glyph(g, seed * 31.0 + cell);
        lines += fx_line(glyph * 0.034, 0.0045);
        nearest = min(nearest, glyph * 0.034);
    }

    // The hexagram, turning against the circle.
    vec2 star = fx_rotate(local, -rotation * 0.6);
    float hex = 10.0;
    for (int i = 0; i < 6; i++) {
        hex = min(hex, fx_segment(star, fx_vertex(i, 6, 0.8), fx_vertex(i + 2, 6, 0.8)));
    }
    lines += fx_line(hex, 0.005);
    nearest = min(nearest, hex);
    float dots = 10.0;
    for (int i = 0; i < 6; i++) {
        dots = min(dots, abs(length(star - fx_vertex(i, 6, 0.8)) - 0.035));
    }
    lines += fx_line(dots, 0.005);

    // The ticks around the inner ring and the small triangle inside it.
    float tickAngle = fract(atan(band.y, band.x) / FX_TAU * 48.0);
    if (d > 0.43 && d < 0.47 && tickAngle < 0.12) {
        lines += 0.8;
    }
    vec2 inner = fx_rotate(local, rotation * 1.7);
    float tri = 10.0;
    for (int i = 0; i < 3; i++) {
        tri = min(tri, fx_segment(inner, fx_vertex(i, 3, 0.38), fx_vertex(i + 1, 3, 0.38)));
    }
    lines += fx_line(tri, 0.005);
    nearest = min(nearest, tri);

    float pulse = 0.85 + 0.15 * sin(fx_time() * 2.5 + d * 9.0);
    float glow = exp(-nearest * 38.0) * 0.4 + exp(-d * d * 18.0) * 0.25;
    float intensity = (min(lines, 1.5) + glow) * pulse;
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), clamp(lines * 0.45, 0.0, 0.8));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
