/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

import java.util.Optional;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.recipe.ElectrolyzingRecipe;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Keeps FE -> fuel -> FE loops losing energy. From live config and data, it works out the most FE any setup gets
// back from a mB of a burnable fluid, so the Electrolyzer can charge more than that (see minEnergyFor).
//
// If another way to turn heat into FE is ever added, add it to bestFePerHu() (and to the hydrogen_net_negative
// GameTest), or hydrogen could become a free power source.
public final class EnergyBalance {
    private EnergyBalance() {}

    // The most FE one HU becomes, over every heat -> FE route, fully upgraded.
    public static double bestFePerHu() {
        return Math.max(thermoelectricFePerHu(), bestSteamFePerHu());
    }

    // A Thermoelectric Plant at its bonus temperature with every efficiency card.
    public static double thermoelectricFePerHu() {
        return ArcforgeConfig.THERMOELECTRIC_BONUS_EFFICIENCY.getAsDouble()
                * (1.0 + ArcforgeConfig.THERMOELECTRIC_UPGRADE_EFFICIENCY.getAsDouble() * UpgradeType.MAX_PER_MACHINE);
    }

    // Steam Boiler Array into a lubricated Steam Turbine Array with its exhaust draining, over every grade, boiled
    // directly or raised by a Superheater Array.
    public static double bestSteamFePerHu() {
        double best = 0.0;
        for (SteamGrade made : SteamGrade.values()) {
            for (SteamGrade boiled : SteamGrade.values()) {
                if (boiled.ordinal() <= made.ordinal()) {
                    best = Math.max(best, steamFePerHu(boiled, made));
                }
            }
        }
        return best;
    }

    // FE per HU boiling into one grade and (if different) superheating it into another.
    public static double steamFePerHu(SteamGrade boiled, SteamGrade made) {
        double hu = boiled.huPerMb() * ArcforgeConfig.BOILER_ARRAY_HEAT_COST.getAsDouble()
                + (made.huPerMb() - boiled.huPerMb()) * ArcforgeConfig.SUPERHEATER_HEAT_COST.getAsDouble();
        double fe = made.arrayFePerMb() * (1.0 + ArcforgeConfig.LUBRICANT_OUTPUT_BONUS.getAsDouble()
                + ArcforgeConfig.TURBINE_ARRAY_VACUUM_BONUS.getAsDouble());
        return fe / hu;
    }

    // The most a burner gets out of each mB: every Heat upgrade.
    public static double maxHeatMultiplier() {
        return UpgradeType.outputMultiplier(UpgradeType.MAX_PER_MACHINE);
    }

    // The most FE a mB of this fluid can give back when burnt, or 0 if it isn't a fuel.
    public static double recoverableFePerMb(Fluid fluid) {
        BurnerFuel fuel = BurnerFuel.of(fluid);
        return fuel == null ? 0.0 : fuel.huPerMb() * maxHeatMultiplier() * bestFePerHu();
    }

    // The least FE an operation of this recipe may cost: the safety factor times what its products give back.
    public static int minEnergyFor(ElectrolyzingRecipe recipe) {
        double recoverable = recoverable(Optional.of(recipe.primary())) + recoverable(recipe.secondary());
        return ceil(ArcforgeConfig.ELECTROLYZER_BALANCE_SAFETY_FACTOR.getAsDouble() * recoverable);
    }

    // Rounds up, ignoring floating-point noise (61,950.000000001 is 61,950).
    public static int ceil(double value) {
        return (int) Math.ceil(value - 1e-6);
    }

    private static double recoverable(Optional<FluidStackTemplate> output) {
        return output.map(template -> template.amount() * recoverableFePerMb(template.fluid().value())).orElse(0.0);
    }
}
