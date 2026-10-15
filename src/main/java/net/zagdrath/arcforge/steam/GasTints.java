/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.steam;

// The colours Arcforge's gases are drawn in (the steam grades' are on SteamGrade). The client tints the shared gas
// texture with them, and the gas API gives them out through the arcforge:gas_properties data map, which a GameTest
// keeps in step with these.
public final class GasTints {
    // Exhaust Steam: duller and greyer than live steam.
    public static final int EXHAUST_STEAM = 0xFF9EA6AE;
    public static final int HYDROGEN = 0xFFEAF2FA;
    public static final int OXYGEN = 0xFF9FD4F2;
    // Carbon Dioxide: a cool grey, darker than steam.
    public static final int CARBON_DIOXIDE = 0xFFB4BCC4;
    // Farm chemistry: Nitrogen a cold lilac, Ammonia a pale sharp yellow-green, Biogas a murky olive.
    public static final int NITROGEN = 0xFFC4C0EC;
    public static final int AMMONIA = 0xFFE2F0A0;
    public static final int BIOGAS = 0xFFA8B478;
    // Chlorine: its own sickly greenish yellow, deeper and greener than Ammonia.
    public static final int CHLORINE = 0xFFBCD24A;
    // Ethylene: colourless, so a faint cool mint, paler than Hydrogen is white and greener than Oxygen.
    public static final int ETHYLENE = 0xFFD2EADA;
    // Syngas: colourless too, so a warm pale tan, set apart from the cool gases and Biogas's olive.
    public static final int SYNGAS = 0xFFE2CCA4;
    // Refinery Gas: a faint dusty blue-grey, cooler than Syngas and warmer than Carbon Dioxide.
    public static final int REFINERY_GAS = 0xFFC2CCDA;
    // Natural Gas: clear, so a clean pale blue, bluer and lighter than Refinery Gas. Hydrogen Sulfide: a sickly
    // yellow-green, yellower than Chlorine and duller than Ammonia.
    public static final int NATURAL_GAS = 0xFFC8E4F6;
    // A pale lavender.
    public static final int PROPYLENE = 0xFFD8CCEC;
    public static final int HYDROGEN_SULFIDE = 0xFFC4CC6A;

    private GasTints() {}
}
