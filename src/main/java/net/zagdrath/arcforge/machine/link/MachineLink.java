/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.link;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// A machine's Wired Machine Link, as the machine (or a structure's controller) keeps it: the block the Link Conduit is
// attached to (the machine itself, or any outer block of a structure), the face of that block it's on, and the Wired
// Machine Link block at the cable's other end.
public record MachineLink(BlockPos anchor, Direction face, BlockPos linkPos) {
    public static final Codec<MachineLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("anchor").forGetter(MachineLink::anchor),
            Direction.CODEC.fieldOf("face").forGetter(MachineLink::face),
            BlockPos.CODEC.fieldOf("link").forGetter(MachineLink::linkPos)).apply(instance, MachineLink::new));

    public boolean isFace(BlockPos pos, Direction side) {
        return anchor.equals(pos) && face == side;
    }
}
