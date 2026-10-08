#version 330
#extension GL_ARB_separate_shader_objects : require

// Vanilla's terrain shader, with Hardwrought's dynamic lights added to the lightmap per pixel. The
// surface normal comes from the screen-space derivatives of the position, which is exact for the
// flat faces terrain is made of.

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:texture_sampling.glsl>
#include <minecraft:oit.glsl>
#include <minecraft:terrainglobals.glsl>
#include <hardwrought:dynamic_light.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in float chunkVisibility;
layout(location = 5) in vec4 lightMapColor;
layout(location = 6) in vec3 worldPos;
layout(location = 7) in float blockLight;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}

void main() {
    vec4 light = lightMapColor;
    #ifndef OIT_ALPHA_ONLY
    if (HwLightInfo.x > 0) {
        vec3 normal = normalize(cross(dFdx(worldPos), dFdy(worldPos)));
        // Turn the normal towards the camera; the camera sits at the origin.
        if (dot(normal, worldPos) > 0.0) {
            normal = -normal;
        }
        light.rgb = hw_add_light(light.rgb, hw_dynamic_light(worldPos, normal, blockLight));
    }
    #endif
    vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor * light;
    #ifndef OIT_ALPHA_ONLY
    color = mix(FogColor * vec4(1, 1, 1, color.a), color, chunkVisibility);
    #endif
    #ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
