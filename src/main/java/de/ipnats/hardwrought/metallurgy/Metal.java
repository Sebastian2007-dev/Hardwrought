package de.ipnats.hardwrought.metallurgy;

import java.util.Locale;

/**
 * The metals of specification sections 55 and 56, and where each of them sits in the ground.
 *
 * <p>One table, and everything else is derived from it: the ore blocks and their deepslate
 * counterparts, the raw material and the ingot, the smelting recipes, the block tags that decide
 * which pickaxe bites, the ore generation, and which rock a prospector finds them in. Adding a metal
 * is adding a line here.
 *
 * <p>The depth bands and rarities follow real geology where Minecraft has room for it: bauxite
 * weathers near the surface, lead and zinc travel together in sedimentary rock, the platinum group
 * and the radioactive metals sit deep, and tungsten belongs to granite.
 *
 * @param id          the name everything is registered under
 * @param minY        lowest height the ore generates at
 * @param maxY        highest height it generates at
 * @param veinsPerChunk how often a vein is tried per chunk
 * @param veinSize    blocks in one vein
 * @param hardness    which pickaxe is hard enough to get anything out of it
 * @param drillTier   how good a drill has to be to bring it up (Milestone 6)
 * @param rock        the rock type that carries it, for prospecting and the drill
 * @param placeholder the vanilla ore this borrows its look from until it has art of its own
 */
public enum Metal {
    TIN("tin", -40, 90, 6, 8, Hardness.STONE, 2, Rock.SEDIMENTARY, "copper"),
    ZINC("zinc", -40, 80, 5, 8, Hardness.STONE, 2, Rock.SEDIMENTARY, "iron"),
    LEAD("lead", -60, 60, 5, 8, Hardness.STONE, 2, Rock.SEDIMENTARY, "coal"),
    MANGANESE("manganese", -50, 60, 4, 7, Hardness.STONE, 3, Rock.SEDIMENTARY, "coal"),
    MAGNESIUM("magnesium", -30, 70, 4, 7, Hardness.STONE, 3, Rock.SEDIMENTARY, "lapis"),
    ALUMINUM("aluminum", 20, 130, 5, 9, Hardness.STONE, 3, Rock.SEDIMENTARY, "iron"),
    NICKEL("nickel", -80, 20, 3, 6, Hardness.IRON, 3, Rock.VOLCANIC, "iron"),
    COBALT("cobalt", -100, 0, 3, 5, Hardness.IRON, 4, Rock.VOLCANIC, "lapis"),
    CHROMIUM("chromium", -120, -20, 3, 5, Hardness.IRON, 4, Rock.VOLCANIC, "emerald"),
    MERCURY("mercury", -120, 0, 2, 4, Hardness.IRON, 4, Rock.VOLCANIC, "redstone"),
    TITANIUM("titanium", -140, -20, 2, 5, Hardness.IRON, 5, Rock.GRANITE, "iron"),
    TUNGSTEN("tungsten", -200, -60, 2, 4, Hardness.DIAMOND, 5, Rock.GRANITE, "diamond"),
    URANIUM("uranium", -200, -40, 2, 4, Hardness.IRON, 5, Rock.GRANITE, "emerald"),
    THORIUM("thorium", -220, -80, 2, 4, Hardness.IRON, 5, Rock.GRANITE, "emerald"),
    PLATINUM("platinum", -220, -100, 1, 3, Hardness.DIAMOND, 5, Rock.VOLCANIC, "gold");

    /** Which pickaxe is hard enough. Vanilla already owns these lines, so they are reused. */
    public enum Hardness {
        STONE("needs_stone_tool"),
        IRON("needs_iron_tool"),
        DIAMOND("needs_diamond_tool");

        private final String tag;

        Hardness(String tag) {
            this.tag = tag;
        }

        /** The vanilla block tag that states this requirement. */
        public String tag() {
            return tag;
        }
    }

    /** The rock of Milestone 6 that carries this metal. */
    public enum Rock {
        GRANITE, VOLCANIC, SEDIMENTARY;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final String id;
    private final int minY;
    private final int maxY;
    private final int veinsPerChunk;
    private final int veinSize;
    private final Hardness hardness;
    private final int drillTier;
    private final Rock rock;
    private final String placeholder;

    Metal(String id, int minY, int maxY, int veinsPerChunk, int veinSize, Hardness hardness,
          int drillTier, Rock rock, String placeholder) {
        this.id = id;
        this.minY = minY;
        this.maxY = maxY;
        this.veinsPerChunk = veinsPerChunk;
        this.veinSize = veinSize;
        this.hardness = hardness;
        this.drillTier = drillTier;
        this.rock = rock;
        this.placeholder = placeholder;
    }

    public String id() {
        return id;
    }

    public String oreId() {
        return id + "_ore";
    }

    public String deepslateOreId() {
        return "deepslate_" + id + "_ore";
    }

    public String rawId() {
        return "raw_" + id;
    }

    public String ingotId() {
        return id + "_ingot";
    }

    public String powderId() {
        return id + "_powder";
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public int veinsPerChunk() {
        return veinsPerChunk;
    }

    public int veinSize() {
        return veinSize;
    }

    public Hardness hardness() {
        return hardness;
    }

    public int drillTier() {
        return drillTier;
    }

    public Rock rock() {
        return rock;
    }

    public String placeholder() {
        return placeholder;
    }

    /** True where a fire pit is enough: the metals the early game is allowed to reach. */
    public boolean smeltsInAFirePit() {
        return hardness == Hardness.STONE && drillTier <= 2;
    }

    public static Metal byId(String id) {
        if (id == null) return null;
        for (Metal metal : values()) {
            if (metal.id.equals(id.toLowerCase(Locale.ROOT))) return metal;
        }
        return null;
    }
}
