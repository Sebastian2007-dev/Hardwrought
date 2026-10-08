#version 330
#extension GL_ARB_separate_shader_objects : require

// One step up the bloom chain (dual Kawase): twice the size, blurred again, added onto the level
// below so every scale of glow ends up in the largest one.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 halfTexel = 0.5 / vec2(textureSize(InSampler, 0));
    vec4 sum = texture(InSampler, texCoord + vec2(-halfTexel.x * 2.0, 0.0));
    sum += texture(InSampler, texCoord + vec2(-halfTexel.x, halfTexel.y)) * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, halfTexel.y * 2.0));
    sum += texture(InSampler, texCoord + vec2(halfTexel.x, halfTexel.y)) * 2.0;
    sum += texture(InSampler, texCoord + vec2(halfTexel.x * 2.0, 0.0));
    sum += texture(InSampler, texCoord + vec2(halfTexel.x, -halfTexel.y)) * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, -halfTexel.y * 2.0));
    sum += texture(InSampler, texCoord + vec2(-halfTexel.x, -halfTexel.y)) * 2.0;
    fragColor = sum / 12.0;
}
