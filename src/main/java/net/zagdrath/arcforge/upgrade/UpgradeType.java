/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.upgrade;

// Machine upgrades. Up to MAX_PER_MACHINE of each type fit in a machine, all in one upgrade slot.
//  - Speed: the machine works 2^(n/2) times as fast (x16 at 8), using energy or fuel just as fast, so
//    the cost per operation (or the output per unit of fuel) doesn't change.
//  - Energy: FE consumers use 0.8^n of the FE per operation; the Combustion Plant makes 1 + n/8 times
//    the FE per unit of fuel.
//  - Heat: heat producers make 1 + n/8 times the heat per unit of fuel or lava (and from nearby lava and
//    magma); the Thermoelectric Plant converts heat as if its efficiency were divided by 0.8^n (up to 100%).
public enum UpgradeType {
    SPEED("speed"),
    ENERGY("energy"),
    HEAT("heat");

    public static final int MAX_PER_MACHINE = 8;

    private final String name;

    UpgradeType(String name) {
        this.name = name;
    }

    public String getSerializedName() {
        return name;
    }

    public static double speedMultiplier(int speed) {
        return Math.pow(2.0, speed / 2.0);
    }

    // Share of the base FE cost per operation an FE consumer still pays.
    public static double energyCostMultiplier(int energy) {
        return Math.pow(0.8, energy);
    }

    // Output per unit of fuel for the Combustion Plant (Energy) or heat producers (Heat).
    public static double outputMultiplier(int count) {
        return 1.0 + 0.125 * count;
    }

    // Processing time after Speed upgrades, never under a tick.
    public static int time(int baseTime, int speed) {
        return Math.max(1, (int) Math.round(baseTime / speedMultiplier(speed)));
    }
}
