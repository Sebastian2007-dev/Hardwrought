#ifndef MINECRAFT_GLOBALS_GLSL
#define MINECRAFT_GLOBALS_GLSL

// Vanilla's globals, followed by Hardwrought's dynamic lights (see hardwrought:dynamic_light.glsl).
// The buffer is enlarged to match by GlobalSettingsUniformMixin; the vanilla part is unchanged.

#define HW_MAX_LIGHTS 64

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    float GlintAlpha;
    vec3 CameraOffset;
    float GameTime;
    vec2 ScreenSize;
    int MenuBlurRadius;
    int UseRgss;
    // x: number of lights in use.
    ivec4 HwLightInfo;
    // xyz: position relative to the camera, w: radius in blocks, negative for a placed light.
    vec4 HwLightPos[HW_MAX_LIGHTS];
    // rgb: colour, a: intensity.
    vec4 HwLightColor[HW_MAX_LIGHTS];
};

#endif
