/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.api.machine.status.CompletedOperation;
import net.zagdrath.arcforge.api.machine.MachineControl;
import net.zagdrath.arcforge.api.machine.status.MachineListener;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;

// What the machine control API (docs/API.md) keeps for one machine: the enable switch and the statistics totals, saved
// with it, and what lives only while it's loaded: the listeners, the last minute's operations and the last status seen.
// Machines report their work through completed() (one operation) and the produced/consumed methods (continuous flows).
public final class MachineControlState {
    // The rolling rate's window: a minute.
    private static final int RATE_WINDOW = 1200;
    // How often the loaded and running times mark the machine to be saved.
    private static final int SAVE_INTERVAL = 100;

    private final Runnable onChanged;
    private boolean enabled = true;
    private long operations;
    private long itemsProduced;
    private long itemsConsumed;
    private long fluidProduced;
    private long fluidConsumed;
    private long uptime;
    private long loaded;

    // Not saved.
    private long sessionTicks;
    private final ArrayDeque<Long> recent = new ArrayDeque<>();
    private final List<MachineListener> listeners = new CopyOnWriteArrayList<>();
    private @Nullable MachineStatus lastStatus;
    private @Nullable MachineControl control;

    public MachineControlState(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    // --- The enable switch ---

    public boolean isEnabled() {
        return enabled;
    }

    // Returns whether it changed.
    public boolean setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return false;
        }
        this.enabled = enabled;
        onChanged.run();
        return true;
    }

    // --- Reporting work ---

    // One operation finished, making these items and fluids and using up this much.
    public void completed(List<ItemStack> produced, List<FluidStack> fluids, long itemsUsed, long fluidUsed) {
        operations++;
        for (ItemStack stack : produced) {
            itemsProduced += stack.getCount();
        }
        for (FluidStack stack : fluids) {
            fluidProduced += stack.getAmount();
        }
        itemsConsumed += itemsUsed;
        fluidConsumed += fluidUsed;
        recent.addLast(sessionTicks);
        onChanged.run();
        if (!listeners.isEmpty() && control != null) {
            List<ItemStack> items = produced.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
            List<FluidStack> fluidCopies = fluids.stream().filter(stack -> !stack.isEmpty()).map(FluidStack::copy).toList();
            CompletedOperation operation = new CompletedOperation(items, fluidCopies, itemsUsed, fluidUsed);
            MachineControl machine = control;
            fire(listener -> listener.onOperationCompleted(machine, operation));
        }
    }

    // One operation finished, making one stack (or nothing) from this many items.
    public void completed(ItemStack produced, long itemsUsed) {
        completed(produced.isEmpty() ? List.of() : List.of(produced), List.of(), itemsUsed, 0);
    }

    // Continuous work, with no operation: amounts made or used this tick.
    public void producedItems(long count) {
        itemsProduced += count;
    }

    public void consumedItems(long count) {
        itemsConsumed += count;
    }

    public void producedFluid(long amount) {
        fluidProduced += amount;
    }

    public void consumedFluid(long amount) {
        fluidConsumed += amount;
    }

    // --- Read by the API ---

    public long operations() {
        return operations;
    }

    public long itemsProduced() {
        return itemsProduced;
    }

    public long itemsConsumed() {
        return itemsConsumed;
    }

    public long fluidProduced() {
        return fluidProduced;
    }

    public long fluidConsumed() {
        return fluidConsumed;
    }

    public long uptime() {
        return uptime;
    }

    public long loaded() {
        return loaded;
    }

    // Operations in the last minute loaded, scaled up to a minute while it's been loaded for less (at least a second).
    public double operationsPerMinute() {
        trim();
        long window = Math.max(20, Math.min(RATE_WINDOW, sessionTicks));
        return recent.size() * (double) RATE_WINDOW / window;
    }

    private void trim() {
        while (!recent.isEmpty() && recent.peekFirst() <= sessionTicks - RATE_WINDOW) {
            recent.removeFirst();
        }
    }

    // --- Listeners and the per-tick check (MachineControlTracker) ---

    @Nullable MachineControl control() {
        return control;
    }

    void setControl(MachineControl control) {
        this.control = control;
    }

    void addListener(MachineListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    void removeListener(MachineListener listener) {
        listeners.remove(listener);
    }

    // Once a tick while loaded, with the machine's status this tick: counts loaded and running time and tells listeners
    // about a change.
    void tick(MachineControl machine, MachineStatus status) {
        sessionTicks++;
        loaded++;
        if (status == MachineStatus.RUNNING) {
            uptime++;
        }
        if (loaded % SAVE_INTERVAL == 0) {
            onChanged.run();
        }
        trim();
        MachineStatus previous = lastStatus;
        lastStatus = status;
        if (previous != null && previous != status && !listeners.isEmpty()) {
            fire(listener -> listener.onStatusChanged(machine, previous, status));
            if (status == MachineStatus.FAULT) {
                var reason = machine.statusReason();
                fire(listener -> listener.onFault(machine, reason));
            }
        }
    }

    // A listener's exception is the consumer's bug: log it, and don't let it reach the machine.
    private void fire(Consumer<MachineListener> event) {
        for (MachineListener listener : listeners) {
            try {
                event.accept(listener);
            } catch (RuntimeException e) {
                Arcforge.LOGGER.error("A machine control listener ({}) threw", listener.getClass().getName(), e);
            }
        }
    }

    // A structure's controller moved to another block (a cube array grown into a bigger box): this one carries on with
    // the old one's switch and totals, and the old one starts afresh.
    public void takeOver(MachineControlState old) {
        enabled = old.enabled;
        operations = old.operations;
        itemsProduced = old.itemsProduced;
        itemsConsumed = old.itemsConsumed;
        fluidProduced = old.fluidProduced;
        fluidConsumed = old.fluidConsumed;
        uptime = old.uptime;
        loaded = old.loaded;
        old.enabled = true;
        old.operations = old.itemsProduced = old.itemsConsumed = old.fluidProduced = old.fluidConsumed = old.uptime = old.loaded = 0;
        onChanged.run();
        old.onChanged.run();
    }

    // --- Saving ---

    public void save(ValueOutput output) {
        ValueOutput child = output.child("machine_control");
        child.putBoolean("enabled", enabled);
        child.putLong("operations", operations);
        child.putLong("items_produced", itemsProduced);
        child.putLong("items_consumed", itemsConsumed);
        child.putLong("fluid_produced", fluidProduced);
        child.putLong("fluid_consumed", fluidConsumed);
        child.putLong("uptime", uptime);
        child.putLong("loaded", loaded);
    }

    public void load(ValueInput input) {
        ValueInput child = input.childOrEmpty("machine_control");
        enabled = child.getBooleanOr("enabled", true);
        operations = child.getLongOr("operations", 0);
        itemsProduced = child.getLongOr("items_produced", 0);
        itemsConsumed = child.getLongOr("items_consumed", 0);
        fluidProduced = child.getLongOr("fluid_produced", 0);
        fluidConsumed = child.getLongOr("fluid_consumed", 0);
        uptime = child.getLongOr("uptime", 0);
        loaded = child.getLongOr("loaded", 0);
    }
}
