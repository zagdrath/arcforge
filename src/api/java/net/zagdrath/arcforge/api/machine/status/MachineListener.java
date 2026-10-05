/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.status;

import net.minecraft.network.chat.Component;
import net.zagdrath.arcforge.api.machine.MachineControl;

/**
 * Receives a machine's events, so consumers need not poll. Register with
 * {@link MachineControl#addListener(MachineListener)}.
 *
 * <p>Events are delivered on the server thread, during the machine's own tick or the end of the level's tick, so
 * keep listeners quick. A listener may read the machine but should not change it from inside an event. An exception
 * thrown by a listener is logged and does not reach the machine. Listeners are not saved: once the machine is
 * unloaded or broken ({@link MachineControl#isValid()} is {@code false}) they receive nothing more, and a consumer
 * that wants to keep listening must look the machine up again and add its listener to the new instance.
 */
public interface MachineListener {
    /**
     * Called when the machine's {@link MachineControl#status()} changes. Checked once a tick.
     *
     * @param machine  the machine
     * @param previous the status before
     * @param current  the status now
     */
    default void onStatusChanged(MachineControl machine, MachineStatus previous, MachineStatus current) {}

    /**
     * Called when the machine finishes an operation.
     *
     * @param machine   the machine
     * @param operation what the operation produced and consumed
     */
    default void onOperationCompleted(MachineControl machine, CompletedOperation operation) {}

    /**
     * Called when the machine's status becomes {@link MachineStatus#FAULT}, after
     * {@link #onStatusChanged(MachineControl, MachineStatus, MachineStatus)}.
     *
     * @param machine the machine
     * @param reason  the machine's reason, as {@link MachineControl#statusReason()} gives it
     */
    default void onFault(MachineControl machine, Component reason) {}
}
