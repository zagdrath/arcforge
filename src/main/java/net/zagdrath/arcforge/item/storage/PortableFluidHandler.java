/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.storage;

import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.ItemAccessFluidHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.steam.Gases;

// The fluid capability of a Canister (liquids only) or a Gas Cartridge (gases only): one fluid at a time,
// moving at most the item's rate per call.
public class PortableFluidHandler extends ItemAccessFluidHandler {
    private final boolean gas;
    private final int rate;

    public PortableFluidHandler(ItemAccess itemAccess, PortableStorageItem item) {
        super(itemAccess, ModDataComponents.FLUID_CONTENTS.get(), item.capacity());
        this.gas = item.kind() == PortableStorageItem.Kind.GAS_CARTRIDGE;
        this.rate = item.rate();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return super.isValid(index, resource) && (resource.isEmpty() || Gases.isGas(resource) == gas);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return super.insert(index, resource, Math.min(amount, rate), transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return super.extract(index, resource, Math.min(amount, rate), transaction);
    }
}
