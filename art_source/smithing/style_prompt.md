# Smithing part texture direction

- Native 16x16 Minecraft item sprites with hard pixel edges and transparent background.
- Extract the silhouette and diagonal presentation directly from the corresponding Vanilla tool;
  remove its wooden stick or handle, then centre the remaining visible bounds in the 16x16 slot.
  Do not rotate the part or turn the pick head into a V.
- Heads keep a small dark socket; blades keep only a short metal tang. No crossguard, grip or pommel.
- Iron is cool grey and visibly forged, gold warm and polished, bronze copper-brown.
- Sharpened edges are one palette step brighter than the body and the dark outline stays readable.
- The reproducible source of truth is `tools/part_textures.py`; after changes run
  `tools/glow_textures.py` as well.
