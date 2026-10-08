#version 330
#extension GL_ARB_separate_shader_objects : require

// One step down the bloom chain (dual Kawase): half the size, blurred on the way.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 halfTexel = 0.5 / vec2(textureSize(InSampler, 0));
    vec4 sum = texture(InSampler, texCoord) * 4.0;
    sum += texture(InSampler, texCoord - halfTexel);
    sum += texture(InSampler, texCoord + halfTexel);
    sum += texture(InSampler, texCoord + vec2(halfTexel.x, -halfTexel.y));
    sum += texture(InSampler, texCoord - vec2(halfTexel.x, -halfTexel.y));
    fragColor = sum / 8.0;
}
