#version 330
#extension GL_ARB_separate_shader_objects : require

// The Witch King's ward, drawn on a quad facing the camera as the sphere it stands for: a shell of
// hexagonal cells, bright at their seams, brightest at the rim where the shell is seen edge-on, a slow
// sweep of light running over it. Cracks run across it as its crystals fall. params.x: how cracked it
// is (0..1). params.y: seed.

#include <hardwrought:fx.glsl>

// Distance to the nearest seam of a hexagonal grid, and the cell's id.
vec3 hexGrid(vec2 p) {
    vec2 s = vec2(1.0, 1.7320508);
    vec2 h = s * 0.5;
    vec2 a = mod(p, s) - h;
    vec2 b = mod(p - h, s) - h;
    vec2 g = dot(a, a) < dot(b, b) ? a : b;
    vec2 id = p - g;
    vec2 q = abs(g);
    float seam = 0.5 - max(dot(q, normalize(vec2(1.0, 1.7320508))), q.x);
    return vec3(seam, id);
}

void main() {
    float r2 = dot(local, local);
    if (r2 >= 1.0) discard;
    float cracked = clamp(params.x, 0.0, 1.0);
    float seed = params.y;
    float t = fx_time();
    // The point on the sphere this pixel shows.
    vec3 n = vec3(local, sqrt(1.0 - r2));
    float rim = pow(1.0 - n.z, 2.5);
    vec2 uv = vec2(atan(n.x, n.z) * 2.6, n.y * 4.2) + vec2(t * 0.05, 0.0);
    vec3 cell = hexGrid(uv * 1.6);
    float seam = fx_line(cell.x, 0.035);
    float pulse = fx_hash(cell.yz + seed) ;
    float sweep = 1.0 - smoothstep(0.0, 0.35, abs(fract(n.y * 0.5 + t * 0.18) - 0.5));
    float cellGlow = 0.05 + 0.12 * step(0.86, fract(pulse + t * 0.1));

    // Cracks: jagged lines through the shell, more of them the more it is broken.
    float crack = 0.0;
    for (int i = 0; i < 4; i++) {
        if (float(i) >= cracked * 4.0) break;
        float k = float(i) + seed;
        vec2 dir = normalize(vec2(fx_hash(vec2(k, 1.3)) - 0.5, fx_hash(vec2(k, 7.1)) - 0.5));
        vec2 p = local - (vec2(fx_hash(vec2(k, 3.7)), fx_hash(vec2(k, 9.2))) - 0.5) * 0.8;
        float along = dot(p, dir);
        float across = dot(p, vec2(-dir.y, dir.x)) + (fx_noise(vec2(along * 14.0, k)) - 0.5) * 0.08;
        crack += fx_line(across, 0.008) * (1.0 - smoothstep(0.2, 0.75, abs(along)));
    }

    float intensity = seam * (0.35 + 0.65 * sweep) * (0.5 + rim) + rim * 0.9 + cellGlow * (1.0 - rim) + crack * 1.4;
    intensity *= 0.9 + 0.1 * sin(t * 4.0 + seed);
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0, 0.92, 1.0), clamp(crack + rim * 0.3, 0.0, 1.0));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
