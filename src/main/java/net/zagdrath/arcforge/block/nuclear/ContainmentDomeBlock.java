/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

// The Containment Dome: lined Containment Concrete cast as a curve, placed like stairs (its corners round inside and
// out) to build a domed roof. It's its own lining: it seals a containment with nothing behind it (see Containment).
public class ContainmentDomeBlock extends StairBlock {
    public ContainmentDomeBlock(BlockState base, BlockBehaviour.Properties properties) {
        super(base, properties);
    }
}
