/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.fusion;

import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.ItemAccessFluidHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.fusion.Tritium;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;

// A Tritium Canister's two tanks: its tritium (filled and drained, tritium only) and the Helium-3 it has decayed to (drained
// only), each moving at most the canister's rate a call.
public final class TritiumCanisterHandler {
    private TritiumCanisterHandler() {}

    public static ItemAccessFluidHandler tritium(ItemAccess access) {
        return new ItemAccessFluidHandler(access, ModDataComponents.FLUID_CONTENTS.get(), TritiumCanisterItem.CAPACITY) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return super.isValid(index, resource) && (resource.isEmpty() || Tritium.is(resource));
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return super.insert(index, resource, Math.min(amount, TritiumCanisterItem.RATE), transaction);
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return super.extract(index, resource, Math.min(amount, TritiumCanisterItem.RATE), transaction);
            }
        };
    }

    public static ItemAccessFluidHandler helium3(ItemAccess access) {
        return new ItemAccessFluidHandler(access, ModDataComponents.HELIUM_3_CONTENTS.get(), TritiumCanisterItem.CAPACITY) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return super.isValid(index, resource) && (resource.isEmpty() || resource.is(ModFluids.HELIUM_3.get()));
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return 0;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return super.extract(index, resource, Math.min(amount, TritiumCanisterItem.RATE), transaction);
            }
        };
    }
}
