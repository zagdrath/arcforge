/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.conduit;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.steam.Gases;

// The settings of the Conduit Filter on one side of a conduit. There are no real filter slots: the nine entries
// are ghost slots set by clicking with an item (never used up). Every change is a menu button (see the ids
// below), applied on the server to the installed filter's settings component; the block entity then syncs the
// filter to clients, which is what the screen draws.
public class ConduitFilterMenu extends AbstractContainerMenu {
    // Button ids: 0-8 set or clear a ghost slot from the carried item; 10+i and 20+i step slot i's match to the
    // next or previous tag; then the three toggles.
    public static final int CHIP_NEXT = 10;
    public static final int CHIP_PREVIOUS = 20;
    public static final int BUTTON_LIST_MODE = 30;
    public static final int BUTTON_COMPONENTS = 31;
    public static final int BUTTON_DIRECTION = 32;

    private static final double MAX_DISTANCE = 8.0;

    private final Level level;
    private final BlockPos pos;
    private final Direction side;

    // Client constructor, called with the position and side written by the server.
    public ConduitFilterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), Direction.STREAM_CODEC.decode(extraData));
    }

    public ConduitFilterMenu(int containerId, Inventory inventory, BlockPos pos, Direction side) {
        super(ModMenuTypes.CONDUIT_FILTER.get(), containerId);
        this.level = inventory.player.level();
        this.pos = pos;
        this.side = side;
        addStandardInventorySlots(inventory, 8, 84);
    }

    public Direction getSide() {
        return side;
    }

    private @Nullable ConduitBlockEntity conduit() {
        return level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit ? conduit : null;
    }

    // Item conduits filter items; fluid and pressurized conduits filter fluids and gases.
    public ConduitType getConduitType() {
        ConduitBlockEntity conduit = conduit();
        return conduit != null ? conduit.getConduitType() : ConduitType.ITEM;
    }

    public boolean isItemFilter() {
        return getConduitType() == ConduitType.ITEM;
    }

    // The installed filter's settings, as last synced.
    public FilterSettings getSettings() {
        ConduitBlockEntity conduit = conduit();
        return conduit != null ? FilterSettings.of(conduit.getFilter(side)) : FilterSettings.DEFAULT;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        ConduitBlockEntity conduit = conduit();
        if (conduit == null || !conduit.hasFilter(side)) {
            return false;
        }
        FilterSettings settings = FilterSettings.of(conduit.getFilter(side));
        FilterSettings updated = apply(settings, buttonId);
        if (updated != null && !updated.equals(settings)) {
            ItemStack filter = conduit.getFilter(side).copy();
            filter.set(ModDataComponents.CONDUIT_FILTER.get(), updated);
            conduit.setFilter(side, filter);
        }
        return updated != null;
    }

    private @Nullable FilterSettings apply(FilterSettings settings, int buttonId) {
        if (buttonId >= 0 && buttonId < FilterSettings.SIZE) {
            FilterSettings.Entry entry = entryFromCarried(getCarried());
            return entry == null ? null : settings.withEntry(buttonId, entry);
        }
        if (buttonId >= CHIP_NEXT && buttonId < CHIP_NEXT + FilterSettings.SIZE) {
            int slot = buttonId - CHIP_NEXT;
            return settings.withEntry(slot, settings.entry(slot).cycleTag(1));
        }
        if (buttonId >= CHIP_PREVIOUS && buttonId < CHIP_PREVIOUS + FilterSettings.SIZE) {
            int slot = buttonId - CHIP_PREVIOUS;
            return settings.withEntry(slot, settings.entry(slot).cycleTag(-1));
        }
        return switch (buttonId) {
            case BUTTON_LIST_MODE -> settings.withDeny(!settings.deny());
            case BUTTON_COMPONENTS -> isItemFilter() ? settings.withIgnoreComponents(!settings.ignoreComponents()) : null;
            case BUTTON_DIRECTION -> settings.withFlow(settings.flow().next());
            default -> null;
        };
    }

    // What clicking a ghost slot with this carried stack sets it to: EMPTY for an empty hand, the item on an item
    // conduit, or on a fluid or pressurized conduit the fluid or gas it holds (null, ignoring the click, if it
    // holds none, or the wrong kind for this conduit).
    private FilterSettings.@Nullable Entry entryFromCarried(ItemStack carried) {
        if (carried.isEmpty()) {
            return FilterSettings.Entry.EMPTY;
        }
        ConduitType type = getConduitType();
        if (type == ConduitType.ITEM) {
            return FilterSettings.Entry.of(carried);
        }
        Fluid fluid = fluidIn(carried);
        if (fluid == Fluids.EMPTY || Gases.isGas(fluid) != (type == ConduitType.GAS)) {
            return null;
        }
        return FilterSettings.Entry.of(fluid);
    }

    // The fluid in a bucket, Canister or Gas Cartridge, or anything else with a fluid handler.
    public static Fluid fluidIn(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem bucket && bucket.getContent() != Fluids.EMPTY) {
            return bucket.getContent();
        }
        if (PortableStorageItem.is(stack, PortableStorageItem.Kind.CANISTER) || PortableStorageItem.is(stack, PortableStorageItem.Kind.GAS_CARTRIDGE)) {
            FluidStack held = PortableStorageItem.fluid(stack);
            return held.isEmpty() ? Fluids.EMPTY : held.getFluid();
        }
        ResourceHandler<FluidResource> handler = ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Fluid.ITEM);
        if (handler != null) {
            for (int index = 0; index < handler.size(); index++) {
                FluidResource resource = handler.getResource(index);
                if (!resource.isEmpty()) {
                    return resource.getFluid();
                }
            }
        }
        return Fluids.EMPTY;
    }

    // Nothing to shift-click into.
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        ConduitBlockEntity conduit = conduit();
        return conduit != null && !conduit.isRemoved() && conduit.hasFilter(side)
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE * MAX_DISTANCE;
    }
}
