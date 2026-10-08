#version 330
#extension GL_ARB_separate_shader_objects : require

// Not drawn into the scene but into the distortion field: how far, in screen UV, the air at each
// pixel bends what is seen through it. params.x is the strength, params.y the kind of bending:
// 0 a shockwave's ring, 1 shimmering heat, 2 a swirl, 3 a lens that pulls inward.

#include <hardwrought:fx.glsl>

void main() {
    float d = length(local);
    if (d >= 1.0) {
        discard;
    }
    // The direction in which the distance from the centre grows, measured on screen, so the
    // bending follows the primitive however it lies in the world.
    vec2 grad = vec2(dFdx(d), dFdy(d));
    float gradLength = length(grad);
    vec2 outward = gradLength > 1e-6 ? grad / gradLength : vec2(0.0);
    // The share of the screen the primitive covers: far away, the same effect bends less of the view.
    float screenRadius = gradLength > 1e-6 ? min(1.0 / gradLength / ScreenSize.y, 0.6) : 0.0;

    // At full strength a primitive filling the view shifts it by about 7% of the screen.
    float strength = params.x * vertexColor.a * screenRadius * 0.12;
    int mode = int(params.y + 0.5);
    vec2 offset;
    if (mode == 0) {
        float band = sin(clamp((d - 0.62) / 0.38, 0.0, 1.0) * 3.14159265);
        offset = outward * band * strength;
    } else if (mode == 1) {
        float t = fx_time();
        vec2 wobble = vec2(fx_noise(local * 3.0 + vec2(0.0, t * 2.2)), fx_noise(local * 3.0 + vec2(5.3, t * 2.6))) - 0.5;
        offset = wobble * (1.0 - d) * (1.0 - d) * strength * 2.0;
    } else if (mode == 2) {
        offset = vec2(-outward.y, outward.x) * sin(d * 3.14159265) * strength;
    } else {
        offset = -outward * sin(d * 3.14159265) * strength;
    }
    fragColor = vec4(offset, 0.0, 1.0);
}
