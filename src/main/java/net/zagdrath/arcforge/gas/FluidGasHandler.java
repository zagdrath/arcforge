/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gas;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.api.gas.Gas;
import net.zagdrath.arcforge.api.gas.GasHandler;
import net.zagdrath.arcforge.api.gas.GasRegistry;
import net.zagdrath.arcforge.api.gas.GasTank;
import net.zagdrath.arcforge.transfer.Transactions;

// The gas API's view of a fluid handler: only the indices that can hold a gas, as gas tanks, and only gases moving
// through them. Every transfer goes to the handler itself, so its own rules (filters, roles, rates) decide.
public final class FluidGasHandler implements GasHandler {
    private final ResourceHandler<FluidResource> handler;
    // Gas tank -> handler index.
    private final int[] tanks;
    private final IntPredicate canInsert;
    private final IntPredicate canExtract;

    private FluidGasHandler(ResourceHandler<FluidResource> handler, int[] tanks, IntPredicate canInsert, IntPredicate canExtract) {
        this.handler = handler;
        this.tanks = tanks;
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }

    // A storage handler (a cylinder, an item): its gas tanks are the indices that take some gas, and each both takes
    // and gives. Null if none takes a gas.
    public static @Nullable GasHandler of(@Nullable ResourceHandler<FluidResource> handler) {
        if (handler == null) {
            return null;
        }
        return of(handler, index -> holdsGas(handler, index), index -> true, index -> true);
    }

    // holdsGas: which handler indices are gas tanks (decided now, once). canInsert/canExtract: by handler index.
    public static @Nullable GasHandler of(ResourceHandler<FluidResource> handler, IntPredicate holdsGas, IntPredicate canInsert, IntPredicate canExtract) {
        int[] tanks = IntStream.range(0, handler.size()).filter(holdsGas).toArray();
        return tanks.length == 0 ? null : new FluidGasHandler(handler, tanks, canInsert, canExtract);
    }

    // Whether the handler's index could ever hold some gas (contents aside).
    public static boolean holdsGas(ResourceHandler<FluidResource> handler, int index) {
        for (Gas gas : GasHandlers.gases()) {
            if (handler.isValid(index, gas.toResource())) {
                return true;
            }
        }
        return false;
    }

    private int index(int tank) {
        return tank >= 0 && tank < tanks.length ? tanks[tank] : -1;
    }

    @Override
    public int tankCount() {
        return tanks.length;
    }

    @Override
    public GasTank tank(int tank) {
        int index = index(tank);
        if (index < 0) {
            return GasTank.NONE;
        }
        FluidResource resource = handler.getResource(index);
        Optional<Gas> gas = resource.isEmpty() ? Optional.empty() : GasRegistry.of(resource.getFluid());
        int amount = gas.isPresent() ? handler.getAmountAsInt(index) : 0;
        // A tank's capacity for its own gas, or for none when it's empty or holds a liquid.
        int capacity = handler.getCapacityAsInt(index, gas.isPresent() ? resource : FluidResource.EMPTY);
        return new GasTank(gas, amount, capacity, canInsert.test(index), canExtract.test(index));
    }

    @Override
    public List<GasTank> tanks() {
        List<GasTank> list = new ArrayList<>(tanks.length);
        for (int tank = 0; tank < tanks.length; tank++) {
            list.add(tank(tank));
        }
        return List.copyOf(list);
    }

    @Override
    public boolean accepts(int tank, Gas gas) {
        int index = index(tank);
        return index >= 0 && canInsert.test(index) && handler.isValid(index, gas.toResource());
    }

    @Override
    public int insert(int tank, Gas gas, int amount, boolean simulate) {
        int index = index(tank);
        if (index < 0 || amount <= 0 || !canInsert.test(index)) {
            return 0;
        }
        FluidResource resource = gas.toResource();
        return Transactions.transact(simulate, tx -> handler.insert(index, resource, amount, tx));
    }

    @Override
    public int insert(Gas gas, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        // The handler's own routing (a machine sorts each gas into its tank); only gas tanks can take a gas.
        FluidResource resource = gas.toResource();
        return Transactions.transact(simulate, tx -> handler.insert(resource, amount, tx));
    }

    @Override
    public int extract(int tank, Gas gas, int amount, boolean simulate) {
        int index = index(tank);
        if (index < 0 || amount <= 0 || !canExtract.test(index)) {
            return 0;
        }
        FluidResource resource = gas.toResource();
        return Transactions.transact(simulate, tx -> handler.extract(index, resource, amount, tx));
    }

    @Override
    public int extract(Gas gas, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        FluidResource resource = gas.toResource();
        return Transactions.transact(simulate, tx -> {
            int extracted = 0;
            for (int tank = 0; tank < tanks.length && extracted < amount; tank++) {
                if (canExtract.test(tanks[tank])) {
                    extracted += handler.extract(tanks[tank], resource, amount - extracted, tx);
                }
            }
            return extracted;
        });
    }
}
