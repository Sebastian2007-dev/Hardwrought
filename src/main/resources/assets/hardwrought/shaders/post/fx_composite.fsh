#version 330
#extension GL_ARB_separate_shader_objects : require

// The last step of the FX post pass: the scene seen through the distortion field, split into its
// colours where the air bends hardest, with the bloom of the effects laid over it.
//
// And while the End's dragon rages, the void itself tears: cracks of light that open, flare and close
// again all over the sky and the far fog, held fast to directions in the world so they stay where they
// are as the view turns (see VoidTear in FxPost).

#include <minecraft:globals.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D BloomSampler;
uniform sampler2D DistortSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform FxComposite {
    // x: bloom strength, y: chromatic aberration across the whole view, z: saturation boost, w: unused.
    vec4 Params;
    // x: how torn the void is (0 none, 1 fully), y: seconds, z/w: from and to how far away terrain fades
    // into the tear, in blocks: the last of the fog, so nothing near and solid is torn.
    vec4 Tear;
    // From the screen to a direction in the world, the camera's rotation only.
    mat4 InvViewProj;
    // From the screen to the camera's own space, for how far away a pixel is.
    mat4 InvProj;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

vec3 hash3(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453);
}

float hash1(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

// How far x lies from the nearest edge between two cells, and which cell it is in.
float cellEdge(vec3 x, out vec3 cell) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    vec3 nearestB = vec3(0.0), nearestR = vec3(0.0);
    float nearest = 8.0;
    for (int k = -1; k <= 1; k++) for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
        vec3 b = vec3(i, j, k);
        vec3 r = b + hash3(p + b) - f;
        float d = dot(r, r);
        if (d < nearest) {
            nearest = d;
            nearestR = r;
            nearestB = b;
        }
    }
    float edge = 8.0;
    for (int k = -1; k <= 1; k++) for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
        vec3 b = nearestB + vec3(i, j, k);
        vec3 r = b + hash3(p + b) - f;
        if (dot(nearestR - r, nearestR - r) > 1e-5) edge = min(edge, dot(0.5 * (nearestR + r), normalize(r - nearestR)));
    }
    cell = p + nearestB;
    return edge;
}

// One layer of cracks at a scale: each cell's edges open, flare and close on a cycle of its own, and
// each time round a different few of them, so that there are always new ones.
vec3 cracks(vec3 dir, float scale, float rate, float seconds, float width) {
    vec3 x = dir * scale;
    // Jagged rather than straight: the space bent a little, and bending on.
    x += 0.22 * vec3(sin(x.y * 3.1 + seconds * 0.7), sin(x.z * 2.7 - seconds * 0.5), sin(x.x * 3.3 + seconds * 0.6));
    x += 0.08 * vec3(sin(x.y * 11.0), sin(x.z * 13.0), sin(x.x * 12.0));
    vec3 cell;
    float edge = cellEdge(x, cell);
    float seed = hash1(cell);
    float cycle = seconds * rate * (0.7 + 0.6 * seed) + seed * 9.0;
    float phase = fract(cycle);
    float open = hash1(cell + floor(cycle) * 17.31) > 0.55 ? 1.0 : 0.0;
    float life = open * smoothstep(0.0, 0.05, phase) * (1.0 - smoothstep(0.35, 0.65, phase));
    float flare = open * (1.0 - smoothstep(0.0, 0.12, phase));
    float line = 1.0 - smoothstep(0.0, width, edge);
    float halo = exp(-edge * 14.0);
    // Through the crack, what lies beyond the void: white-hot, flickering; round it, magenta light.
    float flicker = 0.75 + 0.25 * sin(seconds * 23.0 + seed * 40.0 + edge * 60.0);
    vec3 core = mix(vec3(1.0, 0.55, 0.95), vec3(1.0), flare) * line * flicker * 2.2;
    vec3 glow = vec3(0.75, 0.18, 1.0) * halo * 0.55 + vec3(1.0, 0.25, 0.8) * halo * flare;
    return (core + glow) * life;
}

vec3 tornVoid(vec2 uv, float depth, float seconds) {
    vec4 world = InvViewProj * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(world.xyz / world.w);
    // Only where the eye looks into the void: the sky, and what the fog has all but swallowed. The depth
    // runs the other way round here: nothing drawn is 0, the nearest is 1.
    float far = 1.0;
    if (depth > 0.00001) {
        vec4 view = InvProj * vec4(uv * 2.0 - 1.0, depth, 1.0);
        float distance = length(view.xyz / view.w);
        far = smoothstep(Tear.z, Tear.w, distance) * 0.85;
    }
    if (far <= 0.001) return vec3(0.0);
    vec3 light = cracks(dir, 3.0, 0.11, seconds, 0.035) + cracks(dir.zxy, 7.0, 0.19, seconds * 1.3, 0.05) * 0.6;
    // The void itself stirred: a slow violet breathing, and now and then a flash all over.
    float breath = 0.5 + 0.5 * sin(seconds * 1.7 + dir.y * 4.0 + dir.x * 3.0);
    float flash = pow(max(0.0, sin(seconds * 0.9) * sin(seconds * 2.3 + 1.0)), 10.0);
    vec3 stir = vec3(0.32, 0.06, 0.42) * (0.18 + 0.12 * breath) + vec3(0.9, 0.3, 1.0) * flash * 0.25;
    return (light + stir) * far;
}

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

    if (Tear.x > 0.001) {
        scene += tornVoid(uv, texture(DepthSampler, uv).r, Tear.y) * Tear.x;
    }

    vec3 bloom = texture(BloomSampler, texCoord).rgb * Params.x;
    // Laid on softly, so bright magic over a bright sky does not clip to flat white.
    vec3 color = scene + bloom * (1.0 - scene * 0.6);

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, 1.0 + Params.z);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
