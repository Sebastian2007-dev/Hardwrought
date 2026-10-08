#version 330
#extension GL_ARB_separate_shader_objects : require

// A point of light: a wide soft halo around a small hot core. Stretched along its motion, the same
// quad becomes a streak. params.x is how white-hot the core burns, params.y how strongly the
// four-pointed glint shows.

#include <hardwrought:fx.glsl>

void main() {
    float d = length(local);
    if (d >= 1.0) {
        discard;
    }
    float fall = 1.0 - d;
    float halo = fall * fall;
    float core = exp(-d * d * 26.0);
    float glint = (exp(-abs(local.x) * 22.0) * (1.0 - abs(local.y))
            + exp(-abs(local.y) * 22.0) * (1.0 - abs(local.x))) * fall;

    float hot = params.x;
    float intensity = halo * 0.55 + core * (0.45 + hot) + glint * params.y;
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), clamp(core * hot + glint * params.y * 0.5, 0.0, 1.0));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
