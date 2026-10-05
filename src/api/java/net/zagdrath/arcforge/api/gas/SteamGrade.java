/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.gas;

import java.util.Optional;

import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.api.ArcforgeApi;

/**
 * Arcforge's three grades of steam, each its own gas. A boiler makes the grade its temperature reaches; hotter grades
 * are at higher pressure, cost more heat per mB to boil and give a turbine more FE per mB. Tanks and conduits never
 * mix grades. See {@link Gas#steamGrade()}.
 */
public enum SteamGrade {
    /** Steam ({@code arcforge:steam}), boiled from 100 °C. */
    STEAM("steam"),
    /** High-Pressure Steam ({@code arcforge:high_pressure_steam}), boiled from 500 °C. */
    HIGH_PRESSURE("high_pressure_steam"),
    /** Superheated Steam ({@code arcforge:superheated_steam}), boiled from 900 °C. */
    SUPERHEATED("superheated_steam");

    private final Identifier gasId;

    SteamGrade(String path) {
        this.gasId = Identifier.fromNamespaceAndPath(ArcforgeApi.MOD_ID, path);
    }

    /**
     * Returns the id of this grade's gas.
     *
     * @return the gas id, e.g. {@code arcforge:high_pressure_steam}
     */
    public Identifier gasId() {
        return gasId;
    }

    /**
     * Returns this grade's gas.
     *
     * @return the gas; empty only before Arcforge's fluids are registered
     */
    public Optional<Gas> gas() {
        return GasRegistry.get(gasId);
    }

    static Optional<SteamGrade> of(Identifier gasId) {
        for (SteamGrade grade : values()) {
            if (grade.gasId.equals(gasId)) {
                return Optional.of(grade);
            }
        }
        return Optional.empty();
    }
}
