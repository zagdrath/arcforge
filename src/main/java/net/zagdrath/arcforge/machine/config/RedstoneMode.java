package net.zagdrath.arcforge.machine.config;

import net.minecraft.network.chat.Component;

// How a machine reacts to a redstone signal.
public enum RedstoneMode {
    IGNORE("ignore"),
    HIGH("high"),
    LOW("low");

    private final String name;

    RedstoneMode(String name) {
        this.name = name;
    }

    public boolean canRun(boolean powered) {
        return switch (this) {
            case IGNORE -> true;
            case HIGH -> powered;
            case LOW -> !powered;
        };
    }

    public String getSerializedName() {
        return name;
    }

    public Component getDescription() {
        return Component.translatable("gui.arcforge.redstone." + name);
    }

    public static RedstoneMode byId(int id) {
        RedstoneMode[] values = values();
        return id >= 0 && id < values.length ? values[id] : IGNORE;
    }
}
