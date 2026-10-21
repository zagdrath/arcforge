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

    // The goal rises this much a tick (0.4% of full power a second) while nothing is near a limit, and while something is
    // it stays BACK_OFF (a share) under the power, so the power comes down only as far as it must, at the rate the core
    // can follow; never above full power or below MIN_GOAL.
    static final double RAISE = 0.0002, BACK_OFF = 0.02;
    public static final double MIN_GOAL = 0.05;
    // How near each trip it backs off: the fuel and cladding (°C), the pressure (bar) and the vessel's level. The coolant
    // flow it doesn't wait for: once less than FLOW_SHORT of the heat the core makes is leaving, the plant can only take
    // that share, and the goal drops straight to it.
    static final double FUEL_MARGIN = 100.0, PRESSURE_MARGIN = 3.0, FLOW_SHORT = 0.97, LEVEL = 0.75;
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

    // The plant's side, seen from the reactor's own tanks. A loop with buffers (the intake, the hot leg, the Steam
    // Generator's tanks) can fall behind without the core's flow falling short at all: a generator short of feedwater
    // still takes all the Hot Coolant it's sent into its own tank, but sends back only what it cools, so the intake and
    // then the vessel drain, and the core trips on low coolant. So it also watches the coolant coming back into the
    // intake and what the plant takes from the hot leg (or steam) tank, against what the core sends out, each smoothed by
    // PLANT_SMOOTHING a tick. Once a buffer is half used (the intake half empty, or the output tank half full) the plant
    // is plainly not keeping up, and the share it does keep up with is the most it takes (plantShare, 1 while it keeps up).
    static final double PLANT_SMOOTHING = 0.05, BUFFER_USED = 0.5, PLANT_FLOW_MIN = 50.0;
    // Once the plant is behind, the goal goes PLANT_MARGIN under what it keeps up with, so the buffers it filled drain back
    // (into the vessel) instead of staying full.
    static final double PLANT_MARGIN = 0.1;
    private double lastIntake = -1.0, lastOutput = -1.0, back, taken, sent;
    public double plantShare = 1.0;
    // The goal the plant last fell behind at: from just under it to CEILING_BAND over it the goal creeps up only
    // CEILING_CREEP as fast, so a plant that can't keep up is found again gently rather than overrun each time its buffers
    // have refilled; past the band (the plant has been made bigger, say) it climbs at the full rate again.
    static final double CEILING_CREEP = 0.1, CEILING_BAND = 1.1;
    public double ceiling = 1.0;

    public double goal = 1.0;
    public State state = State.OFF;

    // The reactor's intake and output tanks as they stand at the start of its tick (before the autopilot steers), in mB.
    public void watch(double intake, double intakeCapacity, double output, double outputCapacity) {
        if (lastIntake >= 0.0) {
            back += (Math.max(0.0, intake - lastIntake) - back) * PLANT_SMOOTHING;
            taken += (Math.max(0.0, lastOutput - output) - taken) * PLANT_SMOOTHING;
        }
        double share = 1.0;
        if (sent > PLANT_FLOW_MIN) {
            if (intake < BUFFER_USED * intakeCapacity) {
                share = Math.min(share, back / sent);
            }
            if (output > BUFFER_USED * outputCapacity) {
                share = Math.min(share, taken / sent);
            }
        }
        plantShare = Math.max(0.0, Math.min(1.0, share));
    }

    // The same tanks once the core has taken its coolant and sent out its tick's Hot Coolant or steam (out, mB), before
    // anything takes it away.
    public void sent(double intake, double output, double out) {
        lastIntake = intake;
        lastOutput = output;
        sent += (out - sent) * PLANT_SMOOTHING;
    }

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
        } else if (core.power > 0.05 && Math.min(core.flowRatio, plantShare) < FLOW_SHORT) {
            limit = State.FLOW;
        } else if (core.coolantType.isWater() && core.level() < LEVEL) {
            limit = State.LEVEL;
        }
        if (limit == State.FLOW) {
            double share = core.flowRatio < plantShare ? core.flowRatio : plantShare * (1.0 - PLANT_MARGIN);
            goal = Math.max(MIN_GOAL, Math.min(goal, core.power * share));
            ceiling = goal;
        } else if (limit != null) {
            goal = Math.max(MIN_GOAL, Math.min(goal, core.power * (1.0 - BACK_OFF)));
        } else {
            boolean nearCeiling = goal >= ceiling * 0.98 && goal < ceiling * CEILING_BAND;
            goal = Math.min(1.0, goal + (nearCeiling ? RAISE * CEILING_CREEP : RAISE));
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
