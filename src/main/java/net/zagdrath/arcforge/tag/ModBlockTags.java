/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.zagdrath.arcforge.Arcforge;

public final class ModBlockTags {
    // Every block of an Arcforge multiblock.
    public static final TagKey<Block> MULTIBLOCK_PARTS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Arcforge.MODID, "multiblock_parts"));

    // What the Block Breaker never breaks (the multiblock parts, spawners, vaults and the like).
    public static final TagKey<Block> BREAKER_BLACKLIST = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Arcforge.MODID, "breaker_blacklist"));

    private ModBlockTags() {}
}
