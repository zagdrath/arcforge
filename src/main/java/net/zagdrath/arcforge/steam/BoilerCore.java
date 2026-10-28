/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// Boils water into steam with heat, for the Steam Boiler Array. The grade of steam depends on the temperature
// (see SteamGrade), and the pressure setting (BoilerPressure) picks the band of temperatures it runs in:
//  - Auto climbs as hot as it can (to just under its ceiling), making whatever grade it's hot enough for.
//  - A set grade climbs to the top of that grade's band (just under the next grade's temperature) and holds there.
//    Below the set grade's temperature it makes the grade it's already hot enough for, so it never stops boiling.
// From 100°C up to the top of its band it boils with half the heat coming in and climbs on the rest; at the top it
// boils all of it (up to maxHeatPerTick HU each tick). On a tick no heat comes in (its source is no hotter than it,
// or gone) it boils from what it holds at the rate heat had been coming in, so the steam doesn't stop: a boiler that
// lost its heat cools while it keeps boiling. Each such tick also halves the share it climbs on, so a boiler as hot
// as its source boils nearly all its heat there; the share grows back while heat comes in. The hotter it runs within
// the grade's band, the less heat each mB costs (none of hotSaving at the band's start, all of it at the top). Water
// turns into steam 1 mB for 1 mB; fractions of a mB (and of an HU) carry over to the next tick.
//
// Overpressure vents: steam that doesn't fit in a full tank is vented rather than stopping the boil, and heat that
// lifts the boiler past the top of its band (more than it can boil, or after the pressure was turned down) is
// vented, up to maxHeatPerTick HU a tick.
//
// The steam tank holds one grade. If the boiler cools into a lower grade, the stored steam drops to it
// (the pressure falls, the amount stays). If it heats into a higher grade, it first lifts the stored steam to it,
// paying the difference in heat per mB out of the heat it would boil with (meanwhile it gives out what it holds), then
// makes the new grade. So a big tank kept from emptying by a turbine still switches.
public class BoilerCore {
    public enum State { BOILING, HEATING, NO_WATER, VENTING }

    // How long a boiler holding its temperature still counts as boiling after its last boil, so the
    // status stays put when heat arrives unevenly (e.g. on every other tick). Venting lingers as long.
    private static final int BOILING_GRACE_TICKS = 20;
    // How fast the GUI's rates follow the actual ones (a share per tick).
    private static final double RATE_SMOOTHING = 0.1;
    // The most of the incoming heat it climbs on while under the top of its band, and how fast that share grows back
    // (a share per tick of heat) after a tick without heat halved it.
    private static final double CLIMB_SHARE = 0.5;
    private static final double CLIMB_SHARE_GROWTH = 0.005;
    // How fast the heat-in rate it boils at on unheated ticks follows the heat that comes in (a share per heated tick).
    private static final double HEAT_IN_SMOOTHING = 0.1;
    // How far under its ceiling the top grade's band ends, so a boiler there still has room to take heat in.
    private static final int CEILING_MARGIN = 10;

    private final HeatBuffer heat;
    private final FilteredFluidTank water;
    private final FilteredFluidTank steam;
    private BoilerPressure pressure = BoilerPressure.AUTO;
    private double pendingSteam;
    private double pendingHeat;
    private int boilingGrace;
    private int ventGrace;
    // The heat it held after its last tick, to tell what came in since (-1 until its first tick), the HU/t that has
    // been coming in (smoothed over the ticks heat came in), and the share of it it climbs on. The rate and share
    // survive reloads; lastStored starts fresh because it is only a baseline for measuring the next tick's input.
    private int lastStored = -1;
    private double heatIn;
    private double climbShare = CLIMB_SHARE;
    // What the last boil found (BOILING when it could boil, even if no heat was spare that tick), the heat per mB of
    // what it made, and whether it vented steam.
    private State lastState = State.HEATING;
    private double lastHeatPerMb;
    private boolean ventedSteam;
    // How much of the stored steam it has paid to lift to a higher grade so far (mB), and whether it lifted any this tick.
    private double lifted;
    private boolean lifting;
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

    // The hottest it runs on its pressure setting: the top of the set grade's band, or (on Auto) just under its ceiling.
    public int topCelsius() {
        return bandTop(pressure.grade() != null ? pressure.grade() : SteamGrade.SUPERHEATED);
    }

    // The top of a grade's band: just under the next grade's temperature, or just under the ceiling for the last.
    private int bandTop(SteamGrade grade) {
        SteamGrade[] grades = SteamGrade.values();
        return grade.ordinal() + 1 < grades.length ? grades[grade.ordinal() + 1].minCelsius() - 1 : heat.getCeiling() - CEILING_MARGIN;
    }

    // How far through a grade's band this temperature is: 0 at the grade's temperature, 1 at the band's top.
    public double bandPosition(SteamGrade grade, int celsius) {
        int top = bandTop(grade);
        return top <= grade.minCelsius() ? 1.0 : Mth.clamp((celsius - grade.minCelsius()) / (double) (top - grade.minCelsius()), 0.0, 1.0);
    }

    // One tick. costMultiplier scales the heat per mB (the array's big drum loses less), and hotSaving is the share of
    // that a boiler at the top of its grade's band saves.
    public State tick(int maxHeatPerTick, double costMultiplier, double hotSaving) {
        // Old saves (and a rebuilt hot drum) have no heat-input history. At equilibrium with their source they
        // cannot receive heat until boiling lowers their temperature. Start by drawing from their own stored
        // heat, still subject to the pressure floor, so they can resume without a pressure-setting change.
        if (lastStored < 0 && heatIn <= 0 && heat.getTemperature() >= SteamGrade.STEAM.minCelsius()) {
            heatIn = maxHeatPerTick;
        }
        int incoming = lastStored < 0 ? 0 : Math.max(0, heat.getStored() - lastStored);
        if (incoming > 0) {
            heatIn += (incoming - heatIn) * HEAT_IN_SMOOTHING;
            climbShare = Math.min(CLIMB_SHARE, climbShare + CLIMB_SHARE_GROWTH);
        }
        double boil = boil(incoming, maxHeatPerTick, costMultiplier, hotSaving);
        boolean vented = ventHeat(maxHeatPerTick) || ventedSteam;
        lastStored = heat.getStored();
        rate += (boil - rate) * RATE_SMOOTHING;
        heatUsed += (lastHeatPerMb * boil - heatUsed) * RATE_SMOOTHING;
        State state;
        if (boil > 0 || lifting) {
            boilingGrace = BOILING_GRACE_TICKS;
            state = State.BOILING;
        } else if (lastState != State.BOILING) {
            boilingGrace = 0;
            state = lastState;
        } else {
            state = boilingGrace-- > 0 ? State.BOILING : State.HEATING;
        }
        if (vented) {
            ventGrace = BOILING_GRACE_TICKS;
        } else if (ventGrace > 0) {
            ventGrace--;
        }
        return ventGrace > 0 && state != State.NO_WATER ? State.VENTING : state;
    }

    // Boils what it can this tick, returning the mB of steam made (kept or vented).
    private double boil(int incoming, int maxHeatPerTick, double costMultiplier, double hotSaving) {
        lastState = State.HEATING;
        ventedSteam = false;
        lifting = false;
        int temperature = heat.getTemperature();
        SteamGrade grade = SteamGrade.forTemperature(temperature);
        if (grade == null) {
            return 0;
        }
        // A set grade is made once the boiler is hot enough for it; until then, the grade it's hot enough for.
        SteamGrade set = pressure.grade();
        boolean atSet = set != null && grade.ordinal() >= set.ordinal();
        SteamGrade target = atSet ? set : grade;
        SteamGrade making = gradeToMake(target);
        if (water.getAmount() <= 0) {
            lastState = State.NO_WATER;
            return 0;
        }
        lastState = State.BOILING;

        int top = topCelsius();
        // At the top of its band it boils all the heat above it; under it, what came in less the share it climbs on.
        // Without heat this tick, what it holds at the rate heat had been coming in. It never boils itself below 100°C,
        // or below its set grade's temperature once it got there, so it's ready to make that grade when heat returns.
        double aboveTop = heat.getStored() - heat.minStoredAt(top) - pendingHeat;
        int floor = atSet ? set.minCelsius() : SteamGrade.STEAM.minCelsius();
        double aboveFloor = heat.getStored() - heat.minStoredAt(floor) - pendingHeat;
        double usable;
        if (incoming <= 0) {
            usable = Math.max(aboveTop, heatIn);
            climbShare /= 2;
        } else {
            usable = Math.max(aboveTop, temperature < top ? incoming * (1.0 - climbShare) : 0.0);
        }
        double budget = Math.max(0, Math.min(maxHeatPerTick, Math.min(usable, aboveFloor)));
        // Stored steam of a lower grade is lifted to the one it's hot enough for before any more is boiled.
        if (making != target) {
            double liftPerMb = (target.huPerMb() - making.huPerMb()) * costMultiplier;
            double spend = Math.min(budget, Math.max(0, steam.getAmount() - lifted) * liftPerMb);
            lifted += spend / liftPerMb;
            pendingHeat += spend;
            budget -= spend;
            lifting = spend > 0;
            if (lifted >= steam.getAmount()) {
                steam.set(0, target.resource(), steam.getAmount());
                making = target;
                lifted = 0;
            }
        } else {
            lifted = 0;
        }
        double huPerMb = making.huPerMb() * costMultiplier * (1.0 - hotSaving * bandPosition(making, temperature));
        lastHeatPerMb = huPerMb;
        double possible = budget / huPerMb;
        double boil = Math.max(0, Math.min(possible, water.getAmount() - pendingSteam));
        pendingSteam += boil;
        pendingHeat += boil * huPerMb;
        int mb = (int) Math.min(Math.floor(pendingSteam), water.getAmount());
        int hu = (int) Math.min(Math.floor(pendingHeat), heat.getStored());
        pendingSteam -= mb;
        pendingHeat -= hu;
        heat.remove(hu);
        if (mb > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                water.extract(0, water.getResource(0), mb, tx);
                // What doesn't fit in the tank is vented.
                ventedSteam = steam.insert(0, making.resource(), mb, tx) < mb;
                tx.commit();
            }
        }
        return boil;
    }

    // Vents heat that lifted it past the top of its band, up to max HU. Returns whether it vented any.
    private boolean ventHeat(int max) {
        int top = topCelsius();
        if (heat.getTemperature() <= top) {
            return false;
        }
        int excess = heat.getStored() - (heat.minStoredAt(top + 1) - 1);
        return heat.bleed(Math.min(excess, max)) > 0;
    }

    // Drops the stored steam to a lower grade and returns the grade to make this tick: the stored one while it's
    // still being lifted to a higher grade.
    private SteamGrade gradeToMake(SteamGrade grade) {
        lifted = Math.min(lifted, steam.getAmount());
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
        output.putDouble("lifted", lifted);
        output.putInt("pressure", pressure.ordinal());
        output.putDouble("heat_in", heatIn);
        output.putDouble("climb_share", climbShare);
    }

    public void deserialize(ValueInput input) {
        pendingSteam = input.getDoubleOr("pending_steam", 0.0);
        pendingHeat = input.getDoubleOr("pending_heat", 0.0);
        lifted = input.getDoubleOr("lifted", 0.0);
        pressure = BoilerPressure.byId(input.getIntOr("pressure", 0));
        heatIn = Math.max(0, input.getDoubleOr("heat_in", 0.0));
        climbShare = Mth.clamp(input.getDoubleOr("climb_share", CLIMB_SHARE), 0.0, CLIMB_SHARE);
        lastStored = -1;
    }
}
