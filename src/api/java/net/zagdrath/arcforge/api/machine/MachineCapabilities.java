/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.zagdrath.arcforge.api.ArcforgeApi;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;

/**
 * The block capability through which Arcforge machines are controlled.
 *
 * <p>It is provided, on the server only, by every Arcforge machine block and by every block of a formed multiblock
 * (casings, ports, glass and the controller alike); all of a multiblock's blocks resolve to the same
 * {@link MachineControl}, its controller's. A multiblock with a dedicated controller block also provides it on that
 * block while the structure is not formed, reporting {@link MachineStatus#NOT_FORMED}. The capability has no
 * context: pass {@code null}.
 *
 * <pre>{@code
 * MachineControl machine = level.getCapability(MachineCapabilities.MACHINE_CONTROL, pos, null);
 * if (machine != null) {
 *     MachineStatus status = machine.status();
 * }
 * }</pre>
 */
public final class MachineCapabilities {
    /** The id of {@link #MACHINE_CONTROL}: {@code arcforge:machine_control}. */
    public static final Identifier MACHINE_CONTROL_ID = Identifier.fromNamespaceAndPath(ArcforgeApi.MOD_ID, "machine_control");

    /** The machine control capability. Query it with a {@code null} context. */
    public static final BlockCapability<MachineControl, @Nullable Void> MACHINE_CONTROL =
            BlockCapability.createVoid(MACHINE_CONTROL_ID, MachineControl.class);

    private MachineCapabilities() {}
}
