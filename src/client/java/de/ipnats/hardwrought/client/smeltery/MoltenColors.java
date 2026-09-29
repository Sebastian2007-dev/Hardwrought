package de.ipnats.hardwrought.client.smeltery;

import java.util.Map;

/**
 * The colour each molten material shows in the smeltery's screen. The same shades tint the molten
 * textures (tools/smeltery_assets.py).
 */
public final class MoltenColors {
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("iron", 0xD9521E), Map.entry("gold", 0xF6C83A), Map.entry("copper", 0xE8793A),
            Map.entry("bronze", 0xD9953A), Map.entry("steel", 0xC9C2B8), Map.entry("stainless_steel", 0xE6E9EE),
            Map.entry("tungsten_steel", 0x6E7483), Map.entry("titanium", 0xC7B8D8), Map.entry("tungsten", 0x8C8F99),
            Map.entry("tin", 0xDCDDE8), Map.entry("zinc", 0xB8C8C8), Map.entry("lead", 0x7C7A96),
            Map.entry("manganese", 0xB89A96), Map.entry("magnesium", 0xEDEDE0), Map.entry("aluminum", 0xD8DDE3),
            Map.entry("nickel", 0xD3CFA8), Map.entry("cobalt", 0x5A7DD8), Map.entry("chromium", 0xC8D8E4),
            Map.entry("uranium", 0x7FD04F), Map.entry("thorium", 0x9FA8A0), Map.entry("platinum", 0xD8ECF4),
            Map.entry("netherite", 0x5A4448), Map.entry("carbon", 0x2A2626));

    private MoltenColors() { }

    public static int of(String material) {
        return 0xFF000000 | COLORS.getOrDefault(material, 0xD9521E);
    }
}
