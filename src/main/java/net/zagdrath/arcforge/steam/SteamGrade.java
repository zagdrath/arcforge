/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.registry.ModFluids;

// The three steam grades a boiler makes, by how hot it is: hotter steam is at higher pressure, costs more
// heat per mB to boil and gives a turbine more FE per mB. Each is its own fluid, so tanks and conduit
// networks never mix them.
public enum SteamGrade {
    //                  from °C  HU/mB  FE/mB single, array  tint         GUI colour
    STEAM("steam", 100, 10, 8, 10, 0xFFF4F7F9, 0xFFD8E0E6, ModFluids.STEAM),
    HIGH_PRESSURE("high_pressure_steam", 500, 15, 14, 18, 0xFFCFE6FF, 0xFF8CC8FF, ModFluids.HIGH_PRESSURE_STEAM),
    SUPERHEATED("superheated_steam", 900, 20, 22, 28, 0xFFFFE2C4, 0xFFFFBE78, ModFluids.SUPERHEATED_STEAM);

    private final String name;
    private final int minCelsius;
    private final int huPerMb;
    private final int fePerMb;
    private final int arrayFePerMb;
    private final int tint;
    private final int guiColor;
    private final Supplier<? extends Fluid> fluid;

    SteamGrade(String name, int minCelsius, int huPerMb, int fePerMb, int arrayFePerMb, int tint, int guiColor, Supplier<? extends Fluid> fluid) {
        this.name = name;
        this.minCelsius = minCelsius;
        this.huPerMb = huPerMb;
        this.fePerMb = fePerMb;
        this.arrayFePerMb = arrayFePerMb;
        this.tint = tint;
        this.guiColor = guiColor;
        this.fluid = fluid;
    }

    public String getSerializedName() {
        return name;
    }

    // The lowest temperature that boils water into this grade.
    public int minCelsius() {
        return minCelsius;
    }

    public int huPerMb() {
        return huPerMb;
    }

    // FE a Steam Turbine makes from each mB.
    public int fePerMb() {
        return fePerMb;
    }

    // FE a Steam Turbine Array makes from each mB.
    public int arrayFePerMb() {
        return arrayFePerMb;
    }

    // Colour the steam textures are tinted with.
    public int tint() {
        return tint;
    }

    // Colour of the short grade name in GUIs.
    public int guiColor() {
        return guiColor;
    }

    public Fluid fluid() {
        return fluid.get();
    }

    public FluidResource resource() {
        return FluidResource.of(fluid());
    }

    // "Steam", "High-Pressure" or "Superheated", for GUIs where the full name doesn't fit.
    public Component shortName() {
        return Component.translatable("gui.arcforge.steam_grade." + name);
    }

    // The grade a boiler at this temperature makes, or null below boiling.
    public static @Nullable SteamGrade forTemperature(int celsius) {
        SteamGrade grade = null;
        for (SteamGrade candidate : values()) {
            if (celsius >= candidate.minCelsius) {
                grade = candidate;
            }
        }
        return grade;
    }

    // The grade of this fluid, or null if it isn't steam.
    public static @Nullable SteamGrade of(Fluid fluid) {
        for (SteamGrade grade : values()) {
            if (grade.fluid().isSame(fluid)) {
                return grade;
            }
        }
        return null;
    }

    public static @Nullable SteamGrade of(FluidResource resource) {
        return resource.isEmpty() ? null : of(resource.getFluid());
    }
}
