#version 330
#extension GL_ARB_separate_shader_objects : require

// A sphere of force seen from the camera: bright at its rim, a shimmering hexagonal lattice across
// its face and ripples that run outward. The quad always faces the camera, so the sphere's normal
// can be rebuilt from the position on it. params.x is how strong the lattice and ripples show,
// params.y a time offset so neighbouring shields do not pulse in step.

#include <hardwrought:fx.glsl>

float fx_hex(vec2 p) {
    const vec2 s = vec2(1.0, 1.7320508);
    vec4 hc = floor(vec4(p, p - vec2(0.5, 1.0)) / s.xyxy) + 0.5;
    vec4 h = vec4(p - hc.xy * s, p - (hc.zw + 0.5) * s);
    vec2 cell = dot(h.xy, h.xy) < dot(h.zw, h.zw) ? h.xy : h.zw;
    cell = abs(cell);
    return 0.5 - max(dot(cell, s * 0.5), cell.x);
}

void main() {
    float d = length(local);
    if (d >= 1.0) {
        discard;
    }
    float z = sqrt(1.0 - d * d);
    float fresnel = pow(1.0 - z, 2.4);
    float t = fx_time() + params.y;

    // The lattice lies on the sphere, so it bunches up towards the rim.
    vec2 surface = local / (0.35 + z) * 4.0 + vec2(t * 0.15, t * 0.07);
    float hexEdge = fx_line(fx_hex(surface), 0.035);
    float shimmer = 0.5 + 0.5 * sin(t * 3.0 + fx_noise(surface * 0.7) * 6.0);

    float ripple = pow(max(0.0, sin(d * 14.0 - t * 5.0)), 12.0) * (1.0 - d);
    float rim = smoothstep(0.86, 0.995, d) * (1.0 - smoothstep(0.995, 1.0, d));

    float intensity = fresnel * 0.9 + rim * 0.9 + 0.05
            + params.x * (hexEdge * (0.12 + fresnel * 0.6) * shimmer + ripple * 0.35);
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), clamp(rim * 0.7 + hexEdge * fresnel * 0.4, 0.0, 1.0));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
