/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.fusion;

// Thermal fusion reactivities <σv> (cm³/s) at an ion temperature in keV, from the Bosch-Hale fits (Nuclear Fusion 32,
// 611, 1992): D-T, D-He3 and the two D-D branches. Valid from about 0.2 keV to 100 keV (D-He3 to 190); clamped there.
public final class Reactivity {
    private Reactivity() {}

    // Energy per reaction (MeV), and the share of it the neutron carries.
    public static final double DT_ENERGY = 17.59, DT_NEUTRON_SHARE = 14.07 / 17.59;
    public static final double DHE3_ENERGY = 18.35;
    public static final double DD_T_ENERGY = 4.03;
    public static final double DD_HE3_ENERGY = 3.27, DD_HE3_NEUTRON_SHARE = 2.45 / 3.27;

    public static double dt(double keV) {
        return fit(keV, 34.3827, 1_124_656, 1.17302e-9, 1.51361e-2, 7.51886e-2, 4.60643e-3, 1.35e-2, -1.0675e-4, 1.366e-5, 100.0);
    }

    public static double dhe3(double keV) {
        return fit(keV, 68.7508, 1_124_572, 5.51036e-10, 6.41918e-3, -2.02896e-3, -1.91080e-5, 1.35776e-4, 0.0, 0.0, 190.0);
    }

    // D(d,p)T.
    public static double ddT(double keV) {
        return fit(keV, 31.3970, 937_814, 5.65718e-12, 3.41267e-3, 1.99167e-3, 0.0, 1.05060e-5, 0.0, 0.0, 100.0);
    }

    // D(d,n)He3.
    public static double ddHe3(double keV) {
        return fit(keV, 31.3970, 937_814, 5.43360e-12, 5.85778e-3, 7.68222e-3, 0.0, -2.964e-6, 0.0, 0.0, 100.0);
    }

    private static double fit(double keV, double bg, double mrc2, double c1, double c2, double c3, double c4, double c5, double c6,
            double c7, double max) {
        if (keV < 0.2) {
            return 0.0;
        }
        double t = Math.min(keV, max);
        double theta = t / (1.0 - t * (c2 + t * (c4 + t * c6)) / (1.0 + t * (c3 + t * (c5 + t * c7))));
        double xi = Math.cbrt(bg * bg / (4.0 * theta));
        return c1 * theta * Math.sqrt(xi / (mrc2 * t * t * t)) * Math.exp(-3.0 * xi);
    }
}
