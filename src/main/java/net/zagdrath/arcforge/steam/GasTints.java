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
    // The nuclear gases: Fluorine pale yellow, Uranium Hexafluoride (colourless in fact, white as the crystals it freezes
    // to) a faint grey-green white, its depleted tails a duller grey.
    public static final int FLUORINE = 0xFFE6E49A;
    public static final int URANIUM_HEXAFLUORIDE = 0xFFDCE0D0;
    public static final int DEPLETED_URANIUM_HEXAFLUORIDE = 0xFFB4B8AC;
    // A boiling reactor's steam: steam with the faintest blue-green cast.
    public static final int CONTAMINATED_STEAM = 0xFFDCF0EC;
    // Nitrogen Dioxide: the Ostwald process's reddish-brown gas.
    public static final int NITROGEN_DIOXIDE = 0xFFC8743C;
    // Sulfur Dioxide: colourless in fact, so a clear pale yellow, paler and warmer than Fluorine.
    public static final int SULFUR_DIOXIDE = 0xFFF2ECB8;
    // Acetylene: colourless in fact, so a faint warm white.
    public static final int ACETYLENE = 0xFFF2EEE4;
    // The fusion gases, all colourless in fact: Deuterium a pale blue, Tritium a pale green, Helium-3 a pale gold,
    // Helium near-white, Argon a faint lavender (paler and greyer than Nitrogen's lilac), Helium-Rich Natural Gas Natural
    // Gas's pale blue gone grey, Fusion Exhaust a dull grey-violet.
    public static final int DEUTERIUM = 0xFFC6DCF4;
    public static final int TRITIUM = 0xFFCDEBCB;
    public static final int HELIUM = 0xFFF2F2EE;
    public static final int HELIUM_3 = 0xFFEEDFB0;
    // Hot Helium: the helium coolant hot from a fusion reactor, a warm peach-white (Superheated Steam's warm cast, paler).
    public static final int HOT_HELIUM = 0xFFF6DCC8;
    public static final int ARGON = 0xFFDCD4EE;
    public static final int HELIUM_RICH_NATURAL_GAS = 0xFFD4E4EA;
    public static final int FUSION_EXHAUST = 0xFFB6AEC4;

    private GasTints() {}
}
