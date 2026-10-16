#version 330
#extension GL_ARB_separate_shader_objects : require

// The Underworld shaft: looking down into it, it goes on below the pit, further than any light reaches.
// Every pixel of the surface casts the view ray on down into a shaft as wide as the opening: its walls
// carry the cracked seal on downwards, glowing veins fading into the dark; mist hangs in it in layers,
// drifting and slowly turning; pale souls rise out of the deep. Nothing is drawn but what the ray meets.
//
// local: where on the opening this pixel lies, in blocks from its corner. params: the opening's size.

#include <hardwrought:fx.glsl>

const vec3 SOUL = vec3(0.20, 0.78, 0.86);
const vec3 PALE = vec3(0.82, 0.94, 1.00);
const vec3 ROCK = vec3(0.045, 0.055, 0.065);
const float FAR = 48.0;

float ridge(vec2 p) {
    return 1.0 - abs(fx_noise(p) * 2.0 - 1.0);
}

mat2 turn(float angle) {
    float c = cos(angle), s = sin(angle);
    return mat2(c, -s, s, c);
}

void main() {
    vec2 size = max(params, vec2(1.0));
    vec2 at = local;
    vec3 ray = normalize(worldPos);
    float down = max(-ray.y, 0.015);
    // How far the ray travels sideways for each block it goes down.
    vec2 step = ray.xz / down;
    float t = fx_time();
    // The shaft breathes: a slow swell in all its light.
    float pulse = 0.82 + 0.18 * sin(t * 1.1) * sin(t * 0.37 + 1.0);

    // Where the ray meets a wall of the shaft, and which.
    float wall = FAR;
    float along = 0.0;
    if (abs(step.x) > 1e-4) {
        float d = ((step.x > 0.0 ? size.x : 0.0) - at.x) / step.x;
        if (d < wall) { wall = d; along = at.y + step.y * d; }
    }
    if (abs(step.y) > 1e-4) {
        float d = ((step.y > 0.0 ? size.y : 0.0) - at.y) / step.y;
        if (d < wall) { wall = d; along = at.x + step.x * d; }
    }
    wall = clamp(wall, 0.0, FAR);

    vec3 color = vec3(0.0);
    float through = 1.0;

    // Mist, layer on layer, rising very slowly; each layer keeps its own pattern as it rises.
    float mistRise = t * 0.06;
    for (int i = 0; i < 26; i++) {
        float layer = float(i) - floor(mistRise);
        float d = (float(i) + 1.0 - fract(mistRise)) * 0.6;
        if (d > wall) break;
        vec2 p = at + step * d - size * 0.5;
        p = turn(t * 0.04 + layer * 0.31) * p;
        float n = fx_fbm(p * 0.85 + vec2(layer * 3.17, layer * 1.91));
        float wisp = smoothstep(0.5, 0.86, n);
        float near = smoothstep(0.0, 1.2, d);
        float fade = exp(-d * 0.09);
        color += through * SOUL * wisp * 0.2 * fade * near * pulse;
        through *= 1.0 - wisp * 0.1 * near;
    }

    // Souls rising out of the deep: points of pale light, a few to each layer, coming up past the eye.
    float soulRise = t * 0.35;
    for (int i = 0; i < 18; i++) {
        float layer = float(i) - floor(soulRise);
        float d = (float(i) + 1.0 - fract(soulRise)) * 1.3;
        if (d > wall) break;
        vec2 p = (at + step * d) * 1.6;
        vec2 cell = floor(p);
        float h = fx_hash(cell + vec2(layer * 7.13, layer * 3.71));
        if (h < 0.86) continue;
        vec2 center = cell + vec2(fx_hash(cell + layer), fx_hash(cell - layer));
        float r = length(p - center);
        float glow = exp(-r * r * 90.0) + 0.25 * exp(-r * r * 9.0);
        float fade = exp(-d * 0.12) * smoothstep(0.0, 1.5, d);
        float flicker = 0.7 + 0.3 * sin(t * 6.0 + h * 40.0);
        color += through * PALE * glow * fade * flicker * 0.9;
    }

    // The walls: the cracked seal going on down, its veins glowing less the deeper they are.
    if (wall < FAR) {
        // Sixteen pixels to a block, like every texture in the world it continues.
        vec2 w = (floor(vec2(along, wall) * 16.0) + 0.5) / 16.0;
        float rock = 0.7 + 0.6 * fx_noise(w * 4.0) + 0.3 * fx_noise(w * 11.0);
        float veins = pow(ridge(w * vec2(1.4, 0.8)), 14.0) + 0.6 * pow(ridge(w * vec2(2.9, 1.7) + 3.1), 18.0);
        float depth = exp(-wall * 0.16);
        vec3 surface = ROCK * rock * exp(-wall * 0.35) + SOUL * veins * 0.85 * depth * pulse;
        color += through * surface;
    }

    fx_output_translucent(color, 1.0);
}
