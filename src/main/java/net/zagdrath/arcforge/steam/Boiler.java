/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

// A block entity that boils with a BoilerCore: the Steam Boiler and the Steam Boiler Array's master.
public interface Boiler {
    BoilerCore getCore();

    // Sets the pressure (see BoilerPressure) and saves it.
    void setPressure(BoilerPressure pressure);
}
