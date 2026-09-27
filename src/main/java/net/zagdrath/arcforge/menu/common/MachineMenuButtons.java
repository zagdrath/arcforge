/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.common;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;

// Menu button ids shared by every machine GUI (sent through vanilla's container button packet),
// and the server-side handling that applies them to a ConfigurableMachine.
public final class MachineMenuButtons {
    private static final int REDSTONE_FIRST = 0;
    private static final int SIDE_FIRST = 100;
    public static final int CLEAR_SIDES = 99;

    private MachineMenuButtons() {}

    public static int redstoneButtonId(RedstoneMode mode) {
        return REDSTONE_FIRST + mode.ordinal();
    }

    public static int sideButtonId(RelativeSide side, int action) {
        return SIDE_FIRST + side.ordinal() * SideConfig.ACTION_COUNT + action;
    }

    // Applies a button to the machine at the menu's position. Returns false for ids that aren't ours.
    public static boolean handle(ContainerLevelAccess access, int buttonId) {
        if (buttonId >= REDSTONE_FIRST && buttonId < REDSTONE_FIRST + RedstoneMode.values().length) {
            RedstoneMode mode = RedstoneMode.byId(buttonId - REDSTONE_FIRST);
            apply(access, machine -> machine.setRedstoneMode(mode));
            return true;
        }

        if (buttonId == CLEAR_SIDES) {
            apply(access, ConfigurableMachine::clearSideModes);
            return true;
        }

        int sideButton = buttonId - SIDE_FIRST;
        if (sideButton >= 0 && sideButton < RelativeSide.values().length * SideConfig.ACTION_COUNT) {
            RelativeSide side = RelativeSide.byId(sideButton / SideConfig.ACTION_COUNT);
            int action = sideButton % SideConfig.ACTION_COUNT;
            apply(access, machine -> {
                List<SideMode> allowed = machine.getAllowedSideModes();
                SideMode current = machine.getSideMode(side);
                int index = Math.max(0, allowed.indexOf(current));
                machine.setSideMode(side, switch (action) {
                    case SideConfig.ACTION_NEXT -> allowed.get((index + 1) % allowed.size());
                    case SideConfig.ACTION_PREVIOUS -> allowed.get((index + allowed.size() - 1) % allowed.size());
                    default -> SideMode.NONE;
                });
            });
            return true;
        }
        return false;
    }

    private static void apply(ContainerLevelAccess access, Consumer<ConfigurableMachine> action) {
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof ConfigurableMachine machine) {
                action.accept(machine);
            }
        });
    }
}
