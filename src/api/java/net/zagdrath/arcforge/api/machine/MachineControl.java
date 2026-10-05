/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.api.machine.resource.MachineEnergy;
import net.zagdrath.arcforge.api.machine.resource.MachineFluids;
import net.zagdrath.arcforge.api.machine.resource.MachineHeat;
import net.zagdrath.arcforge.api.machine.resource.MachineItems;
import net.zagdrath.arcforge.api.machine.settings.MachineSettings;
import net.zagdrath.arcforge.api.machine.status.MachineListener;
import net.zagdrath.arcforge.api.machine.status.MachineStatistics;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;

/**
 * One Arcforge machine or multiblock, as {@link MachineCapabilities#MACHINE_CONTROL} provides it.
 *
 * <p>Server side only: call every method on the server thread. An instance belongs to one loaded block entity (the
 * controller's, for a multiblock); once that is unloaded or broken, {@link #isValid()} returns {@code false} and the
 * instance should be dropped. NeoForge's {@code BlockCapabilityCache} is a convenient way to hold one.
 *
 * <p>Features a machine lacks are reported as empty, never by throwing; see the package documentation.
 */
public interface MachineControl {
    // --- Identity ---

    /**
     * Returns the kind of machine: its block entity type's id, e.g. {@code arcforge:arc_crusher} or
     * {@code arcforge:steam_turbine_array}.
     *
     * @return the machine type id
     */
    Identifier machineType();

    /**
     * Returns the machine's display name (its custom name if renamed).
     *
     * @return the display name
     */
    Component displayName();

    /**
     * Returns the machine's tier. Arcforge machines currently have no tiers, so this is empty for all of them; it is
     * here for machines that may have tiers in future.
     *
     * @return the tier, from 1 up, or empty for a machine without tiers
     */
    OptionalInt tier();

    /**
     * Returns whether this is a multiblock.
     *
     * @return {@code true} for a multiblock, {@code false} for a single-block machine
     */
    boolean isMultiblock();

    /**
     * Returns whether the machine is formed. Single-block machines always are.
     *
     * @return {@code false} only for a multiblock whose structure is not complete
     */
    boolean isFormed();

    /**
     * Returns a formed multiblock's size.
     *
     * @return the structure's size, or empty for single-block machines and unformed multiblocks
     */
    Optional<StructureSize> structureSize();

    /**
     * Returns where the machine is: the single block, or the multiblock's controller block.
     *
     * @return the machine's position
     */
    BlockPos position();

    /**
     * Returns the machine's owner, for consumers that check permissions.
     *
     * @return the owner, or empty if the machine has none yet
     */
    Optional<MachineOwner> owner();

    /**
     * Returns whether this instance still refers to a loaded machine.
     *
     * @return {@code false} once the machine is unloaded or broken
     */
    boolean isValid();

    // --- Status ---

    /**
     * Returns what the machine is doing.
     *
     * @return the machine's status
     */
    MachineStatus status();

    /**
     * Returns the machine's own description of its status, as its GUI shows it, e.g. "No fluid" or "Too cold".
     *
     * @return a human-readable reason
     */
    Component statusReason();

    /**
     * Returns how far through its current operation the machine is.
     *
     * @return from 0 to 1, or empty if the machine is not in an operation or has no measurable progress
     */
    OptionalDouble progress();

    /**
     * Returns roughly how long the current operation will take to finish, assuming the machine keeps working.
     *
     * @return the ticks remaining, or empty if the machine is not in an operation or cannot tell
     */
    OptionalInt ticksRemaining();

    /**
     * Returns the items the current operation will produce.
     *
     * @return copies of the expected item outputs, or an empty list if none or unknown
     */
    List<ItemStack> currentOutputs();

    /**
     * Returns the fluids the current operation will produce.
     *
     * @return copies of the expected fluid outputs, or an empty list if none or unknown
     */
    List<FluidStack> currentFluidOutputs();

    // --- Resources ---

    /**
     * Returns the machine's energy buffer.
     *
     * @return the energy view, or empty for a machine without energy
     */
    Optional<MachineEnergy> energy();

    /**
     * Returns the machine's item slots.
     *
     * @return the item view, or empty for a machine without items
     */
    Optional<MachineItems> items();

    /**
     * Returns the machine's fluid and gas tanks.
     *
     * @return the fluid view, or empty for a machine without tanks
     */
    Optional<MachineFluids> fluids();

    /**
     * Returns the machine's heat buffer.
     *
     * @return the heat view, or empty for a machine without heat
     */
    Optional<MachineHeat> heat();

    // --- Settings, statistics and events ---

    /**
     * Returns the machine's settings.
     *
     * @return the settings view (every machine has one, though some settings may be unsupported)
     */
    MachineSettings settings();

    /**
     * Returns what the machine has done.
     *
     * @return the statistics view
     */
    MachineStatistics statistics();

    /**
     * Starts sending this machine's events to a listener. Adding the same listener twice has no effect.
     *
     * @param listener the listener
     */
    void addListener(MachineListener listener);

    /**
     * Stops sending this machine's events to a listener.
     *
     * @param listener the listener
     */
    void removeListener(MachineListener listener);
}
