/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

// A containment building's wall: Containment Concrete and the Steel Liner on it (see Containment). A class of its own
// so Jade can show the containment they bound.
public class ContainmentBlock extends Block {
    public ContainmentBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
