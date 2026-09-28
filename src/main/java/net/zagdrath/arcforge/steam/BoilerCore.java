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

// Boils water into steam with heat, for the Steam Boiler and the Steam Boiler Array. The grade of steam
// depends on the temperature (see SteamGrade), and the pressure setting (BoilerPressure) decides which:
//  - Auto boils only the heat above 100°C, making whatever grade the boiler is hot enough for. Each tick it
//    uses up to maxHeatPerTick HU, so a boiler fed more than that climbs toward its heat source's
//    temperature and makes better steam, while an underfed one holds at 100°C making plain steam.
//  - A set grade heats without boiling until the boiler reaches that grade's temperature, then boils only
//    the heat above it: the boiler holds there, making that grade at whatever rate its heat comes in.
// Water turns into steam 1 mB for 1 mB; fractions of a mB (and of an HU) carry over to the next tick.
//
// The steam tank holds one grade. If the boiler cools into a lower grade, the stored steam drops to it
// (the pressure falls, the amount stays). If it heats into a higher grade, it keeps making the stored
// grade until the tank empties, then switches.
public class BoilerCore {
    public enum State { BOILING, HEATING, NO_WATER, STEAM_FULL }

    // How long a boiler holding its temperature still counts as boiling after its last boil, so the
    // status stays put when heat arrives unevenly (e.g. on every other tick).
    private static final int BOILING_GRACE_TICKS = 20;
    // How fast the GUI's rates follow the actual ones (a share per tick).
    private static final double RATE_SMOOTHING = 0.1;

    private final HeatBuffer heat;
    private final FilteredFluidTank water;
    private final FilteredFluidTank steam;
    private BoilerPressure pressure = BoilerPressure.AUTO;
    private double pendingSteam;
    private double pendingHeat;
    private int boilingGrace;
    // What the last boil found (BOILING when it could boil, even if no heat was spare that tick), and the
    // heat per mB of what it made.
    private State lastState = State.HEATING;
    private double lastHeatPerMb;
    // mB/t made and HU/t used, smoothed, for the GUI.
    private double rate;
    private double heatUsed;

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
        return (int) Math.round(heatUsed);
    }

    public BoilerPressure getPressure() {
        return pressure;
    }

    public void setPressure(BoilerPressure pressure) {
        this.pressure = pressure;
    }

    // One tick. costMultiplier scales the heat per mB (the array's big drum loses less).
    public State tick(int maxHeatPerTick, double costMultiplier) {
        double boil = boil(maxHeatPerTick, costMultiplier);
        rate += (boil - rate) * RATE_SMOOTHING;
        heatUsed += (lastHeatPerMb * boil - heatUsed) * RATE_SMOOTHING;
        if (boil > 0) {
            boilingGrace = BOILING_GRACE_TICKS;
            return State.BOILING;
        }
        if (lastState != State.BOILING) {
            boilingGrace = 0;
            return lastState;
        }
        return boilingGrace-- > 0 ? State.BOILING : State.HEATING;
    }

    // Boils what it can this tick, returning the mB of steam made.
    private double boil(int maxHeatPerTick, double costMultiplier) {
        lastState = State.HEATING;
        SteamGrade grade = currentGrade();
        // The grade whose temperature the boiler holds: it only boils the heat above it.
        SteamGrade hold = pressure.grade() != null ? pressure.grade() : SteamGrade.STEAM;
        if (grade == null || grade.ordinal() < hold.ordinal()) {
            return 0;
        }
        SteamGrade making = gradeToMake(pressure.grade() != null ? hold : grade);
        if (water.getAmount() <= 0) {
            lastState = State.NO_WATER;
            return 0;
        }
        if (steam.getSpace() <= 0) {
            lastState = State.STEAM_FULL;
            return 0;
        }
        lastState = State.BOILING;

        double huPerMb = making.huPerMb() * costMultiplier;
        lastHeatPerMb = huPerMb;
        double spare = heat.getStored() - heat.minStoredAt(hold.minCelsius()) - pendingHeat;
        double possible = Math.max(0, Math.min(maxHeatPerTick, spare)) / huPerMb;
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
        return boil;
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
        output.putInt("pressure", pressure.ordinal());
    }

    public void deserialize(ValueInput input) {
        pendingSteam = input.getDoubleOr("pending_steam", 0.0);
        pendingHeat = input.getDoubleOr("pending_heat", 0.0);
        pressure = BoilerPressure.byId(input.getIntOr("pressure", 0));
    }
}
