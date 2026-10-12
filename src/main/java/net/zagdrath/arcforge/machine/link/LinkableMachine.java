/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.link;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.logistics.WiredMachineLinkBlockEntity;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;

// A machine (or multiblock controller) a Link Conduit can link to a Wired Machine Link (see MachineLinks). It keeps its
// link in a MachineLinkState (saved with it), and its capability code reads the cable's face as MACHINE_LINK, and every
// face as forcedLinkMode() while a Wired Machine Link asks for a mode's handler: single machines in modeFor, structures in
// MultiblockController.faceMode.
public interface LinkableMachine extends ConfigurableMachine {
    MachineLinkState machineLinkState();

    BlockPos getBlockPos();

    @Nullable Level getLevel();

    Component getDisplayName();

    // Its link, while the Wired Machine Link at the cable's far end still has it (or is in an unloaded chunk); null if it
    // has none.
    default @Nullable MachineLink getMachineLink() {
        MachineLink link = machineLinkState().link();
        Level level = getLevel();
        if (link == null || level == null || !level.isLoaded(link.linkPos())) {
            return link;
        }
        return level.getBlockEntity(link.linkPos()) instanceof WiredMachineLinkBlockEntity wml && wml.isLinkedTo(this) ? link : null;
    }

    // Whether this face of the block at pos (the machine, or a block of its structure) is where its Link Conduit is.
    default boolean isLinkFace(BlockPos pos, Direction side) {
        MachineLink link = getMachineLink();
        return link != null && link.isFace(pos, side);
    }

    // A Wired Machine Link takes this machine: false if another one already has it.
    default boolean claimMachineLink(MachineLink link) {
        MachineLink current = getMachineLink();
        if (current != null && !current.linkPos().equals(link.linkPos())) {
            return false;
        }
        MachineLink old = machineLinkState().link();
        if (!link.equals(old)) {
            machineLinkState().set(link);
            onMachineLinkChanged(old);
        }
        return true;
    }

    // The Wired Machine Link at linkPos lets this machine go (its face takes back the mode it had).
    default void releaseMachineLink(BlockPos linkPos) {
        MachineLink old = machineLinkState().link();
        if (old != null && old.linkPos().equals(linkPos)) {
            machineLinkState().set(null);
            onMachineLinkChanged(old);
        }
    }

    // Saves it and reconnects everything round the machine (or structure), and round the face the cable left.
    default void onMachineLinkChanged(@Nullable MachineLink old) {
        ((BlockEntity) this).setChanged();
        Level level = getLevel();
        if (level == null) {
            return;
        }
        if (this instanceof MultiblockController controller) {
            MultiblockAutomation.refresh(level, controller.getMinCorner(), controller.getMaxCorner());
        } else {
            level.invalidateCapabilities(getBlockPos());
            ConduitBlock.refreshAround(level, getBlockPos());
        }
        if (old != null && !old.anchor().equals(getBlockPos())) {
            level.invalidateCapabilities(old.anchor());
            ConduitBlock.refreshAround(level, old.anchor());
        }
    }

    // The machine is being removed: its Wired Machine Link drops its handlers now (not on its next trace), so nothing goes
    // into a machine that's gone.
    default void notifyMachineLinkRemoved() {
        MachineLink link = machineLinkState().link();
        Level level = getLevel();
        if (link != null && level != null && level.isLoaded(link.linkPos())
                && level.getBlockEntity(link.linkPos()) instanceof WiredMachineLinkBlockEntity wml) {
            wml.machineRemoved(this);
        }
    }

    // The mode a Wired Machine Link is asking for, while it looks up a handler (see withForcedMode); null otherwise.
    default @Nullable SideMode forcedLinkMode() {
        return machineLinkState().forced();
    }

    // Runs query with every face of the machine (or its structure) reading as mode, so the machine's own capability code
    // hands out the handler a face in that mode would. Server thread only.
    default <T> @Nullable T withForcedMode(SideMode mode, Supplier<@Nullable T> query) {
        MachineLinkState state = machineLinkState();
        SideMode previous = state.forced();
        state.force(mode);
        try {
            return query.get();
        } finally {
            state.force(previous);
        }
    }
}
