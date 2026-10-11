/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.storage;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.block.storage.ItemReservoirBlock;
import net.zagdrath.arcforge.blockentity.storage.ItemReservoirBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ItemReservoirStore;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// An Item Reservoir's GUI: the Crate's scrolling window of 6 rows over the whole store's slots, with a search box. On
// the server the 54 window slots point at the store's slots that match the search (all of them when it's empty), from
// the scrolled row on; the client holds just the window, filled by the usual slot sync. Scrolling is a menu button
// (SCROLL_BUTTON + row); the search text comes as an ItemReservoirSearchPayload and starts the window at the top. A
// search matches an item's name, or "@mod" its mod. Shift-clicking from the inventory fills the whole store.
public class ItemReservoirMenu extends AbstractContainerMenu {
    public static final int COLUMNS = CrateMenu.COLUMNS;
    public static final int VISIBLE_ROWS = CrateMenu.VISIBLE_ROWS;
    public static final int VISIBLE_SLOTS = COLUMNS * VISIBLE_ROWS;
    public static final int SCROLL_BUTTON = CrateMenu.SCROLL_BUTTON;
    public static final int SLOTS_X = CrateMenu.SLOTS_X, SLOTS_Y = CrateMenu.SLOTS_Y, INVENTORY_Y = CrateMenu.INVENTORY_Y;
    public static final int MAX_QUERY = 64;
    // Synced values: the store's slots, how many match the search, and the first row in view (the server clamps it when
    // fewer rows match).
    private static final int DATA_SLOTS = 0, DATA_MATCHES = 1, DATA_OFFSET = 2, DATA_VALUES = 3;

    private final ContainerLevelAccess access;
    private final @Nullable ItemReservoirBlockEntity reservoir;
    private final ContainerData data;
    // Server: the store slots in view, in order (window slot i shows matches[rowOffset * 9 + i]).
    private final IntList matches = new IntArrayList();
    private String query = "";
    private int rowOffset;

    // Client constructor, called with the block position written by the server.
    public ItemReservoirMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), null,
                new FilteredItemHandler(VISIBLE_SLOTS, (slot, resource) -> ItemReservoirBlockEntity.accepts(resource), () -> {}),
                WideIntContainerData.client(DATA_VALUES));
    }

    public ItemReservoirMenu(int containerId, Inventory inventory, ItemReservoirBlockEntity reservoir) {
        this(containerId, inventory, reservoir.getBlockPos(), reservoir, null, null);
        reservoir.startOpen(inventory.player);
    }

    private ItemReservoirMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable ItemReservoirBlockEntity reservoir,
            @Nullable FilteredItemHandler clientWindow, @Nullable ContainerData clientData) {
        super(ModMenuTypes.ITEM_RESERVOIR.get(), containerId);
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.reservoir = reservoir;
        this.data = clientData != null ? clientData : new WideIntContainerData(DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return index == DATA_SLOTS ? store().slots() : index == DATA_MATCHES ? matches.size() : rowOffset;
            }
        };
        checkContainerDataCount(data, DATA_VALUES * 2);
        if (clientWindow != null) {
            for (int i = 0; i < VISIBLE_SLOTS; i++) {
                addSlot(new ResourceHandlerSlot(clientWindow, clientWindow::set, i, SLOTS_X + i % COLUMNS * 18, SLOTS_Y + i / COLUMNS * 18));
            }
        } else {
            refreshMatches();
            Window window = new Window();
            for (int i = 0; i < VISIBLE_SLOTS; i++) {
                addSlot(new WindowSlot(window, i, SLOTS_X + i % COLUMNS * 18, SLOTS_Y + i / COLUMNS * 18));
            }
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    // --- The store and the search (server) ---

    private ItemReservoirStore store() {
        return reservoir.getStore();
    }

    // Whether the stack matches a search: its name contains the text, or with "@" its mod's id does. An empty search
    // matches everything, empty slots included.
    public static boolean matches(ItemStack stack, String query) {
        String text = query.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return true;
        }
        if (stack.isEmpty()) {
            return false;
        }
        if (text.startsWith("@")) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(text.substring(1));
        }
        // The name is the server's language; the item's id matches too, so a search in English works anywhere.
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(text)
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ').contains(text);
    }

    // The store slots the search shows. Rebuilt every sync: the store's slots move as blocks join or leave, and with a
    // search on, as stacks come and go.
    private void refreshMatches() {
        ItemReservoirStore store = store();
        IntList next = new IntArrayList();
        int slots = store.slots();
        for (int index = 0; index < slots; index++) {
            if (query.isEmpty() || matches(store.getStack(index), query)) {
                next.add(index);
            }
        }
        if (!next.equals(matches)) {
            matches.clear();
            matches.addAll(next);
            rowOffset = Math.min(rowOffset, getMaxOffset());
            windowMoved();
        }
    }

    public void setQuery(String text) {
        String next = text.length() > MAX_QUERY ? text.substring(0, MAX_QUERY) : text;
        if (!next.equals(query)) {
            query = next;
            rowOffset = 0;
            if (reservoir != null) {
                refreshMatches();
                windowMoved();
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (reservoir != null && !reservoir.isRemoved()) {
            refreshMatches();
        }
        super.broadcastChanges();
    }

    // The store slot a window slot shows, or -1.
    private int storeIndex(int windowSlot) {
        int at = rowOffset * COLUMNS + windowSlot;
        return at < matches.size() ? matches.getInt(at) : -1;
    }

    // --- Scrolling ---

    // How many rows the search shows (on the client, from the synced count).
    public int getRows() {
        int count = reservoir != null ? matches.size() : WideIntContainerData.read(data, DATA_MATCHES);
        return (count + COLUMNS - 1) / COLUMNS;
    }

    public int getStoreSlots() {
        return WideIntContainerData.read(data, DATA_SLOTS);
    }

    public int getMaxOffset() {
        return Math.max(0, getRows() - VISIBLE_ROWS);
    }

    public int getRowOffset() {
        return rowOffset;
    }

    // Client: when fewer rows match than the row in view (the server has moved its window up), follow the server.
    public void followServerOffset() {
        if (reservoir == null && rowOffset > getMaxOffset()) {
            rowOffset = Math.min(WideIntContainerData.read(data, DATA_OFFSET), getMaxOffset());
        }
    }

    public void setRowOffset(int offset) {
        rowOffset = Math.clamp(offset, 0, getMaxOffset());
        windowMoved();
    }

    private void windowMoved() {
        for (int i = 0; i < VISIBLE_SLOTS && i < slots.size(); i++) {
            if (slots.get(i) instanceof WindowSlot slot) {
                slot.moved();
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= SCROLL_BUTTON && buttonId <= SCROLL_BUTTON + getMaxOffset()) {
            setRowOffset(buttonId - SCROLL_BUTTON);
            return true;
        }
        return false;
    }

    // --- The server's window over the store ---

    private final class Window implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return VISIBLE_SLOTS;
        }

        @Override
        public ItemResource getResource(int index) {
            int at = storeIndex(index);
            return at < 0 ? ItemResource.EMPTY : store().handler().getResource(at);
        }

        @Override
        public long getAmountAsLong(int index) {
            int at = storeIndex(index);
            return at < 0 ? 0 : store().handler().getAmountAsLong(at);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            int at = storeIndex(index);
            return at < 0 ? 0 : store().handler().getCapacityAsLong(at, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            int at = storeIndex(index);
            return at >= 0 && store().handler().isValid(at, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            int at = storeIndex(index);
            return at < 0 ? 0 : store().handler().insert(at, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            int at = storeIndex(index);
            return at < 0 ? 0 : store().handler().extract(at, resource, amount, transaction);
        }

        // A player's click sets the slot outright, as on a chest.
        void set(int index, ItemResource resource, int amount) {
            int at = storeIndex(index);
            if (at >= 0) {
                store().set(at, resource, amount);
            }
        }
    }

    private static final class WindowSlot extends ResourceHandlerSlot {
        WindowSlot(Window window, int index, int x, int y) {
            super(window, window::set, index, x, y);
        }

        void moved() {
            clearCachedReturnStack();
        }
    }

    // --- Clicks ---

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
        } else if (reservoir != null) {
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

    // Into any of the store's slots, topping up matching stacks before taking empty slots. Returns how many went in.
    private int insertAnywhere(ItemStack stack) {
        ResourceHandler<ItemResource> handler = store().handler();
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

    // Closes if the block is gone or replaced, or the player walked away.
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).getBlock() instanceof ItemReservoirBlock
                && (reservoir == null || (level.getBlockEntity(pos) == reservoir && !reservoir.isRemoved()))
                && player.isWithinBlockInteractionRange(pos, 4.0), true);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (reservoir != null) {
            reservoir.stopOpen(player);
        }
    }
}
