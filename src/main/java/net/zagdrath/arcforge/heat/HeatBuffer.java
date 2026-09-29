/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// A machine's store of heat (HU). Its temperature rises linearly with how full it is, from ambient
// when empty to the machine's maximum when full. A producer that is making heat right now (a fire
// burning, lava draining) sets a source temperature, and is at least that hot however full it is, so
// its heat flows out at once instead of waiting for the buffer to fill. Oxy-fuel lifts a buffer's ceiling above its
// maximum (setCeiling): it then holds more heat, at the same °C per HU, up to the ceiling's temperature. The owning machine adds and removes heat directly;
// other blocks see one of the one-way views (output() for producers, input() for consumers).
public class HeatBuffer {
    public static final int AMBIENT_CELSIUS = 20;

    private final int capacity;
    private final int maxCelsius;
    private final Runnable onChanged;
    private int stored;
    // How hot it may get: maxCelsius, or higher while a machine runs on oxy-fuel. Not saved: set every tick.
    private int ceilingCelsius;
    // Not saved: the owning machine sets it every tick.
    private int sourceCelsius = AMBIENT_CELSIUS;

    public HeatBuffer(int capacity, int maxCelsius, Runnable onChanged) {
        this.capacity = Math.max(1, capacity);
        this.maxCelsius = maxCelsius;
        this.onChanged = onChanged;
        this.ceilingCelsius = maxCelsius;
    }

    public static int temperature(int stored, int capacity, int maxCelsius) {
        if (capacity <= 0) {
            return AMBIENT_CELSIUS;
        }
        return AMBIENT_CELSIUS + (int) ((long) (maxCelsius - AMBIENT_CELSIUS) * Mth.clamp(stored, 0, capacity) / capacity);
    }

    public int getStored() {
        return stored;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getMaxCelsius() {
        return maxCelsius;
    }

    public int getTemperature() {
        if (maxCelsius <= AMBIENT_CELSIUS) {
            return Math.max(sourceCelsius, AMBIENT_CELSIUS);
        }
        // Linear in what it holds, past maxCelsius when the ceiling let it fill further.
        return Math.max(sourceCelsius, AMBIENT_CELSIUS + (int) ((long) (maxCelsius - AMBIENT_CELSIUS) * Math.max(0, stored) / capacity));
    }

    // Lets the buffer run hotter than its maximum (oxy-fuel), or back to it; never below maxCelsius.
    public void setCeiling(int celsius) {
        ceilingCelsius = Math.max(maxCelsius, celsius);
    }

    public int getCeiling() {
        return ceilingCelsius;
    }

    // The most heat it may hold: its capacity, or more when the ceiling is above maxCelsius.
    public int getLimit() {
        return ceilingCelsius <= maxCelsius ? capacity : linearStored(ceilingCelsius);
    }

    // How much heat it holds at this temperature on its linear scale, uncapped.
    private int linearStored(int celsius) {
        if (maxCelsius <= AMBIENT_CELSIUS) {
            return capacity;
        }
        return (int) Math.min(Integer.MAX_VALUE, (long) capacity * Math.max(0, celsius - AMBIENT_CELSIUS) / (maxCelsius - AMBIENT_CELSIUS));
    }

    // Whether the machine is making heat this tick: while it is, the buffer is at its maximum temperature.
    public void setProducing(boolean producing) {
        sourceCelsius = producing ? maxCelsius : AMBIENT_CELSIUS;
    }

    // The machine is making heat this tick at this temperature (capped at the maximum).
    public void setProducingAt(int celsius) {
        sourceCelsius = Math.min(celsius, ceilingCelsius);
    }

    // How much heat the buffer holds at this temperature.
    public int storedAt(int celsius) {
        if (celsius >= ceilingCelsius) {
            return getLimit();
        }
        return linearStored(celsius);
    }

    // The least heat the buffer can hold and still be at least this hot (storedAt rounds down).
    public int minStoredAt(int celsius) {
        if (celsius >= ceilingCelsius) {
            return getLimit();
        }
        long span = maxCelsius - AMBIENT_CELSIUS;
        return (int) (((long) capacity * Math.max(0, celsius - AMBIENT_CELSIUS) + span - 1) / span);
    }

    public int getRoom() {
        return Math.max(0, getLimit() - stored);
    }

    public boolean isFull() {
        return stored >= getLimit();
    }

    // Adds up to the given heat, returning how much fit.
    public int add(int amount) {
        int added = Math.min(Math.max(0, amount), getRoom());
        if (added > 0) {
            stored += added;
            onChanged.run();
        }
        return added;
    }

    // Removes up to the given heat, returning how much was taken.
    public int remove(int amount) {
        int removed = Math.min(Math.max(0, amount), stored);
        if (removed > 0) {
            stored -= removed;
            onChanged.run();
        }
        return removed;
    }

    // What producers expose on their heat faces: heat can be drawn out but not put in.
    public HeatHandler output() {
        return new View(false, true, Integer.MAX_VALUE);
    }

    // What consumers expose on their heat faces: heat can be put in (up to maxPerCall at a time) but not drawn out.
    public HeatHandler input(int maxPerCall) {
        return new View(true, false, maxPerCall);
    }

    public void serialize(ValueOutput output) {
        output.putInt("heat", stored);
    }

    public void deserialize(ValueInput input) {
        stored = Mth.clamp(input.getIntOr("heat", 0), 0, capacity);
    }

    private class View implements HeatHandler {
        private final boolean canReceive;
        private final boolean canExtract;
        private final int maxPerCall;

        View(boolean canReceive, boolean canExtract, int maxPerCall) {
            this.canReceive = canReceive;
            this.canExtract = canExtract;
            this.maxPerCall = maxPerCall;
        }

        @Override
        public int getHeat() {
            return stored;
        }

        @Override
        public int getMaxHeat() {
            return capacity;
        }

        @Override
        public int getTemperature() {
            return HeatBuffer.this.getTemperature();
        }

        @Override
        public int receiveHeat(int amount, boolean simulate) {
            if (!canReceive) {
                return 0;
            }
            int accepted = Math.min(Math.min(Math.max(0, amount), maxPerCall), getRoom());
            return simulate ? accepted : add(accepted);
        }

        @Override
        public int extractHeat(int amount, boolean simulate) {
            if (!canExtract) {
                return 0;
            }
            int extracted = Math.min(Math.max(0, amount), stored);
            return simulate ? extracted : remove(extracted);
        }
    }
}
