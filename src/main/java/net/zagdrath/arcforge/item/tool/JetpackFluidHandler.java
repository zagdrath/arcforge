/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.ItemAccessFluidHandler;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A Jetpack's tank as an item fluid handler: jetpack fuels only (the handler already keeps it to one fluid at a
// time). It can be drained too, so a cylinder or conduit can empty it.
public class JetpackFluidHandler extends ItemAccessFluidHandler {
    public JetpackFluidHandler(ItemAccess itemAccess, JetpackItem item) {
        super(itemAccess, ModDataComponents.FLUID_CONTENTS.get(), item.capacity());
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return super.isValid(index, resource) && (resource.isEmpty() || JetpackFuel.of(resource.getFluid()) != null);
    }
}
