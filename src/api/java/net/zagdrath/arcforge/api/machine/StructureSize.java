/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine;

/**
 * The size of a formed multiblock's bounding box, in blocks, relative to the way the structure faces.
 *
 * @param width  the size across the structure's front
 * @param height the size along the Y axis
 * @param depth  the size from the structure's front to its back
 */
public record StructureSize(int width, int height, int depth) {}
