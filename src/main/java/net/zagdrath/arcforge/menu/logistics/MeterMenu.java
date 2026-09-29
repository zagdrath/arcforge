/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.zagdrath.arcforge.block.logistics.MeterBlock;
import net.zagdrath.arcforge.blockentity.logistics.MeterBlockEntity;
import net.zagdrath.arcforge.machine.meter.MeterKind;
import net.zagdrath.arcforge.machine.meter.MeterSettings;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.common.MenuReach;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.security.SecuredMenu;

// A Meter's screen: no slots, just its rate, threshold, mode and signal, and its Security tab. The screen sets the
// mode and the threshold through menu buttons: 0 and 1 for Above and Below, THRESHOLD_BUTTON + n for a threshold of n
// (clamped to the meter's cap); the security buttons are MachineMenuButtons'.
public class MeterMenu extends AbstractContainerMenu implements SecuredMenu {
    public static final int DATA_RATE = 0;
    public static final int DATA_THRESHOLD = 1;
    public static final int DATA_MODE = 2;
    public static final int DATA_POWERED = 3;
    public static final int DATA_VALUES = 4;

    public static final int BUTTON_ABOVE = 0, BUTTON_BELOW = 1;
    public static final int THRESHOLD_BUTTON = 1_000;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Block block;
    private final MeterKind kind;

    // Client constructor, called with the meter's position written by the server.
    public MeterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), WideIntContainerData.client(DATA_VALUES));
    }

    public MeterMenu(int containerId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(ModMenuTypes.METER.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;
        Block here = inventory.player.level().getBlockState(pos).getBlock();
        this.block = here instanceof MeterBlock ? here : Blocks.AIR;
        this.kind = here instanceof MeterBlock meter ? meter.kind() : MeterKind.ENERGY;
        addDataSlots(data);
    }

    public BlockPos getPos() {
        return pos;
    }

    public MeterKind getKind() {
        return kind;
    }

    private int value(int index) {
        return WideIntContainerData.read(data, index);
    }

    public int getRate() {
        return value(DATA_RATE);
    }

    public int getThreshold() {
        return value(DATA_THRESHOLD);
    }

    public MeterSettings.Mode getMode() {
        return MeterSettings.Mode.byId(value(DATA_MODE));
    }

    public boolean isPowered() {
        return value(DATA_POWERED) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_ABOVE || buttonId == BUTTON_BELOW || buttonId >= THRESHOLD_BUTTON) {
            access.execute((level, at) -> {
                if (level.getBlockEntity(at) instanceof MeterBlockEntity meter) {
                    MeterSettings now = meter.getSettings();
                    meter.setSettings(buttonId >= THRESHOLD_BUTTON ? new MeterSettings(buttonId - THRESHOLD_BUTTON, now.mode())
                            : new MeterSettings(now.threshold(), MeterSettings.Mode.byId(buttonId)));
                }
            });
            return true;
        }
        return MachineMenuButtons.handle(access, player, buttonId);
    }

    @Override
    public ContainerLevelAccess securityAccess() {
        return access;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return MenuReach.stillValid(access, player, block);
    }
}
