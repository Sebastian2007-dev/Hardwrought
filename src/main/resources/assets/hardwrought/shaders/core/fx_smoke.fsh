#version 330
#extension GL_ARB_separate_shader_objects : require

// A soft puff of smoke or mist that covers what is behind it. params.x is a seed for the puff's
// shape, params.y how far it has dissolved (0 whole, 1 gone), eaten away from its thin parts first.

#include <hardwrought:fx.glsl>

void main() {
    float d = length(local);
    if (d >= 1.0) {
        discard;
    }
    vec2 p = local * 1.6 + vec2(params.x * 13.7, params.x * 7.3);
    float n = fx_fbm(p + vec2(fx_time() * 0.05, 0.0));
    float density = smoothstep(1.0, 0.15, d + (n - 0.5) * 0.7);
    density *= smoothstep(params.y, params.y + 0.25, n + 0.15);
    float shade = 0.75 + 0.35 * n;
    // Lit by the spells around it, so the smoke of a fireball glows with its fire.
    vec3 lit = vertexColor.rgb + hw_dynamic_light(worldPos, vec3(0.0)) * 0.3;
    fx_output_translucent(lit * shade, vertexColor.a * density);
}
