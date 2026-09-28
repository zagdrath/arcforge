/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.storage;

import java.util.Locale;

// Item counts as Vaults show them: "4,096", "12.4k", "262k", "1.2M". Always rounded down, so a vault never
// looks fuller than it is.
public final class StorageCounts {
    private StorageCounts() {}

    public static String compact(long count) {
        if (count < 10_000) {
            return grouped(count);
        }
        if (count < 100_000) {
            return oneDecimal(count / 100 / 10.0) + "k";
        }
        if (count < 1_000_000) {
            return (count / 1_000) + "k";
        }
        return oneDecimal(count / 100_000 / 10.0) + "M";
    }

    public static String grouped(long count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    // One decimal place, dropping a trailing ".0" ("12k" rather than "12.0k").
    private static String oneDecimal(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
