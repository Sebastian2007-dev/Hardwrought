#version 330
#extension GL_ARB_separate_shader_objects : require

// A spectral sword, made of light: a long tapering blade with a burning edge and a fuller of runes that
// flicker along it, a swept crossguard, a grip, a pommel like a small eye. Drawn on a quad along the
// sword's length. local.x runs from the pommel (-1) to the point (+1), local.y across it.
// params.x: intensity (0..1, for summoning and fading). params.y: seed.

#include <hardwrought:fx.glsl>

void main() {
    float x = local.x, y = local.y;
    float strength = clamp(params.x, 0.0, 1.0);
    float seed = params.y;
    float t = fx_time();

    // The blade: from the guard (-0.45) to the point (1.0), tapering, the last fifth to a point.
    float along = clamp((x + 0.45) / 1.45, 0.0, 1.0);
    float bladeWidth = x > -0.45 ? mix(0.42, 0.3, along) * (1.0 - smoothstep(0.78, 1.0, along)) : 0.0;
    float blade = 1.0 - smoothstep(bladeWidth - 0.06, bladeWidth, abs(y));
    float bladeEdge = fx_line(abs(y) - bladeWidth, 0.05) * step(-0.45, x);
    // The fuller: a groove of runes down the middle.
    float rune = step(0.45, fx_noise(vec2(floor(x * 18.0) + seed, floor(t * 6.0 + seed)))) * step(abs(y), 0.07) * step(-0.4, x) * step(x, 0.7);

    // The crossguard, swept forward at its tips.
    float gy = abs(y);
    float guard = (1.0 - smoothstep(0.035, 0.06, abs(x + 0.5 - gy * gy * 0.08))) * step(gy, 0.95);
    // The grip, and the pommel.
    float grip = step(-0.88, x) * step(x, -0.5) * (1.0 - smoothstep(0.08, 0.12, gy));
    float pommel = 1.0 - smoothstep(0.1, 0.16, length(vec2((x + 0.94) * 1.4, y)));

    float shape = max(max(blade * 0.45, guard), max(grip * 0.6, pommel));
    float glow = exp(-abs(y) * 4.0) * step(-0.95, x) * 0.15;
    float flicker = 0.85 + 0.15 * sin(t * 13.0 + seed * 3.0);
    float intensity = (shape + bladeEdge * 1.2 + rune * 1.1 + pommel * 0.8 + glow) * strength * flicker;
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0, 0.97, 1.0), clamp(bladeEdge * 0.7 + rune + pommel * 0.5, 0.0, 1.0));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
