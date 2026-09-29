/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.function.DoubleSupplier;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// The modules an Arc Drill or Arc Saw takes (see ToolModules). Each adds a share of the base FE per block while on.
public enum ModuleType implements StringRepresentable {
    AREA("area", ArcforgeConfig.MODULE_AREA_FE::getAsDouble),
    SILK_TOUCH("silk_touch", ArcforgeConfig.MODULE_SILK_FE::getAsDouble),
    FORTUNE_I("fortune_i", ArcforgeConfig.MODULE_FORTUNE_FE::getAsDouble),
    FORTUNE_II("fortune_ii", () -> 2 * ArcforgeConfig.MODULE_FORTUNE_FE.getAsDouble()),
    FORTUNE_III("fortune_iii", () -> 3 * ArcforgeConfig.MODULE_FORTUNE_FE.getAsDouble()),
    VEIN("vein", ArcforgeConfig.MODULE_VEIN_FE::getAsDouble),
    SPEED("speed", ArcforgeConfig.MODULE_SPEED_FE::getAsDouble);

    public static final Codec<ModuleType> CODEC = StringRepresentable.fromEnum(ModuleType::values);

    private final String name;
    private final DoubleSupplier feFactor;

    ModuleType(String name, DoubleSupplier feFactor) {
        this.name = name;
        this.feFactor = feFactor;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public double feFactor() {
        return feFactor.getAsDouble();
    }

    public boolean isFortune() {
        return this == FORTUNE_I || this == FORTUNE_II || this == FORTUNE_III;
    }

    // Its Fortune level, 0 for the others.
    public int fortuneLevel() {
        return switch (this) {
            case FORTUNE_I -> 1;
            case FORTUNE_II -> 2;
            case FORTUNE_III -> 3;
            default -> 0;
        };
    }

    // Silk Touch and Fortune can both be installed but never both on.
    public boolean conflictsWith(ModuleType other) {
        return (this == SILK_TOUCH && other.isFortune()) || (isFortune() && other == SILK_TOUCH);
    }

    public Component displayName() {
        return Component.translatable("module.arcforge." + name);
    }
}
