/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// Boils water into steam with heat, for the Steam Boiler and the Steam Boiler Array. It only boils at
// 100°C or hotter, and the grade of steam depends on the temperature (see SteamGrade). Each tick it
// uses up to maxHeatPerTick HU, so a boiler fed more heat than that climbs toward its heat source's
// temperature and makes better steam, while an underfed one sits near 100°C making plain steam.
// Water turns into steam 1 mB for 1 mB; fractions of a mB (and of an HU) carry over to the next tick.
//
// The steam tank holds one grade. If the boiler cools into a lower grade, the stored steam drops to it
// (the pressure falls, the amount stays). If it heats into a higher grade, it keeps making the stored
// grade until the tank empties, then switches.
public class BoilerCore {
    public enum State { BOILING, HEATING, NO_WATER, STEAM_FULL }

    private final HeatBuffer heat;
    private final FilteredFluidTank water;
    private final FilteredFluidTank steam;
    private double pendingSteam;
    private double pendingHeat;
    // mB/t made and HU/t used last tick, for the GUI.
    private double rate;
    private int heatUsed;

    public BoilerCore(HeatBuffer heat, FilteredFluidTank water, FilteredFluidTank steam) {
        this.heat = heat;
        this.water = water;
        this.steam = steam;
    }

    public static boolean isWater(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().defaultFluidState().is(FluidTags.WATER);
    }

    public static boolean isSteam(FluidResource resource) {
        return SteamGrade.of(resource) != null;
    }

    // The grade the boiler is hot enough to make right now, or null below boiling.
    public @Nullable SteamGrade currentGrade() {
        return SteamGrade.forTemperature(heat.getTemperature());
    }

    public double getRate() {
        return rate;
    }

    public int getHeatUsed() {
        return heatUsed;
    }

    // One tick. costMultiplier scales the heat per mB (the array's big drum loses less).
    public State tick(int maxHeatPerTick, double costMultiplier) {
        rate = 0;
        heatUsed = 0;
        SteamGrade grade = currentGrade();
        if (grade == null) {
            return State.HEATING;
        }
        SteamGrade making = gradeToMake(grade);
        if (water.getAmount() <= 0) {
            return State.NO_WATER;
        }
        if (steam.getSpace() <= 0) {
            return State.STEAM_FULL;
        }

        double huPerMb = making.huPerMb() * costMultiplier;
        double possible = Math.min(maxHeatPerTick, heat.getStored() - pendingHeat) / huPerMb;
        double boil = Math.max(0, Math.min(possible, Math.min(water.getAmount(), steam.getSpace()) - pendingSteam));
        pendingSteam += boil;
        pendingHeat += boil * huPerMb;
        int mb = (int) Math.min(Math.floor(pendingSteam), Math.min(water.getAmount(), steam.getSpace()));
        int hu = (int) Math.min(Math.floor(pendingHeat), heat.getStored());
        pendingSteam -= mb;
        pendingHeat -= hu;
        heat.remove(hu);
        if (mb > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                water.extract(0, water.getResource(0), mb, tx);
                steam.insert(0, making.resource(), mb, tx);
                tx.commit();
            }
        }
        rate = boil;
        heatUsed = (int) Math.round(boil * huPerMb);
        return boil > 0 ? State.BOILING : State.HEATING;
    }

    // Applies the grade rules to the stored steam and returns the grade to make this tick.
    private SteamGrade gradeToMake(SteamGrade grade) {
        SteamGrade stored = steam.getAmount() > 0 ? SteamGrade.of(steam.getResource(0)) : null;
        if (stored == null) {
            return grade;
        }
        if (grade.ordinal() < stored.ordinal()) {
            steam.set(0, grade.resource(), steam.getAmount());
            return grade;
        }
        return stored;
    }

    public void serialize(ValueOutput output) {
        output.putDouble("pending_steam", pendingSteam);
        output.putDouble("pending_heat", pendingHeat);
    }

    public void deserialize(ValueInput input) {
        pendingSteam = input.getDoubleOr("pending_steam", 0.0);
        pendingHeat = input.getDoubleOr("pending_heat", 0.0);
    }
}
