/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.reactor;

// The Fission Reactor's core as a grid of cells (one per block inside the vessel), and the neutron diffusion over it:
// one energy group, a finite difference on the block grid, solved for the fundamental mode by power iteration. It gives
// the core's k-effective and each cell's flux and fission rate. No Minecraft classes, so it can be run on its own.
//
// What each cell does (homogenised per block; the numbers are game-scaled so neutrons travel a few blocks, see
// ReactorParams):
//  - Moderation: a fuel cell's fission depends on how well slowed its neutrons are, from the moderating power of its
//    own channel water and its six neighbours (water, heavy water, graphite; steel and void don't moderate). Heavy
//    water and graphite moderate almost as well as light water and absorb far less.
//  - Absorption: U-238 resonance capture in the fuel (more when the fuel is hot: Doppler, and when it's poorly
//    moderated), the fissile fuel's own capture, fission products and xenon, control rods (by how far each is
//    inserted, from the top), water (light water a little, heavy water hardly), graphite hardly, steel some.
//  - Boiling or uncovering takes water out of a cell (its void fraction): less moderation and less absorption.
//  - Reflectors: graphite and steel cells at the edge send neutrons back (by their diffusion and absorption); the
//    vessel's walls are a vacuum boundary.
public final class CoreLattice {
    public enum Cell {
        // Coolant only (empty, or an empty channel).
        COOLANT,
        // A Fuel Channel with a Fuel Rod in it.
        FUEL,
        // A Control Rod Channel with a Control Rod in it (its bank decides how far it's in).
        CONTROL,
        GRAPHITE,
        STEEL
    }

    public enum Coolant { LIGHT_WATER, HEAVY_WATER }

    public final int nx, ny, nz, size;
    final Cell[] cells;
    final int[] bank;
    // What's in each cell that the owner keeps up to date (index = cell).
    final double[] fissile;      // U-235 (+ fissile plutonium) fraction in a fuel cell
    final double[] poison;       // fission-product absorption (Σa) in a fuel cell
    final double[] xenon;        // Xe-135, as a fraction of its full-power equilibrium
    final double[] voids;        // 0 full of water .. 1 no water (boiling or uncovered)
    // The result of the last solve.
    final double[] flux;
    final double[] fission;      // ν Σf φ: where the power comes from
    private final double[] work;

    public CoreLattice(int nx, int ny, int nz) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.size = nx * ny * nz;
        cells = new Cell[size];
        java.util.Arrays.fill(cells, Cell.COOLANT);
        bank = new int[size];
        fissile = new double[size];
        poison = new double[size];
        xenon = new double[size];
        voids = new double[size];
        flux = new double[size];
        fission = new double[size];
        work = new double[size * 4];
        java.util.Arrays.fill(flux, 1.0);
    }

    public int index(int x, int y, int z) {
        return (y * nz + z) * nx + x;
    }

    public int x(int i) {
        return i % nx;
    }

    public int y(int i) {
        return i / (nx * nz);
    }

    public int z(int i) {
        return (i / nx) % nz;
    }

    public void set(int i, Cell cell, int bankIndex) {
        cells[i] = cell;
        bank[i] = bankIndex;
    }

    public Cell cell(int i) {
        return cells[i];
    }

    public int bank(int i) {
        return bank[i];
    }

    public void setFuel(int i, double fissileFraction, double poisonAbsorption) {
        fissile[i] = fissileFraction;
        poison[i] = poisonAbsorption;
    }

    public void setXenon(int i, double value) {
        xenon[i] = value;
    }

    public double xenon(int i) {
        return xenon[i];
    }

    public void setVoid(int i, double value) {
        voids[i] = Math.max(0.0, Math.min(1.0, value));
    }

    public double voidAt(int i) {
        return voids[i];
    }

    public double flux(int i) {
        return flux[i];
    }

    public double fission(int i) {
        return fission[i];
    }

    // The state the cross sections depend on besides the cells: the coolant, its density (1 cold, less hot), the
    // fuel's temperature (°C), and each bank's insertion (0 out .. 1 in).
    public record Conditions(Coolant coolant, double waterDensity, double fuelCelsius, double[] insertion) {}

    // --- Cross sections ---

    // How much of a control rod's absorption cell i has: banks go in from the top. A cell the rod tip is part way into
    // is flux-weighted (the flux is lower round the rod), not volume-weighted, so its first few percent don't take most
    // of the cell's worth: the bank's worth then grows smoothly instead of jumping each time its tip enters a cell.
    double rodCover(int i, double[] insertion, ReactorParams p) {
        int b = bank[i];
        double in = b >= 0 && b < insertion.length ? insertion[b] : 0.0;
        double depth = in * ny;
        double fromTop = ny - 1 - y(i);
        double f = Math.max(0.0, Math.min(1.0, depth - fromTop));
        return f * p.rodFluxRatio / (f * p.rodFluxRatio + 1.0 - f);
    }

    // Water in a cell (0..1 of a full water cell): a channel holds a little round its rod, which moderates less than
    // its share (it's a thin annulus) but absorbs its share.
    private double water(int i, Conditions c, ReactorParams p, boolean moderating) {
        double wet = (1.0 - voids[i]) * c.waterDensity();
        return switch (cells[i]) {
            case COOLANT -> wet;
            case FUEL, CONTROL -> wet * (moderating ? p.channelModeration : p.channelWater);
            default -> 0.0;
        };
    }

    // Moderating power a cell gives itself or a neighbour.
    private double moderating(int i, Conditions c, ReactorParams p) {
        double water = water(i, c, p, true) * (c.coolant() == Coolant.HEAVY_WATER ? p.heavyWaterModeration : 1.0);
        return switch (cells[i]) {
            case GRAPHITE -> p.graphiteModeration;
            default -> water;
        };
    }

    // The thermal fraction of a fuel cell's neutrons, from its own water and its neighbours' moderating power.
    double thermal(int i, Conditions c, ReactorParams p) {
        double m = moderating(i, c, p);
        int x = x(i), y = y(i), z = z(i);
        if (x > 0) m += moderating(i - 1, c, p);
        if (x < nx - 1) m += moderating(i + 1, c, p);
        if (z > 0) m += moderating(i - nx, c, p);
        if (z < nz - 1) m += moderating(i + nx, c, p);
        if (y > 0) m += moderating(i - nx * nz, c, p);
        if (y < ny - 1) m += moderating(i + nx * nz, c, p);
        return m / (m + p.moderationHalf);
    }

    private double doppler(Conditions c, ReactorParams p) {
        return 1.0 + p.doppler * (Math.sqrt(c.fuelCelsius() + 273.0) - Math.sqrt(293.0));
    }

    double diffusion(int i, Conditions c, ReactorParams p) {
        return switch (cells[i]) {
            case GRAPHITE -> p.graphiteDiffusion;
            case STEEL -> p.steelDiffusion;
            case COOLANT -> p.diffusion * (1.0 + p.voidStreaming * voids[i]);
            default -> p.diffusion;
        };
    }

    double absorption(int i, Conditions c, ReactorParams p) {
        double waterAbs = water(i, c, p, false) * (c.coolant() == Coolant.HEAVY_WATER ? p.heavyWaterAbsorption : p.lightWaterAbsorption);
        return switch (cells[i]) {
            case COOLANT -> waterAbs;
            case GRAPHITE -> p.graphiteAbsorption;
            case STEEL -> p.steelAbsorption;
            case CONTROL -> waterAbs + p.controlAbsorption * rodCover(i, c.insertion(), p);
            case FUEL -> {
                double th = thermal(i, c, p);
                double capture = p.u238Capture * (p.captureBase + p.resonance * (1.0 - th)) * doppler(c, p);
                double fissileAbs = p.fissionCross * fissile[i] * (th + p.fastFission * (1.0 - th)) * (1.0 + p.captureToFission);
                yield waterAbs + capture + fissileAbs + poison[i] + p.xenonWorth * xenon[i];
            }
        };
    }

    double production(int i, Conditions c, ReactorParams p) {
        if (cells[i] != Cell.FUEL) {
            return 0.0;
        }
        double th = thermal(i, c, p);
        return p.nu * p.fissionCross * fissile[i] * (th + p.fastFission * (1.0 - th));
    }

    // --- The solve ---

    // Solves for the fundamental mode; returns k-effective (0 if there's no fuel). flux and fission hold the shape.
    public double solve(Conditions c, ReactorParams p) {
        double[] d = new double[size], a = new double[size], nf = new double[size];
        boolean any = false;
        for (int i = 0; i < size; i++) {
            d[i] = diffusion(i, c, p);
            a[i] = absorption(i, c, p);
            nf[i] = production(i, c, p);
            any |= nf[i] > 0.0;
        }
        if (!any) {
            java.util.Arrays.fill(flux, 0.0);
            java.util.Arrays.fill(fission, 0.0);
            return 0.0;
        }
        // Coupling to each neighbour (harmonic mean of D, one block apart), and leakage out through the walls
        // (a vacuum boundary half a block out, with the extrapolation distance 2.13 D).
        double[] diag = work;
        int[] stride = { 1, nx, nx * nz };
        for (int i = 0; i < size; i++) {
            double sum = a[i];
            int[] at = { x(i), z(i), y(i) };
            int[] max = { nx, nz, ny };
            for (int axis = 0; axis < 3; axis++) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    int n = at[axis] + dir;
                    if (n < 0 || n >= max[axis]) {
                        sum += p.wallLeakage * d[i] / (0.5 + 2.13 * d[i]);
                    } else {
                        int j = i + dir * stride[axis];
                        sum += 2.0 * d[i] * d[j] / (d[i] + d[j]);
                    }
                }
            }
            diag[i] = sum;
        }
        double k = Math.max(0.3, lastK);
        double[] phi = flux;
        for (int i = 0; i < size; i++) {
            if (!(phi[i] > 0.0) || Double.isInfinite(phi[i])) {
                phi[i] = 1.0;
            }
        }
        double source = 0.0;
        for (int i = 0; i < size; i++) {
            source += nf[i] * phi[i];
        }
        for (int outer = 0; outer < p.outerIterations; outer++) {
            double[] q = fission;
            for (int i = 0; i < size; i++) {
                q[i] = nf[i] * phi[i] / k;
            }
            for (int inner = 0; inner < p.innerIterations; inner++) {
                for (int i = 0; i < size; i++) {
                    double s = q[i];
                    int x = x(i), y = y(i), z = z(i);
                    if (x > 0) s += couple(d, i, i - 1) * phi[i - 1];
                    if (x < nx - 1) s += couple(d, i, i + 1) * phi[i + 1];
                    if (z > 0) s += couple(d, i, i - nx) * phi[i - nx];
                    if (z < nz - 1) s += couple(d, i, i + nx) * phi[i + nx];
                    if (y > 0) s += couple(d, i, i - nx * nz) * phi[i - nx * nz];
                    if (y < ny - 1) s += couple(d, i, i + nx * nz) * phi[i + nx * nz];
                    phi[i] = s / diag[i];
                }
            }
            double next = 0.0;
            for (int i = 0; i < size; i++) {
                next += nf[i] * phi[i];
            }
            double kNext = source > 0.0 ? k * next / source : k;
            // Keep the flux at a sensible scale.
            double norm = next > 0.0 ? 1.0 / next * size : 1.0;
            for (int i = 0; i < size; i++) {
                phi[i] *= norm;
            }
            source = next * norm;
            boolean done = Math.abs(kNext - k) < p.tolerance;
            k = kNext;
            if (done && outer > 2) {
                break;
            }
        }
        for (int i = 0; i < size; i++) {
            fission[i] = nf[i] * phi[i];
        }
        lastK = k;
        return k;
    }

    private double lastK = 1.0;

    private static double couple(double[] d, int i, int j) {
        return 2.0 * d[i] * d[j] / (d[i] + d[j]);
    }
}
