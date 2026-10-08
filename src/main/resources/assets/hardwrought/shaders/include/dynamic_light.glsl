#ifndef HARDWROUGHT_DYNAMIC_LIGHT_GLSL
#define HARDWROUGHT_DYNAMIC_LIGHT_GLSL

// Coloured point lights from spells, carried torches and placed light sources, added on top of the
// lightmap. Positions are relative to the camera, like every vertex position in the world shaders.

#include <minecraft:globals.glsl>

// The light reaching a surface at pos, facing along normal. A zero normal lights from every side.
//
// A placed light (sent with a negative radius) is held against blockLight, vanilla's block light
// level at the surface (0 to 15, negative when unknown): vanilla's light flows around walls rather
// than through them, so where it is clearly weaker than the placed light would make it, a wall
// stands in between, and the placed light does not reach.
vec3 hw_dynamic_light(vec3 pos, vec3 normal, float blockLight) {
    vec3 sum = vec3(0.0);
    bool facing = dot(normal, normal) > 0.01;
    int count = min(HwLightInfo.x, HW_MAX_LIGHTS);
    for (int i = 0; i < count; i++) {
        vec4 light = HwLightPos[i];
        float radius = abs(light.w);
        vec3 toLight = light.xyz - pos;
        float dist = length(toLight);
        if (dist >= radius) {
            continue;
        }
        // Falls off smoothly to exactly nothing at the radius, and stays finite at the source.
        float t = dist / radius;
        float falloff = (1.0 - t * t);
        falloff *= falloff / (1.0 + dist * dist * 0.08);
        // Wrapped diffuse: surfaces turned half away still catch some of the light.
        float diffuse = facing ? clamp(dot(normal, toLight / max(dist, 1e-3)) * 0.65 + 0.35, 0.0, 1.0) : 0.8;
        vec3 lit = HwLightColor[i].rgb * HwLightColor[i].a * falloff * diffuse;
        if (light.w < 0.0 && blockLight >= 0.0) {
            // Vanilla's light loses a level per block walked along the axes, at most sqrt(3) per
            // block of straight distance; a little slack covers smooth lighting's averaging.
            float expected = radius - dist * 1.73 - 1.5;
            lit *= smoothstep(expected - 2.5, expected, blockLight);
        }
        sum += lit;
    }
    return sum;
}

vec3 hw_dynamic_light(vec3 pos, vec3 normal) {
    return hw_dynamic_light(pos, normal, -1.0);
}

// How much of vanilla's block light at pos to keep. Where it comes from the placed lights the shaders
// draw — no brighter than they could make it — it is mostly taken out, since they light the place
// themselves, in their own colour, and the two would otherwise add up to glare. What is left of it
// keeps the corners the shader light does not reach from going black. Light from anything else, lava
// or fire, stays.
#define HW_BOUNCE 0.5
// The weakest a placed light is sent while not fading: FxLighting.PLACED_INTENSITY at the low of its flicker.
#define HW_PLACED_INTENSITY 0.96
float hw_vanilla_block_scale(float blockLight, vec3 pos) {
    float taken = 0.0;
    int count = min(HwLightInfo.x, HW_MAX_LIGHTS);
    for (int i = 0; i < count; i++) {
        vec4 light = HwLightPos[i];
        if (light.w >= 0.0) {
            continue;
        }
        // Vanilla's light from it is at most its level less the straight distance.
        float most = -light.w - length(light.xyz - pos);
        float explains = 1.0 - smoothstep(most + 0.5, most + 2.0, blockLight);
        // A light fading out at the edge of the search gives vanilla's light back as it goes.
        float strength = clamp(HwLightColor[i].a / HW_PLACED_INTENSITY, 0.0, 1.0);
        taken = max(taken, explains * strength);
    }
    return mix(1.0, HW_BOUNCE, taken);
}

// Vanilla's sample_lightmap, with the block light the placed shader lights account for taken out.
vec4 hw_sample_lightmap(sampler2D lightMap, ivec2 uv, vec3 pos) {
    vec2 coord = vec2(float(uv.x) * hw_vanilla_block_scale(float(uv.x) / 16.0, pos), float(uv.y));
    return texture(lightMap, clamp(coord / 256.0 + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

// The lightmap's light with the dynamic light added. Many lights together would go far past full
// brightness and clip to a glaring flat colour; above a knee the sum is eased towards full
// brightness instead, all channels alike, so the light keeps its hue. The knee sits no lower than
// the lightmap's own light, so daylight is left as it is.
vec3 hw_add_light(vec3 base, vec3 added) {
    vec3 light = base + added;
    float peak = max(light.r, max(light.g, light.b));
    float knee = clamp(max(base.r, max(base.g, base.b)), 0.7, 0.98);
    if (peak <= knee) {
        return light;
    }
    float eased = knee + (1.0 - knee) * (1.0 - exp(-(peak - knee) / (1.0 - knee)));
    return light * (eased / peak);
}

// Vanilla's block light level, 0 to 15, from a vertex's packed lightmap coordinates.
float hw_block_light(ivec2 uv2) {
    return float(uv2.x) / 16.0;
}

// For the vertex shaders of entities and items: only in the world, never in the GUI, whose
// orthographic projection gives its vertices meaningless positions.
bool hw_in_world(mat4 projection) {
    return projection[3][3] == 0.0;
}

#endif
