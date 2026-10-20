# Soul Compass

Generated with the built-in imagegen tool. `source.png` is the original transparent source;
`base.png` is the sampled 16x16 housing. `preview.png` enlarges frame 00 without interpolation.

Prompt:

> Use case: stylized-concept. Generate a Minecraft inventory item texture source for a Soul Compass. Single centered antique dark iron compass housing viewed directly face-on, octagonal round silhouette, charcoal and muted violet metal rim with a few restrained pale turquoise soul-colored inlays, very dark empty recessed dial. NO NEEDLE and no center pin: these will be animated separately by the game. Pixel art drawn on an exact 16 by 16 logical pixel grid, enlarged with nearest neighbor square pixels to fill the output canvas. Object occupies 14 of the 16 logical pixels leaving one transparent pixel margin all around. Transparent background, no text, no letters, no numbers, no cast shadow, no surrounding glow, no perspective, no smooth shading. Restrained Minecraft survival item style, bold readable silhouette.

Re-export from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/soul_compass_assets.ps1
```

The exporter samples pixel centers and adds recolored vanilla needle pixels for all 32
directions. The existing compass range thresholds and lodestone target remain unchanged.
Pass `-MinecraftJar` when the local Minecraft cache path changes.
