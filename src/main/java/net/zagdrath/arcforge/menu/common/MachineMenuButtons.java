/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.common;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.security.SecuredMenu;
import net.zagdrath.arcforge.security.SecurityMode;
import net.zagdrath.arcforge.security.SecurityRules;

// Menu button ids shared by every machine GUI (sent through vanilla's container button packet),
// and the server-side handling that applies them to a ConfigurableMachine.
public final class MachineMenuButtons {
    private static final int REDSTONE_FIRST = 0;
    private static final int SIDE_FIRST = 100;
    public static final int CLEAR_SIDES = 99;
    public static final int TOGGLE_AUTO_EJECT = 98;
    // Security: SECURITY_PROFILE clears the override (follow the owner's profile), then Public, Trusted, Private.
    public static final int SECURITY_PROFILE = 300;

    private MachineMenuButtons() {}

    public static int redstoneButtonId(RedstoneMode mode) {
        return REDSTONE_FIRST + mode.ordinal();
    }

    public static int sideButtonId(RelativeSide side, int action) {
        return SIDE_FIRST + side.ordinal() * SideConfig.ACTION_COUNT + action;
    }

    public static int securityButtonId(Optional<SecurityMode> override) {
        return SECURITY_PROFILE + override.map(mode -> mode.ordinal() + 1).orElse(0);
    }

    // Applies a button to the machine at the menu's position. Returns false for ids that aren't ours. Security buttons
    // only work for the owner (or an operator), and tell the player's screen the result.
    public static boolean handle(ContainerLevelAccess access, Player player, int buttonId) {
        if (buttonId >= SECURITY_PROFILE && buttonId <= SECURITY_PROFILE + SecurityMode.values().length) {
            Optional<SecurityMode> override = buttonId == SECURITY_PROFILE ? Optional.empty() : Optional.of(SecurityMode.byId(buttonId - SECURITY_PROFILE - 1));
            access.execute((level, pos) -> SecurityRules.of(level, pos).ifPresent(owned -> {
                if (SecurityRules.canEditSecurity(player, owned)) {
                    owned.setSecurityOverride(override);
                }
            }));
            if (player instanceof ServerPlayer serverPlayer) {
                SecuredMenu.sync(serverPlayer, player.containerMenu.containerId, access);
            }
            return true;
        }
        return handle(access, buttonId);
    }

    public static boolean handle(ContainerLevelAccess access, int buttonId) {
        if (buttonId >= REDSTONE_FIRST && buttonId < REDSTONE_FIRST + RedstoneMode.values().length) {
            RedstoneMode mode = RedstoneMode.byId(buttonId - REDSTONE_FIRST);
            apply(access, machine -> {
                if (machine.getAllowedRedstoneModes().contains(mode)) {
                    machine.setRedstoneMode(mode);
                }
            });
            return true;
        }

        if (buttonId == CLEAR_SIDES) {
            apply(access, ConfigurableMachine::clearSideModes);
            return true;
        }

        if (buttonId == TOGGLE_AUTO_EJECT) {
            apply(access, machine -> machine.setAutoEject(!machine.isAutoEject()));
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
