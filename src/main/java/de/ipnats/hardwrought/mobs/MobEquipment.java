package de.ipnats.hardwrought.mobs;

import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What hostile mobs spawn holding, where it changes what they can do (mob specification §§ 5, 28, 42,
 * 45). The equipment is always in hand and so always visible: a zombie with an axe is a threat to
 * wooden walls, and the player can see that before it is one.
 */
public final class MobEquipment {
    /** The kinds of tool a zombie can come with; each opens a different kind of wall. */
    public enum Tool { AXE, PICKAXE, SHOVEL }

    /** Share of zombies with a tool on the first day, at the surface. */
    static final double BASE_TOOL_CHANCE = 0.03;
    /** Added for every day the world has run. */
    static final double TOOL_CHANCE_PER_DAY = 0.004;
    static final double MAX_TOOL_CHANCE = 0.25;
    /** Wither skeletons that come with a bow instead of a sword (§ 28.2). */
    static final double WITHER_BOW_CHANCE = 0.2;
    /** Section 44: share of armed zombies that come with an archer, and of those with a witch too. */
    static final double GROUP_CHANCE = 0.2, GROUP_WITCH_CHANCE = 0.35;

    private MobEquipment() { }

    /**
     * How far along the world is, from 0 for a fresh surface to about 1 and more: a month in, or deep
     * underground. Tool chance and quality both grow with it.
     */
    public static double threat(long day, int y) {
        double depth = y < -32 ? 0.6 : y < 0 ? 0.3 : 0;
        return day / 30.0 + depth;
    }

    public static double toolChance(long day, int y) {
        double depth = y < -32 ? 0.08 : y < 0 ? 0.04 : 0;
        return Mth.clamp(BASE_TOOL_CHANCE + TOOL_CHANCE_PER_DAY * day + depth, 0, MAX_TOOL_CHANCE);
    }

    /** The best tier on offer at this threat: flint, then stone, bronze and iron. */
    static int maxTier(double threat) {
        return threat >= 1.0 ? 3 : threat >= 0.6 ? 2 : threat >= 0.3 ? 1 : 0;
    }

    public static Item tool(Tool kind, int tier) {
        return switch (kind) {
            case AXE -> switch (tier) {
                case 0 -> ModItems.FLINT_HATCHET;
                case 1 -> Items.STONE_AXE;
                case 2 -> ModItems.BRONZE_HATCHET;
                default -> Items.IRON_AXE;
            };
            case PICKAXE -> switch (tier) {
                case 0 -> ModItems.FLINT_PICKAXE;
                case 1 -> Items.STONE_PICKAXE;
                case 2 -> ModItems.BRONZE_PICKAXE;
                default -> Items.IRON_PICKAXE;
            };
            // There is no bronze shovel: stone stands in for it.
            case SHOVEL -> switch (tier) {
                case 0 -> ModItems.FLINT_SHOVEL;
                case 1, 2 -> Items.STONE_SHOVEL;
                default -> Items.IRON_SHOVEL;
            };
        };
    }

    /** A zombie that spawned naturally: perhaps with a tool, worn from use, which it will also keep. */
    public static void equipZombie(Zombie zombie, RandomSource random) {
        // Any zombie can pick a tool up that it walks over, and becomes the threat that tool makes it.
        zombie.setCanPickUpLoot(true);
        long day = zombie.level().getOverworldClockTime() / 24000L;
        int y = zombie.blockPosition().getY();
        if (random.nextDouble() >= toolChance(day, y)) return;
        int roll = random.nextInt(100);
        Tool kind = roll < 40 ? Tool.AXE : roll < 75 ? Tool.SHOVEL : Tool.PICKAXE;
        // Lower tiers stay likelier than the best one on offer.
        int max = maxTier(threat(day, y));
        int tier = Math.min(random.nextInt(max + 1), random.nextInt(max + 1));
        ItemStack stack = new ItemStack(tool(kind, tier));
        if (stack.isDamageableItem()) {
            stack.setDamageValue(random.nextInt(Math.max(1, stack.getMaxDamage() / 2)));
        }
        zombie.setItemSlot(EquipmentSlot.MAINHAND, stack);
        // Section 44: difficulty through composition. An armed zombie sometimes comes with an archer
        // to cover it, and now and then a witch to back them both.
        if (random.nextDouble() < GROUP_CHANCE && zombie.level() instanceof net.minecraft.server.level.ServerLevel level) {
            boolean witch = random.nextDouble() < GROUP_WITCH_CHANCE;
            net.minecraft.core.BlockPos at = zombie.blockPosition();
            // After this spawn is finished, not in the middle of it.
            level.getServer().execute(() -> {
                if (!zombie.isAlive()) return;
                net.minecraft.world.entity.EntityTypes.SKELETON.spawn(level, at, net.minecraft.world.entity.EntitySpawnReason.REINFORCEMENT);
                if (witch) net.minecraft.world.entity.EntityTypes.WITCH.spawn(level, at, net.minecraft.world.entity.EntitySpawnReason.REINFORCEMENT);
            });
        }
    }

    /** § 28.2: a minority of wither skeletons carry a bow, and their arrows burn. */
    public static void equipWitherSkeleton(WitherSkeleton skeleton, RandomSource random) {
        if (random.nextDouble() < WITHER_BOW_CHANCE) {
            // Setting the hand reassesses the skeleton's weapon goal: it fights as an archer.
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }
    }
}
