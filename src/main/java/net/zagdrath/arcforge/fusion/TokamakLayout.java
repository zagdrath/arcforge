/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.fusion;

// The shape of a Fusion Reactor (a tokamak, or a stellarator) of major radius r (3 to 7 blocks): what goes in each
// cell, by its distance from the axis column d (the horizontal distance between block centres, rounded) and its height y
// from the midplane. Pure geometry, no Minecraft, so it's shared by the structure check, the blueprint, the renderer and
// the tests. Compact: the blanket is the chamber's outer wall (its plasma-facing side armoured, as real blanket modules
// are), the toroidal field coils close the ring over and under (their outer legs run up inside the cryostat's wall), and
// the poloidal field coils are rings set into those two layers. From the axis out, and from the midplane up and down:
//  - the Central Solenoid: the axis column d = 0, the full height between the coil layers (the toroidal coils' inner legs
//    run up inside it); in a stellarator, Stellarator Coil;
//  - the plasma chamber: air, 2 <= d <= r - 1, |y| <= hp (hp, the plasma's half-height: (r - 1) / 2);
//  - the First Wall: the chamber's inner wall (d = 1) and its roof (y = hp + 1); its floor is the Divertor
//    (y = -(hp + 1)), both across 1 <= d <= r - 1;
//  - the Breeding Blanket: the chamber's outer wall (d = r) for |y| <= hp + 1, and its roof and floor (y = ±(hp + 2))
//    across 1 <= d <= r;
//  - the Toroidal Field Coils: the top and bottom layers (y = ±(hp + 3)) across d <= r, with the Poloidal Field Coils set
//    into them as rings at d = 1 and d = r; in a stellarator, Stellarator Coil (the poloidal rings stay);
//  - the Cryostat: its wall d = r + 1 (the controller, Vessel Ports and Reactor Glass go in it), the full height.
// So a reactor is 2r + 3 blocks across (9 at r = 3, 17 at r = 7) and 2hp + 7 tall (9 to 13). Its plasma is sized for the
// physics as the first, larger layout's was (majorRadiusMetres), so each size makes the power it did.
public final class TokamakLayout {
    public static final int MIN_RADIUS = 3, MAX_RADIUS = 7;

    public enum Part {
        // Outside the reactor (anything may be there). CRYOSTAT_LID is no longer laid out (the coil layers close the ring),
        // kept so saved parts and switches over the parts stay valid.
        OUTSIDE,
        AIR,
        CENTRAL_SOLENOID,
        FIRST_WALL,
        DIVERTOR,
        BLANKET,
        TOROIDAL_COIL,
        POLOIDAL_COIL,
        CRYOSTAT_WALL,
        CRYOSTAT_LID
    }

    public final int r;
    public final int hp;

    public TokamakLayout(int majorRadius) {
        this.r = majorRadius;
        this.hp = (majorRadius - 1) / 2;
    }

    // The cryostat wall's distance (the reactor's outer radius).
    public int outer() {
        return r + 1;
    }

    // The lids' height.
    public int top() {
        return hp + 3;
    }

    // Blocks across and tall.
    public int width() {
        return 2 * outer() + 1;
    }

    public int height() {
        return 2 * top() + 1;
    }

    // d for a cell dx, dz from the axis.
    public static int distance(int dx, int dz) {
        return (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
    }

    public Part at(int dx, int y, int dz) {
        return at(distance(dx, dz), y);
    }

    public Part at(int d, int y) {
        int ay = Math.abs(y);
        int ro = outer();
        if (d > ro || ay > top()) {
            return Part.OUTSIDE;
        }
        if (d == ro) {
            return Part.CRYOSTAT_WALL;
        }
        if (ay == top()) {
            return d == 1 || d == r ? Part.POLOIDAL_COIL : Part.TOROIDAL_COIL;
        }
        if (d == 0) {
            return Part.CENTRAL_SOLENOID;
        }
        if (d == r || ay == hp + 2) {
            return Part.BLANKET;
        }
        if (y == -(hp + 1)) {
            return Part.DIVERTOR;
        }
        if (d == 1 || y == hp + 1) {
            return Part.FIRST_WALL;
        }
        return Part.AIR;
    }

    // How many cells of a part the reactor holds.
    public int count(Part part) {
        int ro = outer(), count = 0;
        for (int dx = -ro; dx <= ro; dx++) {
            for (int dz = -ro; dz <= ro; dz++) {
                for (int y = -top(); y <= top(); y++) {
                    if (at(dx, y, dz) == part) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // --- The plasma's size, for the physics ---

    // The chamber's inner and outer walls' distances from the axis, and the plasma's half-height in blocks.
    public static int chamberInner() {
        return 2;
    }

    public int chamberOuter() {
        return r - 1;
    }

    // Major radius in metres, for the physics: the plasma inside is smaller than its chamber, and the compact layout's
    // sizes 3 to 7 stand for 2.48 m to 6.2 m (as major radii 4 to 10 did before it), so each size keeps its power.
    public double majorRadiusMetres() {
        return 0.62 * (4.0 + 1.5 * (r - MIN_RADIUS));
    }
}
