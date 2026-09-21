# Hewn workbench texture prompt

The built-in Imagegen tool was used for the initial material study. After visual review, the final
16x16 exports deliberately preserve the real vanilla log texture and add only subtle worked details.
The side study is saved as `hewn_workbench/vanilla_log_workbench_concept_v2.png`; it established the
reduced rim, while the final pixel export improves it further with a genuinely irregular edge. The
top study is saved as `hewn_workbench/crafting_top_concept_v3.png`.

```text
Use case: style-transfer
Asset type: source artwork for a seamless Minecraft-compatible 16x16 block texture
Input images: the in-game screenshot is the problem reference; the matching vanilla bark, stripped
side and end-grain sprites are the material references.
Primary request: Create a primitive workbench hewn directly from a normal vanilla log. Its top must
read immediately as a crafting table by retaining the framed 3x3 work-grid language of the vanilla
crafting-table top, recoloured for the current wood species and joined to its end grain at the outer
edge. Keep almost the entire side as vanilla bark; let only the upper two to four pixels become
stripped wood through an uneven, chipped transition whose depth changes from column to column.
Style/medium: crisp hand-placed pixel art compatible with modern vanilla Minecraft.
Composition/framing: exact orthographic square, full-bleed texture, no margin, no perspective.
Color palette: retain the original vanilla colors and value range of each wood species.
Constraints: one continuous 16x16 side face without separate model layers; fully opaque; hard pixel
edges; no straight horizontal seam; no added tools; no text; no symbols; no external border;
no external shadow; no watermark.
Avoid: redesigning the log, fantasy wood, smooth painting, photorealism, blur, anti-aliasing,
oversized pale bands, plain concentric target patterns without a crafting grid, glow, or an unrelated palette.
```
