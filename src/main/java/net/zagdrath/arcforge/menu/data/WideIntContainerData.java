package net.zagdrath.arcforge.menu.data;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

// Vanilla syncs ContainerData to the client as 16-bit shorts, which truncates FE values.
// This splits each logical int across two data slots (low and high 16 bits).
public abstract class WideIntContainerData implements ContainerData {
    private final int valueCount;

    protected WideIntContainerData(int valueCount) {
        this.valueCount = valueCount;
    }

    // Returns the logical value at the given index (server side).
    protected abstract int getValue(int index);

    @Override
    public int get(int dataId) {
        int value = getValue(dataId / 2);
        return dataId % 2 == 0 ? value & 0xFFFF : (value >>> 16) & 0xFFFF;
    }

    @Override
    public void set(int dataId, int value) {
        // Server-side values are computed from the block entity; nothing to set.
    }

    @Override
    public int getCount() {
        return valueCount * 2;
    }

    // Client-side container that receives the split values.
    public static ContainerData client(int valueCount) {
        return new SimpleContainerData(valueCount * 2);
    }

    // Reassembles a logical value from either side's container.
    public static int read(ContainerData data, int index) {
        return (data.get(index * 2) & 0xFFFF) | ((data.get(index * 2 + 1) & 0xFFFF) << 16);
    }
}
