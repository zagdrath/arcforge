/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import java.util.Locale;

// Dose rates and doses as text: "0.042 mSv/h", "3.5 mSv/h", "120 mSv/h"; "0.4 mSv", "1,250 mSv".
public final class RadiationText {
    private RadiationText() {}

    public static String rate(double mSvPerHour) {
        return number(mSvPerHour) + " mSv/h";
    }

    public static String dose(double mSv) {
        return (mSv < 10.0 ? String.format(Locale.ROOT, "%.1f", mSv) : String.format(Locale.ROOT, "%,d", Math.round(mSv))) + " mSv";
    }

    private static String number(double value) {
        if (value <= 0.0) {
            return "0";
        }
        if (value < 0.001) {
            return "<0.001";
        }
        if (value < 1.0) {
            return String.format(Locale.ROOT, "%.3f", value);
        }
        if (value < 10.0) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        return value < 100.0 ? String.format(Locale.ROOT, "%.1f", value) : String.format(Locale.ROOT, "%,d", Math.round(value));
    }
}
