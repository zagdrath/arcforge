/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import net.minecraft.world.item.Item;

// A module card for an Arc Drill or Arc Saw, installed in the tool's module GUI (see ArcToolMenu).
public class ToolModuleItem extends Item {
    private final ModuleType type;

    public ToolModuleItem(ModuleType type, Item.Properties properties) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public ModuleType type() {
        return type;
    }
}
