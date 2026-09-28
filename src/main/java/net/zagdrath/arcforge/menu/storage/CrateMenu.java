/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.storage;

import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.storage.CrateBlock;
import net.zagdrath.arcforge.blockentity.storage.CrateBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// A Crate's GUI: a window of 6 rows over its slots (all of them on a Wrought Crate; the larger ones scroll),
// then the player inventory. On the server the 54 window slots point at the crate's slots from the scrolled
// row on; the client holds just the window, filled by the usual slot sync, so a scroll shows the new rows one
// sync later. Scrolling is a menu button (SCROLL_BUTTON + row), sent before any click on the moved slots.
// Shift-clicking from the inventory fills the whole crate, not just the rows in view.
public class CrateMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    public static final int VISIBLE_ROWS = 6;
    public static final int VISIBLE_SLOTS = COLUMNS * VISIBLE_ROWS;
    public static final int SCROLL_BUTTON = 1_000;
    public static final int SLOTS_X = 8, SLOTS_Y = 18, INVENTORY_Y = 140;

    private final ContainerLevelAccess access;
    private final @Nullable CrateBlockEntity crate;
    private final FilteredItemHandler handler;
    private final ContainerData data;
    private final ConduitTier tier;
    private int rowOffset;

    // Client constructor, called with the block position written by the server.
    public CrateMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), null,
                new FilteredItemHandler(VISIBLE_SLOTS, CrateBlockEntity::accepts, () -> {}),
                WideIntContainerData.client(CrateBlockEntity.DATA_VALUES));
    }

    public CrateMenu(int containerId, Inventory inventory, CrateBlockEntity crate, ContainerData data) {
        this(containerId, inventory, crate.getBlockPos(), crate, crate.getItems(), data);
        crate.startOpen(inventory.player);
    }

    private CrateMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable CrateBlockEntity crate, FilteredItemHandler handler,
            ContainerData data) {
        super(ModMenuTypes.CRATE.get(), containerId);
        checkContainerDataCount(data, CrateBlockEntity.DATA_VALUES * 2);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.crate = crate;
        this.handler = handler;
        this.data = data;
        this.tier = inventory.player.level().getBlockState(pos).getBlock() instanceof CrateBlock block ? block.getTier() : ConduitTier.WROUGHT;
        // Only the server's slots follow the scroll; the client's are the window itself.
        IntSupplier offset = crate != null ? () -> rowOffset * COLUMNS : () -> 0;
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new CrateSlot(handler, row * COLUMNS + column, offset, SLOTS_X + column * 18, SLOTS_Y + row * 18));
            }
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    // A window slot: the crate slot `base` places on from the scrolled row.
    private static final class CrateSlot extends ResourceHandlerSlot {
        private final int base;
        private final IntSupplier offset;

        CrateSlot(FilteredItemHandler handler, int base, IntSupplier offset, int x, int y) {
            super(handler, handler::set, base, x, y);
            this.base = base;
            this.offset = offset;
        }

        @Override
        public int getSlotIndex() {
            return base + offset.getAsInt();
        }

        @Override
        public int getContainerSlot() {
            return getSlotIndex();
        }

        void scrolled() {
            clearCachedReturnStack();
        }
    }

    public ConduitTier getTier() {
        return tier;
    }

    public int getRows() {
        return tier.crateRows();
    }

    public int getMaxOffset() {
        return Math.max(0, getRows() - VISIBLE_ROWS);
    }

    // The first row in view (on the client: the one last scrolled to).
    public int getRowOffset() {
        return rowOffset;
    }

    public void setRowOffset(int offset) {
        rowOffset = Math.clamp(offset, 0, getMaxOffset());
        for (int i = 0; i < VISIBLE_SLOTS; i++) {
            ((CrateSlot) slots.get(i)).scrolled();
        }
    }

    public SideMode getSideMode(RelativeSide side) {
        return SideConfig.unpack(WideIntContainerData.read(data, CrateBlockEntity.DATA_SIDE_CONFIG), side);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= SCROLL_BUTTON && buttonId <= SCROLL_BUTTON + getMaxOffset()) {
            setRowOffset(buttonId - SCROLL_BUTTON);
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
        ItemStack original = stack.copy();
        if (slotIndex < VISIBLE_SLOTS) {
            if (!moveItemStackTo(stack, VISIBLE_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (crate != null) {
            int moved = insertAnywhere(stack);
            if (moved == 0) {
                return ItemStack.EMPTY;
            }
            stack.shrink(moved);
        } else if (!moveItemStackTo(stack, 0, VISIBLE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    // Into any of the crate's slots, topping up matching stacks before taking empty slots. Returns how many went in.
    private int insertAnywhere(ItemStack stack) {
        ItemResource resource = ItemResource.of(stack);
        int remaining = stack.getCount();
        try (Transaction tx = Transaction.openRoot()) {
            for (int pass = 0; pass < 2 && remaining > 0; pass++) {
                for (int index = 0; index < handler.size() && remaining > 0; index++) {
                    ItemResource current = handler.getResource(index);
                    if (pass == 0 ? current.equals(resource) : current.isEmpty()) {
                        remaining -= handler.insert(index, resource, remaining, tx);
                    }
                }
            }
            tx.commit();
        }
        return stack.getCount() - remaining;
    }

    // Closes if the crate is gone or was swapped for another (a Storage Upgrade), or the player walked away.
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof CrateBlock
                && (crate == null || (level.getBlockEntity(pos) == crate && !crate.isRemoved()))
                && player.isWithinBlockInteractionRange(pos, 4.0), true);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (crate != null) {
            crate.stopOpen(player);
        }
    }
}
