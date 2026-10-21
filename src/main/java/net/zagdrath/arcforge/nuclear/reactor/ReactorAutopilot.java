/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.reactor;

import java.util.Arrays;

// The Reactor Autopilot's control law (the upgrade card's; see FissionReactorBlockEntity.autopilot for when it runs). It
// drives every bank together to hold the core at the most power the plant takes. Its goal climbs to full power, and backs
// off below the power whenever the hottest fuel or cladding, the pressure, the coolant flow or the vessel's level comes
// near its trip, so it settles at the most the core, its coolant loop and whatever takes its heat can keep up. The banks
// follow the reactivity it wants (positive while the power is short of the goal, negative over it), so they find the
// core's critical position as the fuel heats, the xenon builds and the fuel burns. No Minecraft classes, so it can be run
// on its own.
public final class ReactorAutopilot {
    // What it's doing: off (no card, or the reactor switched off or stopped by redstone), holding the rods in for rods to
    // load or come out, raising power, holding it, all rods out and still short of its goal, backing off from a limit
    // (hot fuel, pressure, coolant flow or level), or tripped (it never resets a trip).
    public enum State { OFF, REFUELLING, RAISING, HOLDING, RODS_OUT, FUEL_HOT, PRESSURE, FLOW, LEVEL, TRIPPED }

    // The goal rises this much a tick (0.4% of full power a second) while nothing is near a limit, and falls this much a
    // tick below the power while something is; never above full power or below MIN_GOAL.
    static final double RAISE = 0.0002, BACK_OFF = 0.001;
    public static final double MIN_GOAL = 0.05;
    // How near each trip it backs off: the fuel and cladding (°C), the pressure (bar) and the vessel's level. The coolant
    // flow it doesn't wait for: once less than FLOW_SHORT of the heat the core makes is leaving, the plant can only take
    // that share, and the goal drops straight to it.
    static final double FUEL_MARGIN = 100.0, PRESSURE_MARGIN = 3.0, FLOW_SHORT = 0.97, LEVEL = 0.65;
    // The reactivity it asks for: RHO for each e-fold the power is short of (or over) its goal, at most MAX_RHO either way.
    // That most gives about a 40 s period with the default delayed neutrons, well clear of the short-period trip, so a cold
    // core climbs from its source to full power in about ten minutes.
    static final double RHO = 0.0025, MAX_RHO = 0.0033;
    // How far the banks are sent for each unit of reactivity it's off by (they still move at their own speed).
    static final double ROD_GAIN = 0.5;
    // The period meter, watched as an operator does: while it reads under PERIOD_HOLD seconds the banks come out no further,
    // and under PERIOD_INSERT they go back in (by INSERT_STEP), well before the short-period trip. Coming out to critical
    // from a core that still has power and delayed neutrons in it (after a trip, say), the power climbs steeply as the
    // last of the negative reactivity goes, and pulling the banks at full speed would trip it.
    static final double PERIOD_HOLD = 30.0, PERIOD_INSERT = 20.0, INSERT_STEP = 0.01;

    public double goal = 1.0;
    public State state = State.OFF;

    // One tick of steering the banks (before the core's own tick), when it's in charge.
    public void steer(ReactorSim core) {
        if (core.scrammed) {
            state = State.TRIPPED;
            return;
        }
        ReactorSim.Trips trips = core.trips;
        double fuelLimit = trips.highFuelTemperature ? trips.highFuelCelsius : core.p.fuelMelt;
        double pressureLimit = trips.highPressure ? core.operatingPressure() + trips.highPressureMargin : core.reliefPressure();
        State limit = null;
        if (core.hottestFuel() > fuelLimit - FUEL_MARGIN || core.hottestCladding() > core.p.cladFail - FUEL_MARGIN) {
            limit = State.FUEL_HOT;
        } else if (core.pressure > pressureLimit - PRESSURE_MARGIN) {
            limit = State.PRESSURE;
        } else if (core.power > 0.05 && core.flowRatio < FLOW_SHORT) {
            limit = State.FLOW;
        } else if (core.coolantType.isWater() && core.level() < LEVEL) {
            limit = State.LEVEL;
        }
        if (limit == State.FLOW) {
            goal = Math.max(MIN_GOAL, Math.min(goal, core.power * core.flowRatio));
        } else if (limit != null) {
            goal = Math.max(MIN_GOAL, Math.min(goal, core.power) - BACK_OFF);
        } else {
            goal = Math.min(1.0, goal + RAISE);
        }
        double ratio = Math.max(core.power, 1.0E-9) / goal;
        double want = Math.max(-MAX_RHO, Math.min(MAX_RHO, -Math.log(ratio) * RHO));
        double in = Math.max(0.0, Math.min(1.0, core.insertion[0] - (want - core.rho) * ROD_GAIN));
        if (core.period > 0.0 && core.period < PERIOD_INSERT) {
            in = Math.min(1.0, Math.max(in, core.insertion[0] + INSERT_STEP));
        } else if (core.period > 0.0 && core.period < PERIOD_HOLD) {
            in = Math.max(in, core.insertion[0]);
        }
        Arrays.fill(core.target, in);
        state = limit != null ? limit : ratio > 0.98 ? State.HOLDING : in <= 0.0 ? State.RODS_OUT : State.RAISING;
    }

    // Holds every bank in (rods waiting to load, which they only do shut down, or queued to come out).
    public void holdIn(ReactorSim core) {
        Arrays.fill(core.target, 1.0);
        state = core.scrammed ? State.TRIPPED : State.REFUELLING;
    }
}
