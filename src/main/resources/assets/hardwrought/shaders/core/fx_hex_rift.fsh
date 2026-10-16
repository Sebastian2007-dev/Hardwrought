#version 330
#extension GL_ARB_separate_shader_objects : require

// A rift: a tear standing upright in the air, the Witch King stepping in or out of it. A lens-shaped
// opening with white-hot, crackling lips, and inside it a void that turns, with points of cold light
// far off in it. params.x: how open it is (0..1). params.y: seed.
// local.x runs across the tear, local.y along it.

#include <hardwrought:fx.glsl>

void main() {
    float open = clamp(params.x, 0.0, 1.0);
    float seed = params.y;
    float t = fx_time();
    float y = local.y;
    // The tear's half width at this height: a lens, jagged at its lips.
    float lips = (fx_noise(vec2(y * 12.0 + seed, t * 6.0)) - 0.5) * 0.12;
    float halfWidth = max(0.0, (1.0 - y * y)) * 0.55 * open + lips * open;
    float d = abs(local.x) - halfWidth;
    if (d > 0.25 || open <= 0.001) discard;

    float edge = fx_line(d, 0.02 + 0.015 * open);
    float glow = (1.0 - smoothstep(0.0, 0.25, max(d, 0.0))) * 0.5;
    float inside = 1.0 - step(0.0, d);

    // The void within: a slow spiral of faint light, and far points of it.
    vec2 p = vec2(local.x / max(halfWidth, 0.05), y);
    float a = atan(p.y, p.x) + t * 0.8;
    float spiral = fx_noise(vec2(a * 3.0 + length(p) * 6.0 - t * 2.0, seed)) * (1.0 - length(p) * 0.5);
    vec2 starCell = floor(local * vec2(40.0, 30.0) + vec2(0.0, t * 2.0));
    float star = step(0.97, fx_hash(starCell + seed)) * (0.5 + 0.5 * sin(t * 9.0 + fx_hash(starCell) * 30.0));
    float abyss = inside * (spiral * 0.35 + star * 0.9);

    // Sparks shed from the lips.
    float spark = step(0.93, fx_noise(vec2(y * 40.0, t * 20.0 + seed))) * (1.0 - smoothstep(0.0, 0.12, abs(d))) ;

    float intensity = (edge * 1.5 + glow * open + abyss + spark) * open;
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), clamp(edge * 0.8 + star * inside, 0.0, 1.0));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
