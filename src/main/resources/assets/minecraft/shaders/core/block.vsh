#version 330
#extension GL_ARB_separate_shader_objects : require

// Vanilla's block shader (block entities, moving blocks), with Hardwrought's dynamic lights added per vertex.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <hardwrought:dynamic_light.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;

#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;

void main() {
    vec3 pos = Position + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    #ifndef OIT_ALPHA_ONLY
    vec4 light = hw_sample_lightmap(Sampler2, UV2, pos);
    if (HwLightInfo.x > 0) {
        light.rgb = hw_add_light(light.rgb, hw_dynamic_light(pos, vec3(0.0), hw_block_light(UV2)));
    }
    vertexColor = Color * light;
    #else
    vertexColor = Color;
    #endif
    texCoord0 = UV0;
}
