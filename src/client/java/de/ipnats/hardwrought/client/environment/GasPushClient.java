package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.core.networking.GasPushPayload;
import de.ipnats.hardwrought.environment.Gases;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The client half of pushing gas (see {@code GasPush}). Gas has no outline, so the crosshair never
 * lands on it; a sneaking swing instead looks along the line of sight for the first gas block before
 * anything solid, and asks the server to push it the way the player is looking.
 */
public final class GasPushClient {
    /** How finely the line of sight is walked, in blocks. */
    private static final double STEP = 0.05;

    private GasPushClient() { }

    /** Called on a swing. Returns true when it pushed gas, and the swing should do nothing else. */
    public static boolean trySwing(Minecraft client) {
        if (client.player == null || client.level == null || !client.player.isShiftKeyDown()
                || client.player.isSpectator()) return false;
        if (!ClientPlayNetworking.canSend(GasPushPayload.TYPE)) return false;
        Vec3 eye = client.player.getEyePosition();
        Vec3 look = client.player.getLookAngle();
        double reach = client.player.blockInteractionRange();
        // Something aimed at nearer than the gas — a block, a creature — is what the swing is for.
        double blocked = reach;
        if (client.hitResult != null && client.hitResult.getType() != HitResult.Type.MISS) {
            blocked = Math.min(blocked, client.hitResult.getLocation().distanceTo(eye));
        }
        BlockPos found = null;
        for (double along = 0; along <= blocked; along += STEP) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(along)));
            BlockState state = client.level.getBlockState(pos);
            if (Gases.isGas(state)) {
                found = pos;
                break;
            }
            if (!Gases.passable(state) && !state.getCollisionShape(client.level, pos).isEmpty()) break;
        }
        if (found == null) return false;
        ClientPlayNetworking.send(new GasPushPayload(found, Direction.getApproximateNearest(look)));
        client.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        return true;
    }
}
