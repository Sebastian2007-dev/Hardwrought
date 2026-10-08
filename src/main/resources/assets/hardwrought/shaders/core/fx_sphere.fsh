#version 330
#extension GL_ARB_separate_shader_objects : require

// A shield as a real sphere, built of quads all round (FxRenderer.sphere), so it is seen from inside
// as well as from outside. local is the longitude and latitude of the point, from which the normal is
// rebuilt; params.x is a flash that brightens the whole sphere when it is struck, params.y a time
// offset so neighbouring shields do not pulse in step.

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
    float lon = local.x, lat = local.y;
    vec3 normal = vec3(cos(lat) * cos(lon), sin(lat), cos(lat) * sin(lon));
    vec3 view = normalize(-worldPos);
    // Seen edge-on the sphere is brightest, from inside as from outside.
    float facing = abs(dot(normal, view));
    float fresnel = pow(1.0 - facing, 2.0);
    float t = fx_time() + params.y;

    vec2 surface = vec2(lon * 5.0, lat * 5.5) + vec2(t * 0.05, t * 0.03);
    float hexEdge = fx_line(fx_hex(surface), 0.045);
    float shimmer = 0.55 + 0.45 * sin(t * 2.4 + fx_noise(surface * 0.6) * 6.0);
    float band = pow(max(0.0, sin(lat * 3.0 - t * 2.0)), 16.0);

    float intensity = 0.10 + fresnel * 0.85 + hexEdge * (0.18 + fresnel * 0.5) * shimmer + band * 0.12
            + params.x * (0.45 + hexEdge * 0.6);
    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), clamp(hexEdge * 0.35 + params.x * 0.5 + fresnel * 0.2, 0.0, 1.0));
    fx_output_additive(rgb, vertexColor.a * intensity);
}
