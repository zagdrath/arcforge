/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.settings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A port on a formed multiblock: one outer face of one of its blocks, set to a mode. Multiblocks do all their
 * automation through ports instead of side configuration.
 *
 * @param pos  the block the port is on
 * @param face the face of that block, pointing out of the structure
 * @param mode the port's mode, one of the {@link SideModes} ids
 */
public record MachinePort(BlockPos pos, Direction face, String mode) {}
