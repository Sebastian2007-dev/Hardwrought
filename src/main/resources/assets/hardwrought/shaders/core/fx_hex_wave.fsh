#version 330
#extension GL_ARB_separate_shader_objects : require

// The Witch King's shockwave, flat on the ground: a front of dark power torn into tongues, a white-hot
// leading edge, and behind it a wake of smoke-like light breaking up. Runs from the middle to the rim
// over its life. params.x: how far along its life it is (0..1). params.y: seed.

#include <hardwrought:fx.glsl>

void main() {
    float r = length(local);
    if (r >= 1.0) discard;
    float life = clamp(params.x, 0.0, 1.0);
    float seed = params.y;
    float t = fx_time();
    float angle = atan(local.y, local.x);

    // The front, ragged: tongues pushed ahead of it.
    float ragged = fx_noise(vec2(angle * 9.0 + seed, t * 3.0)) * 0.06 + fx_noise(vec2(angle * 31.0 - seed, t * 8.0)) * 0.025;
    float front = 0.94 - ragged;
    float edge = fx_line(r - front, 0.012);
    float band = smoothstep(front - 0.32, front, r) * (1.0 - smoothstep(front, front + 0.01, r));

    // The wake: broken, turbulent light fading behind the front.
    float wake = fx_fbm(vec2(angle * 4.0, r * 7.0 - t * 4.0) + seed) * smoothstep(front - 0.55, front - 0.05, r)
            * (1.0 - smoothstep(front - 0.05, front, r));
    // Streaks thrown outwards.
    float streak = step(0.82, fx_noise(vec2(angle * 60.0 + seed, 0.0))) * smoothstep(front - 0.25, front, r) * (1.0 - step(front, r));

    float fade = 1.0 - smoothstep(0.55, 1.0, life);
    float intensity = (edge * 1.6 + band * 0.55 + wake * 0.6 + streak * 0.5) * fade;
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), clamp(edge * 0.9, 0.0, 1.0));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
