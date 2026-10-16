package de.ipnats.hardwrought.client.witchking;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the Witch King's renderer needs of him for one frame. */
public class WitchKingRenderState extends LivingEntityRenderState {
    /** 0 to 1: how far his arms are raised to cast. */
    public float cast;
    /** How fast he moves, blocks per tick. */
    public float speed;
    public int phase;
    public boolean dying;
}
