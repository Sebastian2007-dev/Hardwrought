# Hardwrought metal stock source prompts

Generated with the built-in Imagegen tool. The user's plate and rod examples, the existing iron
sprites, and the shared bevel language were supplied as visual references. The generated images are
source concepts; the production sprites are hand-authored 16x16 value maps in
`tools/metal_stock_textures.py` and take their colours from each metal's ingot.

## Plate

```text
Use case: stylized-concept
Asset type: source concept for a Minecraft-compatible 16x16 inventory item texture
Primary request: redesign the metal plate so it looks handcrafted, dimensional, and polished while
remaining instantly readable as a forged metal plate at 16x16 pixels.
Style/medium: authentic vanilla-like Minecraft pixel art with hard-edged pixels and four grayscale
material values suitable for palette swaps.
Composition: one compact hammered plate in three-quarter view, slightly irregular forged silhouette,
with a bright upper-left ridge and a dark lower-right thickness.
Constraints: transparent background, one item, no text, shadow, halo, anti-aliasing, gradients,
blur, extra objects, or watermark.
```

## Rod

```text
Use case: stylized-concept
Asset type: source concept for a Minecraft-compatible 16x16 inventory item texture
Primary request: redesign the metal rod so it looks forged, cylindrical, dimensional, and polished
while remaining instantly readable as a metal rod at 16x16 pixels.
Style/medium: authentic vanilla-like Minecraft pixel art with hard-edged pixels and four grayscale
material values suitable for palette swaps.
Composition: one straight rod from lower-left to upper-right, subtly capped ends, consistent width,
narrow bright ridge, midtone body, and a dark lower-right bevel matching the plate.
Constraints: transparent background, one item, no text, cast shadow, halo, anti-aliasing, gradients,
blur, extra objects, or watermark; must not read as a sword, nail, wire, or stick.
```
