/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.settings;

/**
 * Upgrades of one type installed in a machine.
 *
 * @param type  the upgrade type's id: {@code speed}, {@code energy}, {@code heat}, {@code insulation},
 *              {@code thermoelectric} or {@code range}
 * @param count how many are installed, from 1 to 8
 */
public record InstalledUpgrade(String type, int count) {}
