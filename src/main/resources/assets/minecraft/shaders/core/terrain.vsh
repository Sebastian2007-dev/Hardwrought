#version 330
#extension GL_ARB_separate_shader_objects : require

// Vanilla's terrain shader, except that the lightmap and the position are handed on separately, so
// terrain.fsh can add Hardwrought's dynamic lights per pixel, held against vanilla's block light.
// Vanilla's block light from the placed shader lights is mostly taken out here; see
// hw_vanilla_block_scale.

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:terrainglobals.glsl>
#include <hardwrought:dynamic_light.glsl>
#ifndef MULTIDRAW_TERRAIN
    #include <minecraft:chunksection.glsl>
#endif

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;
#ifdef MULTIDRAW_TERRAIN
layout(location = 4) in ivec3 ChunkPosition;
layout(location = 5) in float ChunkVisibility;
#endif

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out float chunkVisibility;
layout(location = 5) out vec4 lightMapColor;
layout(location = 6) out vec3 worldPos;
layout(location = 7) out float blockLight;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color;
    #ifndef OIT_ALPHA_ONLY
    lightMapColor = hw_sample_lightmap(Sampler2, UV2, pos);
    #else
    lightMapColor = vec4(1.0);
    #endif
    worldPos = pos;
    blockLight = float(UV2.x) / 16.0;
    texCoord0 = UV0;

    const float chunkFullyVisibleRange = 16.0;
    float dist = length(pos);
    chunkVisibility = mix(1.0, ChunkVisibility, clamp((dist - chunkFullyVisibleRange) / chunkFullyVisibleRange, 0.0, 1.0));
}
