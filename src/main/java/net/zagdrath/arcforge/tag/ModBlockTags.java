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

    // What the Arc Drill (pickaxe and shovel blocks) and Arc Saw (axe blocks) mine.
    public static final TagKey<Block> ARC_DRILL_MINEABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_drill_mineable"));
    public static final TagKey<Block> ARC_SAW_MINEABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_saw_mineable"));

    // What the Vein Mining module follows on an Arc Drill (ores), and what an Arc Saw fells (logs and stems).
    public static final TagKey<Block> VEIN_MINEABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Arcforge.MODID, "vein_mineable"));
    public static final TagKey<Block> FELLABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Arcforge.MODID, "fellable"));

    private ModBlockTags() {}
}
