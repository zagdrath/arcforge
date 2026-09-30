/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;

// What ClocheRenderer draws inside a farm: the soil (none in the Hydroponic Cell, or with the slot empty), and the
// plant at its growth, shrunk to fit inside (and, for plants that grow in size, shrunk further while young).
public class ClocheRenderState extends BlockEntityRenderState {
    public ClocheBlockEntity.Kind kind = ClocheBlockEntity.Kind.GLASS_CLOCHE;
    public final BlockModelRenderState soil = new BlockModelRenderState();
    public final BlockModelRenderState plant = new BlockModelRenderState();
    // The plant's scale: 1 draws a one-block plant at the full size of the farm's crop space.
    public float plantScale;
    public int light;
}
