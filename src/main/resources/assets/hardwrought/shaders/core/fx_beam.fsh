#version 330
#extension GL_ARB_separate_shader_objects : require

// A ribbon of energy facing the camera. local.x runs along the beam in blocks, local.y across it
// from -1 to 1. params.x is how turbulent the energy is, params.y how fast it streams.

#include <hardwrought:fx.glsl>

void main() {
    float across = abs(local.y);
    float core = exp(-across * across * 28.0);
    float glow = (1.0 - across) * (1.0 - across);

    float t = fx_time() * params.y;
    float n = fx_fbm(vec2(local.x * 1.7 - t, local.y * 1.3 + t * 0.21));
    float strands = fx_line(local.y - (n - 0.5) * 1.1, 0.03) * params.x;
    float pulse = 0.75 + 0.25 * sin(local.x * 3.0 - t * 2.0);
    // A beam from the hand would otherwise fill the whole view with white right in front of the eyes.
    float start = smoothstep(0.0, 2.0, local.x);

    float intensity = (core * 0.9 + glow * (0.25 + 0.35 * n * params.x) * pulse + strands * 0.25) * start;
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), clamp(core * 0.85, 0.0, 1.0));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
