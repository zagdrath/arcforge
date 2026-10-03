/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.CombustionFuel;
import net.zagdrath.arcforge.machine.PowerGeneration;
import net.zagdrath.arcforge.recipe.CarbonReclaimingRecipe;
import net.zagdrath.arcforge.recipe.CarbonizingRecipe;
import net.zagdrath.arcforge.recipe.ElectrolyzingRecipe;
import net.zagdrath.arcforge.recipe.GasifyingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Keeps FE -> fuel -> FE loops losing energy. From live config and data, it works out the most FE any setup gets
// back from a mB of a burnable fluid, so the Electrolyzer can charge more than that (see minEnergyFor).
//
// If another way to turn heat into FE is ever added, add it to bestFePerHu() (and to the hydrogen_net_negative
// GameTest), or hydrogen could become a free power source. The Gas Turbine Array burns fuel straight to FE, so
// recoverableFePerMb also counts it (gasTurbineFePerHu). Every route counts power.generationMultiplier (see
// PowerGeneration), so turning it up raises the Electrolyzer's floor with it and hydrogen still never makes free power.
public final class EnergyBalance {
    private EnergyBalance() {}

    // The most FE one HU becomes, over every heat -> FE route, fully upgraded.
    public static double bestFePerHu() {
        return Math.max(thermoelectricFePerHu(), bestSteamFePerHu());
    }

    // A Thermoelectric Plant at its bonus temperature with every efficiency card.
    public static double thermoelectricFePerHu() {
        return ArcforgeConfig.THERMOELECTRIC_BONUS_EFFICIENCY.getAsDouble()
                * (1.0 + ArcforgeConfig.THERMOELECTRIC_UPGRADE_EFFICIENCY.getAsDouble() * UpgradeType.MAX_PER_MACHINE) * PowerGeneration.multiplier();
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
                + ArcforgeConfig.TURBINE_ARRAY_VACUUM_BONUS.getAsDouble()) * PowerGeneration.multiplier();
        return fe / hu;
    }

    // The most a burner gets out of each mB: every Heat upgrade.
    public static double maxHeatMultiplier() {
        return UpgradeType.outputMultiplier(UpgradeType.MAX_PER_MACHINE);
    }

    // The most FE a mB of this fluid can give back when burnt, or 0 if it isn't a fuel: through a fully upgraded
    // burner and the best heat route, or through a lubricated Gas Turbine Array with its exhaust raising steam.
    public static double recoverableFePerMb(Fluid fluid) {
        BurnerFuel fuel = BurnerFuel.of(fluid);
        if (fuel == null) {
            return 0.0;
        }
        double burner = fuel.huPerMb() * maxHeatMultiplier() * bestFePerHu();
        if (!fuel.gasTurbine()) {
            return burner;
        }
        int celsius = fuel.burnTemperature().orElse(ArcforgeConfig.GAS_TURBINE_REFERENCE_TEMPERATURE.getAsInt());
        return Math.max(burner, fuel.huPerMb() * gasTurbineFePerHu(celsius));
    }

    // FE per HU of fuel a lubricated Gas Turbine Array makes burning at this temperature, counting its exhaust
    // heat turned into FE by the best steam route (the combined cycle).
    public static double gasTurbineFePerHu(int celsius) {
        double efficiency = Math.min(1.0, celsius / (double) ArcforgeConfig.GAS_TURBINE_REFERENCE_TEMPERATURE.getAsInt());
        return ArcforgeConfig.GAS_TURBINE_SIMPLE_CYCLE_FACTOR.getAsDouble() * efficiency * (1.0 + ArcforgeConfig.LUBRICANT_OUTPUT_BONUS.getAsDouble())
                * PowerGeneration.multiplier() + ArcforgeConfig.GAS_TURBINE_EXHAUST_FRACTION.getAsDouble() * bestSteamFePerHu();
    }

    // The least FE an operation of this recipe may cost: the safety factor times what its products give back.
    public static int minEnergyFor(ElectrolyzingRecipe recipe) {
        double recoverable = recoverable(Optional.of(recipe.primary())) + recoverable(recipe.secondary()) + recoverable(recipe.tertiary());
        return ceil(ArcforgeConfig.ELECTROLYZER_BALANCE_SAFETY_FACTOR.getAsDouble() * recoverable);
    }

    // The least FE a Carbon Reclaimer operation may cost: the safety factor times the most FE its product can give back
    // (see recoverableFePerItem). The level and machine resolve burn times, as a furnace would.
    public static int minEnergyFor(CarbonReclaimingRecipe recipe, ServerLevel level, BlockEntity machine) {
        ItemStack result = recipe.result().create();
        double recoverable = result.getCount() * recoverableFePerItem(level, machine, result);
        return ceil(ArcforgeConfig.RECLAIMER_BALANCE_SAFETY_FACTOR.getAsDouble() * recoverable);
    }

    // The most FE one of this item can give back, over every route that burns it: a Combustion Plant with every Energy
    // upgrade; a Firebox with every Heat upgrade on oxy-fuel, its heat turned into FE by the best route; a Firebox Array on
    // oxy-fuel (which also burns Coal Coke); or first baked in the Carbonizer (counting its Creosote) or gasified in the
    // Gasifier into Syngas, and burnt from there. 0 if nothing burns it.
    public static double recoverableFePerItem(ServerLevel level, BlockEntity machine, ItemStack stack) {
        return recoverableFePerItem(level, machine, stack, 3);
    }

    private static double recoverableFePerItem(ServerLevel level, BlockEntity machine, ItemStack stack, int depth) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        ItemStack one = stack.copyWithCount(1);
        double best = 0.0;
        if (CombustionFuel.isFuel(one)) {
            int ticks = CombustionFuel.vanillaBurnTicks(level, machine, one);
            double plant = ticks / ArcforgeConfig.COMBUSTION_PLANT_BURN_SPEED.getAsDouble() * ArcforgeConfig.COMBUSTION_PLANT_ENERGY_PER_TICK.getAsInt()
                    * UpgradeType.outputMultiplier(UpgradeType.MAX_PER_MACHINE) * PowerGeneration.multiplier();
            double firebox = FireboxArrayBlockEntity.fireboxHeat(ticks) * maxHeatMultiplier() * OxyFuel.heatMultiplier() * bestFePerHu();
            best = Math.max(plant, firebox);
        }
        if (FireboxArrayBlockEntity.isSolidFuel(one)) {
            best = Math.max(best, FireboxArrayBlockEntity.solidHeat(level, machine, one) * OxyFuel.heatMultiplier() * bestFePerHu());
        }
        if (depth > 0) {
            CarbonizingRecipe baked = MachineRecipes.carbonizing(level, one).map(holder -> holder.value()).orElse(null);
            if (baked != null) {
                ItemStack result = baked.result().create();
                double fe = result.getCount() * recoverableFePerItem(level, machine, result, depth - 1)
                        + baked.byproduct().map(out -> out.amount() * recoverableFePerMb(out.fluid().value())).orElse(0.0);
                best = Math.max(best, fe);
            }
            GasifyingRecipe gasified = MachineRecipes.gasifying(level, one).map(holder -> holder.value()).orElse(null);
            if (gasified != null) {
                // Counting oxy-fuel's extra heat too (recoverableFePerMb leaves it out), to stay on the safe side.
                best = Math.max(best, gasified.result().amount() * recoverableFePerMb(gasified.result().fluid().value()) * OxyFuel.heatMultiplier()
                        / gasified.count());
            }
        }
        return best;
    }

    // Rounds up, ignoring floating-point noise (61,950.000000001 is 61,950).
    public static int ceil(double value) {
        return (int) Math.ceil(value - 1e-6);
    }

    private static double recoverable(Optional<FluidStackTemplate> output) {
        return output.map(template -> template.amount() * recoverableFePerMb(template.fluid().value())).orElse(0.0);
    }
}
