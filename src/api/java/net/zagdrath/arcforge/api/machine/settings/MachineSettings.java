/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.settings;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;

/**
 * A machine's settings. Every change is checked by the machine, exactly as if a player made it in the machine's GUI
 * or with a Wrench, and returns a {@link SettingResult} saying whether it was applied. Changes are saved with the
 * machine.
 */
public interface MachineSettings {
    /**
     * Returns whether the machine is switched on. Machines are on unless switched off through
     * {@link #setEnabled(boolean)}. A machine stopped by its redstone mode is still enabled.
     *
     * @return {@code true} if the machine is switched on
     */
    boolean isEnabled();

    /**
     * Switches the machine on or off. A machine switched off does not work, whatever its redstone mode, and
     * reports {@link MachineStatus#DISABLED}; it keeps its contents and progress, but a machine whose operations
     * cannot pause may lose the operation in progress, as it would to a redstone stop. Its GUI says it was switched
     * off remotely.
     *
     * @param enabled {@code true} to switch it on
     * @return {@link SettingResult#APPLIED}, {@link SettingResult#UNCHANGED}, or {@link SettingResult#UNSUPPORTED}
     */
    SettingResult setEnabled(boolean enabled);

    /**
     * Returns the machine's redstone mode.
     *
     * @return the redstone mode, or empty if the machine has none
     */
    Optional<RedstoneMode> redstoneMode();

    /**
     * Returns the redstone modes the machine offers, in the order its GUI shows them.
     *
     * @return the allowed modes, or an empty list if the machine has no redstone mode
     */
    List<RedstoneMode> allowedRedstoneModes();

    /**
     * Sets the redstone mode.
     *
     * @param mode the new mode
     * @return {@link SettingResult#INVALID} if the machine does not offer that mode
     */
    SettingResult setRedstoneMode(RedstoneMode mode);

    /**
     * Returns whether the machine has a side configuration (single-block machines do; multiblocks use
     * {@linkplain #ports() ports} instead).
     *
     * @return {@code true} if {@link #sideMode(MachineSide)} and {@link #setSideMode(MachineSide, String)} apply
     */
    boolean hasSideConfiguration();

    /**
     * Returns the mode of one face.
     *
     * @param side the face
     * @return one of the {@link SideModes} ids, or {@link SideModes#NONE} if the machine has no side configuration
     */
    String sideMode(MachineSide side);

    /**
     * Returns the modes the machine's faces can be set to, in the order its GUI cycles through them.
     *
     * @return the allowed {@link SideModes} ids, or an empty list if the machine has no side configuration
     */
    List<String> allowedSideModes();

    /**
     * Sets the mode of one face.
     *
     * @param side the face
     * @param mode one of {@link #allowedSideModes()}
     * @return {@link SettingResult#INVALID} if the machine does not offer that mode
     */
    SettingResult setSideMode(MachineSide side, String mode);

    /**
     * Sets every face to {@link SideModes#NONE}, as the GUI's clear button does.
     *
     * @return {@link SettingResult#UNSUPPORTED} if the machine has no side configuration
     */
    SettingResult clearSideModes();

    /**
     * Returns a formed multiblock's ports.
     *
     * @return every face set to a port mode, or an empty list for single-block machines and unformed multiblocks
     */
    List<MachinePort> ports();

    /**
     * Returns the modes a multiblock's ports can be set to, in the order the Wrench cycles through them.
     *
     * @return the allowed {@link SideModes} ids, or an empty list for single-block machines
     */
    List<String> allowedPortModes();

    /**
     * Sets the port on one face of a formed multiblock's block, as the Wrench in Port mode does. The block must be
     * part of this structure and able to hold ports, and the face must point out of the structure.
     *
     * @param pos  the block
     * @param face the face
     * @param mode one of {@link #allowedPortModes()}, or {@link SideModes#NONE} to remove the port
     * @return {@link SettingResult#REJECTED} if the structure is not formed or the face cannot hold a port,
     *         {@link SettingResult#INVALID} for a mode the structure does not offer
     */
    SettingResult setPort(BlockPos pos, Direction face, String mode);

    /**
     * Returns whether the machine has auto-eject (only machines with output faces or ports do).
     *
     * @return {@code true} if {@link #setAutoEject(boolean)} applies
     */
    boolean supportsAutoEject();

    /**
     * Returns whether output faces push products into neighbouring blocks.
     *
     * @return {@code true} if auto-eject is on
     */
    boolean isAutoEject();

    /**
     * Turns auto-eject on or off.
     *
     * @param autoEject {@code true} to push products out of output faces
     * @return {@link SettingResult#UNSUPPORTED} if the machine has no auto-eject
     */
    SettingResult setAutoEject(boolean autoEject);

    /**
     * Returns the machine's own settings, beyond the common ones above.
     *
     * @return the machine-specific options, or an empty list if it has none
     */
    List<MachineOption> options();

    /**
     * Changes a machine-specific option.
     *
     * @param id    the option's {@link MachineOption#id()}
     * @param value the new value, in the form its {@link MachineOption#type()} gives
     * @return {@link SettingResult#UNSUPPORTED} for an unknown id, {@link SettingResult#INVALID} for a value of the
     *         wrong form or out of range
     */
    SettingResult setOption(String id, String value);

    /**
     * Returns the upgrades installed. Read only: upgrades are changed in the machine's GUI.
     *
     * @return the installed upgrades, one entry per type, or an empty list
     */
    List<InstalledUpgrade> upgrades();

    /**
     * Returns the upgrade types the machine accepts.
     *
     * @return the accepted upgrade type ids (see {@link InstalledUpgrade#type()}), or an empty set
     */
    Set<String> acceptedUpgrades();
}
