#version 330
#extension GL_ARB_separate_shader_objects : require

// The last step of the FX post pass: the scene seen through the distortion field, split into its
// colours where the air bends hardest, with the bloom of the effects laid over it.

#include <minecraft:globals.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;
uniform sampler2D DistortSampler;

layout(std140) uniform FxComposite {
    // x: bloom strength, y: chromatic aberration across the whole view, z: saturation boost, w: unused.
    vec4 Params;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 offset = texture(DistortSampler, texCoord).xy;
    vec2 uv = clamp(texCoord + offset, vec2(0.001), vec2(0.999));

    vec2 fromCenter = texCoord - 0.5;
    float split = min(length(offset) * 0.35, 0.006) + Params.y * 0.01 * dot(fromCenter, fromCenter) * 4.0;
    vec2 shift = (length(offset) > 1e-5 ? normalize(offset) : normalize(fromCenter + 1e-5)) * split;
    vec3 scene;
    scene.r = texture(SceneSampler, clamp(uv + shift, vec2(0.001), vec2(0.999))).r;
    scene.g = texture(SceneSampler, uv).g;
    scene.b = texture(SceneSampler, clamp(uv - shift, vec2(0.001), vec2(0.999))).b;

    vec3 bloom = texture(BloomSampler, texCoord).rgb * Params.x;
    // Laid on softly, so bright magic over a bright sky does not clip to flat white.
    vec3 color = scene + bloom * (1.0 - scene * 0.6);

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, 1.0 + Params.z);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
