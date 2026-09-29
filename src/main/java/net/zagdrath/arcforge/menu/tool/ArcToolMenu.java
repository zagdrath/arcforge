/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.tool;

import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.ModuleType;
import net.zagdrath.arcforge.item.tool.ToolModuleItem;
import net.zagdrath.arcforge.item.tool.ToolModules;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModMenuTypes;

// An Arc Drill or Arc Saw's module slots, for the tool in the player's hand. The four slots are a view of the tool's
// TOOL_MODULES: loaded when the menu opens and written back on every change, so the modules live in the tool. Slots
// past the tier's count are locked, and the hotbar slot holding the tool can't be moved while it's open.
public class ArcToolMenu extends AbstractContainerMenu {
    // Button ids: TOGGLE + i flips slot i's module.
    public static final int TOGGLE = 100;
    public static final int[] SLOT_X = { 53, 71, 89, 107 };
    public static final int SLOT_Y = 31;

    private final Player player;
    private final int toolSlot;
    // Every change writes back into the tool.
    private final SimpleContainer modules = new SimpleContainer(ToolModules.SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }
    };
    private final int hotbarSlotIndex;
    private boolean loading;

    // Client constructor, called with the hotbar slot written by the server.
    public ArcToolMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readVarInt());
    }

    public ArcToolMenu(int containerId, Inventory inventory, int toolSlot) {
        super(ModMenuTypes.ARC_TOOL.get(), containerId);
        this.player = inventory.player;
        this.toolSlot = toolSlot;
        load();
        for (int index = 0; index < ToolModules.SIZE; index++) {
            addSlot(new ModuleSlot(index, SLOT_X[index], SLOT_Y));
        }
        addStandardInventorySlots(inventory, 8, 84);
        // addStandardInventorySlots adds the 27 main slots, then the hotbar.
        this.hotbarSlotIndex = ToolModules.SIZE + 27 + toolSlot;
    }

    public ItemStack tool() {
        return player.getInventory().getItem(toolSlot);
    }

    public int usableSlots() {
        return tool().getItem() instanceof ArcToolItem item ? item.moduleSlots() : 0;
    }

    public ToolModules toolModules() {
        return ArcToolItem.modules(tool());
    }

    private void load() {
        loading = true;
        ToolModules installed = toolModules();
        for (int index = 0; index < ToolModules.SIZE; index++) {
            modules.setItem(index, installed.slot(index).map(type -> new ItemStack(ModItems.toolModule(type).get())).orElse(ItemStack.EMPTY));
        }
        loading = false;
    }

    // Writes the slots back into the tool, turning a new module on (unless it conflicts).
    private void save() {
        ItemStack tool = tool();
        if (loading || !(tool.getItem() instanceof ArcToolItem)) {
            return;
        }
        ToolModules installed = ArcToolItem.modules(tool);
        ToolModules updated = installed;
        for (int index = 0; index < ToolModules.SIZE; index++) {
            Optional<ModuleType> type = modules.getItem(index).getItem() instanceof ToolModuleItem module ? Optional.of(module.type()) : Optional.empty();
            if (!type.equals(updated.slot(index))) {
                updated = updated.withSlot(index, type);
            }
        }
        if (!updated.equals(installed)) {
            tool.set(ModDataComponents.TOOL_MODULES.get(), updated);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        int index = buttonId - TOGGLE;
        if (index < 0 || index >= usableSlots()) {
            return false;
        }
        ToolModules installed = toolModules();
        if (installed.slot(index).isEmpty()) {
            return false;
        }
        ToolModules toggled = installed.toggle(index);
        tool().set(ModDataComponents.TOOL_MODULES.get(), toggled);
        announce(player, installed, toggled, index);
        return true;
    }

    // "Silk Touch is off while Fortune II is on" when turning one on turned the other off.
    public static void announce(Player player, ToolModules before, ToolModules after, int index) {
        ModuleType type = after.slot(index).orElseThrow();
        Optional<ModuleType> off = before.turnedOffBy(after, index);
        if (off.isPresent()) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.arc_tool.conflict", off.get().displayName(), type.displayName()));
        } else {
            player.sendOverlayMessage(Component.translatable(after.isOn(index) ? "message.arcforge.arc_tool.module_on" : "message.arcforge.arc_tool.module_off",
                    type.displayName()));
        }
    }

    // The tool's own hotbar slot can't be picked up, swapped or thrown while its modules are open.
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
        if (slotIndex == hotbarSlotIndex || (containerInput == ContainerInput.SWAP && buttonNum == toolSlot)) {
            return;
        }
        super.clicked(slotIndex, buttonNum, containerInput, player);
    }

    // Shift-click: a module goes into the first slot that takes it, and back out to the inventory.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index == hotbarSlotIndex) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < ToolModules.SIZE) {
            if (!moveItemStackTo(stack, ToolModules.SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int target = 0; target < ToolModules.SIZE && !moved; target++) {
                Slot moduleSlot = slots.get(target);
                if (!moduleSlot.hasItem() && moduleSlot.mayPlace(stack)) {
                    moduleSlot.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getInventory().getSelectedSlot() == toolSlot && tool().getItem() instanceof ArcToolItem;
    }

    private class ModuleSlot extends Slot {
        private final int index;

        ModuleSlot(int index, int x, int y) {
            super(modules, index, x, y);
            this.index = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && stack.getItem() instanceof ToolModuleItem module && toolModules().canInstall(module.type(), index);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return index < usableSlots();
        }
    }
}
