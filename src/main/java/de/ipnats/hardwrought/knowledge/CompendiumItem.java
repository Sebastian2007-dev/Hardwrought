package de.ipnats.hardwrought.knowledge;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The compendium as a thing a player can hold: section 79 wants the book in the world, not only
 * behind a key.
 *
 * <p>The browser itself is a screen and therefore client-only, so the item asks through a hook the
 * client fills in at start-up. On a dedicated server the hook stays empty and the item does nothing,
 * which is the correct behaviour for a book nobody is looking at.
 */
public class CompendiumItem extends Item {
    /** Set by the client initializer; opens the browser on the shelf it was last left on. */
    public static Runnable opener = () -> { };

    public CompendiumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) opener.run();
        return InteractionResult.SUCCESS;
    }
}
