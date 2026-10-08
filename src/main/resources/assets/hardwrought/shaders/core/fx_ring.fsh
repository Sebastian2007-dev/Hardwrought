#version 330
#extension GL_ARB_separate_shader_objects : require

// A ring of light, flat on whatever plane its quad lies in: shockwaves and halos. params.x is the
// width of the band as a share of the radius, params.y a seed that makes each ring flicker in its
// own pattern.

#include <hardwrought:fx.glsl>

void main() {
    float d = length(local);
    if (d >= 1.0) {
        discard;
    }
    float width = max(params.x, 0.01);
    float center = 1.0 - width;
    float band = clamp(1.0 - abs(d - center) / width, 0.0, 1.0);
    float edge = pow(band, 6.0);

    float angle = atan(local.y, local.x);
    float flicker = fx_noise(vec2(angle * 3.0 + params.y * 17.0, fx_time() * 3.0 + params.y * 5.0));
    float inner = d < center ? 0.12 * smoothstep(0.0, center, d) : 0.0;

    float intensity = (band * band * 0.6 + edge * 0.9) * (0.65 + 0.35 * flicker) + inner;
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), edge * 0.6);
    fx_output_additive(rgb, vertexColor.a * intensity);
}
