/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;

// Batteries carried by a player charge the FE items they carry: every tick each Battery (in the inventory, offhand or a
// Curios slot) gives up to its rate to the held item, the offhand, the armour, the Curios slots, the hotbar and then the
// rest of the inventory, in that order. Any item with an FE capability takes it (Arcforged gear, Arc tools, Long Fall
// Boots, other mods' tools), except other Batteries, so carried Batteries never shuffle FE among themselves.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class BatteryCharging {
    private static final int[] ARMOUR_SLOTS = { 39, 38, 37, 36 };

    private BatteryCharging() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isSpectator()) {
            charge(player);
        }
    }

    // One tick of a player's Batteries charging what else they carry.
    public static void charge(Player player) {
        List<ItemAccess> slots = slotsInOrder(player);
        List<EnergyHandler> batteries = new ArrayList<>();
        List<Integer> batteryRates = new ArrayList<>();
        for (ItemAccess access : slots) {
            ItemStack stack = access.getResource().toStack(access.getAmount());
            if (isBattery(stack) && access.getCapability(Capabilities.Energy.ITEM) instanceof EnergyHandler battery
                    && battery.getAmountAsInt() > 0) {
                batteries.add(battery);
                batteryRates.add(((PortableStorageItem) stack.getItem()).rate());
            }
        }
        if (batteries.isEmpty()) {
            return;
        }
        for (ItemAccess access : slots) {
            ItemStack stack = access.getResource().toStack(access.getAmount());
            if (stack.isEmpty() || isBattery(stack)) {
                continue;
            }
            EnergyHandler target = access.getCapability(Capabilities.Energy.ITEM);
            if (target == null || target.getAmountAsInt() >= target.getCapacityAsInt()) {
                continue;
            }
            for (int i = 0; i < batteries.size(); i++) {
                int budget = batteryRates.get(i);
                if (budget <= 0) {
                    continue;
                }
                int moved = EnergyHandlerUtil.move(batteries.get(i), target, budget, null);
                batteryRates.set(i, budget - moved);
                if (target.getAmountAsInt() >= target.getCapacityAsInt()) {
                    break;
                }
            }
        }
    }

    private static boolean isBattery(ItemStack stack) {
        return stack.getItem() instanceof PortableStorageItem item && item.kind() == PortableStorageItem.Kind.BATTERY;
    }

    // The held item, the offhand, the armour, the Curios slots, the rest of the hotbar, then the main inventory.
    private static List<ItemAccess> slotsInOrder(Player player) {
        List<ItemAccess> slots = new ArrayList<>();
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        slots.add(ItemAccess.forPlayerSlot(player, selected));
        slots.add(ItemAccess.forPlayerSlot(player, Inventory.SLOT_OFFHAND));
        for (int slot : ARMOUR_SLOTS) {
            slots.add(ItemAccess.forPlayerSlot(player, slot));
        }
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(curios -> curios.getCurios().values().forEach(handler -> {
            var stacks = handler.getStacks();
            for (int i = 0; i < stacks.size(); i++) {
                slots.add(ItemAccess.forHandlerIndex(stacks, i));
            }
        }));
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot != selected) {
                slots.add(ItemAccess.forPlayerSlot(player, slot));
            }
        }
        return slots;
    }
}
