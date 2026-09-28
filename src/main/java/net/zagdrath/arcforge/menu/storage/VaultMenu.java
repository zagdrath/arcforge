/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.storage;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModMenuTypes;

// A Vault's GUI: an input slot whose items go straight into the vault (whatever doesn't fit stays in the slot), an
// output slot showing up to a stack of what it holds (taking from it takes from the vault), the lock and void
// buttons, and the player inventory. The amount and settings are synced as data; the item type comes with the
// block entity's own sync.
public class VaultMenu extends AbstractContainerMenu {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int BUTTON_LOCK = 200;
    public static final int BUTTON_VOID = 201;
    private static final int PLAYER_START = 2;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Player player;
    private final @Nullable VaultBlockEntity vault;
    private final ContainerData data;
    private final SimpleContainer input = new SimpleContainer(1);
    private final SimpleContainer output = new SimpleContainer(1);

    // Client constructor, called with the block position written by the server.
    public VaultMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), null, WideIntContainerData.client(VaultBlockEntity.DATA_VALUES));
    }

    public VaultMenu(int containerId, Inventory inventory, VaultBlockEntity vault, ContainerData data) {
        this(containerId, inventory, vault.getBlockPos(), vault, data);
    }

    private VaultMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable VaultBlockEntity vault, ContainerData data) {
        super(ModMenuTypes.VAULT.get(), containerId);
        checkContainerDataCount(data, VaultBlockEntity.DATA_VALUES * 2);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.pos = pos;
        this.player = inventory.player;
        this.vault = vault;
        this.data = data;
        addSlot(new InputSlot(8, 18));
        addSlot(new OutputSlot(8, 52));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
        refreshOutput();
    }

    // The vault on this side: the server's, or the client's synced copy (for the item type).
    public @Nullable VaultBlockEntity getVault() {
        if (vault != null) {
            return vault;
        }
        return player.level().getBlockEntity(pos) instanceof VaultBlockEntity synced ? synced : null;
    }

    public ConduitTier getTier() {
        return player.level().getBlockState(pos).getBlock() instanceof VaultBlock block ? block.getTier() : ConduitTier.WROUGHT;
    }

    public int getAmount() {
        return WideIntContainerData.read(data, VaultBlockEntity.DATA_AMOUNT);
    }

    public int getCapacity() {
        return WideIntContainerData.read(data, VaultBlockEntity.DATA_CAPACITY);
    }

    public boolean isLocked() {
        return (WideIntContainerData.read(data, VaultBlockEntity.DATA_FLAGS) & VaultBlockEntity.FLAG_LOCKED) != 0;
    }

    public boolean isVoidMode() {
        return (WideIntContainerData.read(data, VaultBlockEntity.DATA_FLAGS) & VaultBlockEntity.FLAG_VOID) != 0;
    }

    public ItemStack getTemplate() {
        VaultBlockEntity synced = getVault();
        return synced != null ? synced.getTemplate() : ItemStack.EMPTY;
    }

    public SideMode getSideMode(RelativeSide side) {
        return SideConfig.unpack(WideIntContainerData.read(data, VaultBlockEntity.DATA_SIDE_CONFIG), side);
    }

    // The output slot shows up to a stack of what's stored (server side; the client gets it by slot sync).
    private void refreshOutput() {
        if (vault != null) {
            ItemStack template = vault.getTemplate();
            output.setItem(0, vault.getAmount() == 0 ? ItemStack.EMPTY
                    : template.copyWithCount(Math.min(template.getMaxStackSize(), vault.getAmount())));
        }
    }

    @Override
    public void broadcastChanges() {
        refreshOutput();
        super.broadcastChanges();
    }

    // Whatever's put here goes into the vault at once; what doesn't fit stays.
    private final class InputSlot extends Slot {
        InputSlot(int x, int y) {
            super(input, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            VaultBlockEntity target = getVault();
            return target != null && target.getStorage().accepts(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ItemStack stack = input.getItem(0);
            if (vault != null && !stack.isEmpty()) {
                int taken = vault.insert(stack);
                if (taken > 0) {
                    input.setItem(0, stack.copyWithCount(stack.getCount() - taken));
                    refreshOutput();
                }
            }
        }
    }

    // Shows a stack of the stored item; taking from it takes from the vault. Nothing can be put here.
    private final class OutputSlot extends Slot {
        OutputSlot(int x, int y) {
            super(output, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            if (vault == null) {
                return super.remove(amount);
            }
            ItemStack taken = vault.extract(amount);
            refreshOutput();
            return taken;
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_LOCK || buttonId == BUTTON_VOID) {
            if (vault != null) {
                if (buttonId == BUTTON_LOCK) {
                    vault.setLocked(!vault.isLocked());
                } else {
                    vault.setVoidMode(!vault.isVoidMode());
                }
            }
            return true;
        }
        return MachineMenuButtons.handle(access, buttonId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        if (slotIndex == SLOT_OUTPUT) {
            // One stack out to the inventory, taken from the vault.
            ItemStack moving = stack.copy();
            int before = moving.getCount();
            moveItemStackTo(moving, PLAYER_START, slots.size(), true);
            int moved = before - moving.getCount();
            if (moved > 0 && vault != null) {
                vault.extract(moved);
                refreshOutput();
            }
            return ItemStack.EMPTY;
        }
        if (slotIndex == SLOT_INPUT) {
            moveItemStackTo(stack, PLAYER_START, slots.size(), false);
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        // From the inventory: straight into the vault.
        if (vault != null && vault.getStorage().accepts(stack)) {
            int taken = vault.insert(stack);
            if (taken > 0) {
                stack.shrink(taken);
                slot.setChanged();
                refreshOutput();
            }
        }
        return ItemStack.EMPTY;
    }

    // Closes if the vault is gone or was swapped for another (a Storage Upgrade), or the player walked away.
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, blockPos) -> level.getBlockState(blockPos).getBlock() instanceof VaultBlock
                && (vault == null || (level.getBlockEntity(blockPos) == vault && !vault.isRemoved()))
                && player.isWithinBlockInteractionRange(blockPos, 4.0), true);
    }

    // Anything left in the input slot goes back to the player.
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (vault != null) {
            clearContainer(player, input);
        }
    }
}
