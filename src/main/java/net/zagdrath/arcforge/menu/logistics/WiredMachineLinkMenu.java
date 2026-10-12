/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.logistics;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.zagdrath.arcforge.block.logistics.WiredMachineLinkBlock;
import net.zagdrath.arcforge.blockentity.logistics.WiredMachineLinkBlockEntity;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.common.MenuReach;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.security.SecuredMenu;

// A Wired Machine Link's screen: no slots. What it shows (the machine it's linked to, the face the cable is on, the
// distance, its faces) comes from its block entity, which syncs to the client when it changes. Face buttons are
// faceButtonId(side, SideConfig.ACTION_*); Security tab buttons are MachineMenuButtons'.
public class WiredMachineLinkMenu extends AbstractContainerMenu implements SecuredMenu {
    private static final int FACE_FIRST = 100;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Block block;
    private final Level level;

    // Client constructor, called with the block's position written by the server.
    public WiredMachineLinkMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public WiredMachineLinkMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuTypes.WIRED_MACHINE_LINK.get(), containerId);
        this.pos = pos;
        this.level = inventory.player.level();
        this.access = ContainerLevelAccess.create(level, pos);
        Block here = level.getBlockState(pos).getBlock();
        this.block = here instanceof WiredMachineLinkBlock ? here : Blocks.AIR;
    }

    public static int faceButtonId(RelativeSide side, int action) {
        return FACE_FIRST + side.ordinal() * SideConfig.ACTION_COUNT + action;
    }

    public BlockPos getPos() {
        return pos;
    }

    // The block entity (on the client, the synced copy).
    public @Nullable WiredMachineLinkBlockEntity link() {
        return level.getBlockEntity(pos) instanceof WiredMachineLinkBlockEntity link ? link : null;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        int face = buttonId - FACE_FIRST;
        if (face >= 0 && face < RelativeSide.values().length * SideConfig.ACTION_COUNT) {
            RelativeSide side = RelativeSide.byId(face / SideConfig.ACTION_COUNT);
            int action = face % SideConfig.ACTION_COUNT;
            access.execute((level, at) -> {
                if (level.getBlockEntity(at) instanceof WiredMachineLinkBlockEntity link) {
                    link.cycleFace(side, action);
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
