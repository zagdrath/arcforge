/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.fusion;

import net.minecraft.network.chat.Component;

// A Fusion Reactor's warnings, as bits (append new ones at the end): each lit on its GUI and listed by Jade; those that
// sound the klaxon do until silenced. A disruption, a quench and a tritium release are latched until silenced.
public enum FusionAlarm {
    DENSITY(true),
    BETA(true),
    VERTICAL(true),
    PULSE(false),
    VACUUM(true),
    COOLANT(true),
    MAGNET(true),
    WEAR(false),
    DISRUPTION(true),
    QUENCH(true),
    TRITIUM(true);

    public final int bit;
    private final boolean klaxon;

    FusionAlarm(boolean klaxon) {
        this.bit = 1 << ordinal();
        this.klaxon = klaxon;
    }

    public static int klaxonMask() {
        int mask = 0;
        for (FusionAlarm alarm : values()) {
            if (alarm.klaxon) {
                mask |= alarm.bit;
            }
        }
        return mask;
    }

    public Component description() {
        return Component.translatable("gui.arcforge.fusion_reactor.alarm." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
