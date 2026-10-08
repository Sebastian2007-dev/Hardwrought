#ifndef HARDWROUGHT_FX_GLSL
#define HARDWROUGHT_FX_GLSL

// Shared by every fragment shader of the FX module: the inputs from core/fx.vsh, noise, and the
// output path that works both with and without improved transparency (OIT).

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:oit.glsl>
#include <hardwrought:dynamic_light.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
// The position inside the primitive: -1..1 across a sprite, or along/across a beam.
layout(location = 3) in vec2 local;
// Two numbers per primitive whose meaning each shader defines for itself.
layout(location = 4) in vec2 params;
// Relative to the camera, for the dynamic lights.
layout(location = 5) in vec3 worldPos;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

const float FX_TAU = 6.28318530718;

// Seconds, wrapping with the day; GameTime is the fraction of the 24000-tick day.
float fx_time() {
    return GameTime * 1200.0;
}

float fx_hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float fx_noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = fx_hash(i);
    float b = fx_hash(i + vec2(1.0, 0.0));
    float c = fx_hash(i + vec2(0.0, 1.0));
    float d = fx_hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fx_fbm(vec2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amplitude * fx_noise(p);
        p = p * 2.03 + vec2(17.1, 9.7);
        amplitude *= 0.5;
    }
    return sum;
}

// A line of the given half width around distance 0, antialiased over one screen pixel.
float fx_line(float distance, float halfWidth) {
    float aa = max(fwidth(distance), 1e-4);
    return 1.0 - smoothstep(halfWidth, halfWidth + aa * 1.5, abs(distance));
}

void fx_emit(vec4 color) {
    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif
    fragColor = color;
    #endif
}

// Light that adds to what is behind it. Fog fades it out instead of tinting it.
void fx_output_additive(vec3 rgb, float alpha) {
    vec4 color = vec4(rgb, clamp(alpha, 0.0, 1.0)) * ColorModulator;
    color *= 1.0 - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    if (color.a < 0.002) {
        discard;
    }
    fx_emit(color);
}

// Matter that covers what is behind it, such as smoke. Fog tints it like any other surface.
void fx_output_translucent(vec3 rgb, float alpha) {
    vec4 color = vec4(rgb, clamp(alpha, 0.0, 1.0)) * ColorModulator;
    if (color.a < 0.004) {
        discard;
    }
    #ifdef OIT_ACCUMULATE
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif
}

#endif
