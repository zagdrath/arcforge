/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// A block whose setup a Settings Card copies and pastes: side or port modes, redstone mode, auto-eject, and a few
// machine-specific settings. Never items, fluids, energy or upgrades.
public interface SettingsCopyable {
    // What a card must have been copied from to paste here: the block's id, or a structure's block entity type.
    Identifier settingsKind();

    // A structure's size in its own frame; a card pastes only onto the same size. Empty for single blocks.
    default Optional<SettingsCardData.StructureSize> settingsSize() {
        return Optional.empty();
    }

    void writeSettings(ValueOutput output);

    // Applies what writeSettings wrote; returns how many settings couldn't be applied here.
    int readSettings(ValueInput input);

    // Tooltip lines for a card holding these settings.
    List<Component> describe(ValueInput input);
}
