/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.fusion;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.block.fusion.FusionModuleBlock;
import net.zagdrath.arcforge.blockentity.fusion.FusionReactorBlockEntity;
import net.zagdrath.arcforge.fusion.PlasmaSim;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Fusion Reactor's GUI: the control desk's three pages (the plasma, the plant, the controls and start-up checklist),
// Run and Fast Shutdown, the plant's tanks and FE, over the player inventory. Every reading comes through the data slots
// (see FusionReactorBlockEntity.dataValue): fixed-point ints.
public class FusionReactorMenu extends MachineMenu {
    public static final int DATA_STATUS = 0;
    public static final int DATA_FORMED = 1;
    public static final int DATA_REDSTONE_MODE = 2;
    public static final int DATA_SIDE_CONFIG = 3;
    public static final int DATA_RADIUS = 4;
    public static final int DATA_STELLARATOR = 5;
    public static final int DATA_TEMPERATURE = 6;    // keV x 10
    public static final int DATA_DENSITY = 7;        // 10^20 m^-3 x 1000
    public static final int DATA_CURRENT = 8;        // MA x 100
    public static final int DATA_FIELD = 9;          // T x 100
    public static final int DATA_FUSION = 10;        // MW x 10
    public static final int DATA_HEATING = 11;       // MW x 10
    public static final int DATA_Q = 12;             // x 100
    public static final int DATA_TAU = 13;           // ms
    public static final int DATA_BETA = 14;          // beta_N x 100
    public static final int DATA_GREENWALD = 15;     // of the density limit x 1000
    public static final int DATA_Q95 = 16;           // x 100
    public static final int DATA_POSITION = 17;      // of the vertical limit x 1000 (signed)
    public static final int DATA_PULSE_LEFT = 18;    // s, -1 steady
    public static final int DATA_NEUTRON = 19;       // MW x 10
    public static final int DATA_ALPHA = 20;         // MW x 10
    public static final int DATA_RADIATED = 21;      // MW x 10
    public static final int DATA_IMPURITY = 22;      // x 10000
    public static final int DATA_FLAGS = 23;         // FLAG_*
    public static final int DATA_EVENT = 24;         // PlasmaSim.Event
    public static final int DATA_VACUUM = 25;        // log10(atm) x 100
    public static final int DATA_COIL_TEMP = 26;     // K x 10
    public static final int DATA_FIELD_TARGET = 27;  // T x 100
    public static final int DATA_CS_CHARGE = 28;     // x 1000
    public static final int DATA_ENERGY = 29;
    public static final int DATA_ENERGY_CAPACITY = 30;
    public static final int DATA_WATER = 31;
    public static final int DATA_HOT_COOLANT = 32;
    public static final int DATA_LEAD_LITHIUM = 33;
    public static final int DATA_HOT_LEAD_LITHIUM = 34;
    public static final int DATA_LIQUID_HELIUM = 35;
    public static final int DATA_LIQUID_NITROGEN = 36;
    public static final int DATA_TANK = 37;
    public static final int DATA_WALL_TEMP = 38;     // °C
    public static final int DATA_BLANKET_TEMP = 39;  // °C
    public static final int DATA_FW_WEAR = 40;       // x 1000
    public static final int DATA_DIV_WEAR = 41;
    public static final int DATA_BLANKET_WEAR = 42;
    public static final int DATA_COIL_DAMAGE = 43;
    public static final int DATA_TBR = 44;           // x 1000
    public static final int DATA_BURN = 45;          // mB/s x 1000
    public static final int DATA_BRED = 46;          // mB/s x 1000
    public static final int DATA_HEATING_SET = 47;   // MW
    public static final int DATA_DENSITY_SET = 48;   // %
    public static final int DATA_CURRENT_SET = 49;   // %
    public static final int DATA_FIELD_SET = 50;     // %
    public static final int DATA_FUEL_MODE = 51;
    public static final int DATA_CHECKLIST = 52;     // CHECK_* bits
    public static final int DATA_HEAT_AVAILABLE = 53; // MW x 10
    public static final int DATA_ALARMS = 54;
    public static final int DATA_SILENCED = 55;
    public static final int DATA_THERMAL = 56;       // HU/t
    public static final int DATA_PUMPING = 57;       // x 1000
    public static final int DATA_HISTORY_HEAD = 58;
    public static final int DATA_MAX_CURRENT = 59;   // MA x 100
    public static final int DATA_DENSITY_LIMIT = 60; // 10^20 m^-3 x 1000
    public static final int DATA_LEAK = 61;          // x 1000
    public static final int DATA_QUENCH_UNITS = 62;
    public static final int DATA_FUEL = 63;          // mB/s x 10
    public static final int DATA_MODULES = 64;       // 4 bits per FusionModuleBlock.Kind
    public static final int DATA_HELIUM_LOOP = 65;   // 1 while the wall is helium-cooled
    public static final int DATA_ENERGY_USE = 66;    // FE/t, averaged over the last second
    public static final int DATA_HISTORY = 70;       // 3 series x HISTORY
    public static final int DATA_VALUES = DATA_HISTORY + 3 * FusionReactorBlockEntity.HISTORY;

    public static final int FLAG_ALIVE = 1, FLAG_IGNITED = 2, FLAG_RUN = 4, FLAG_REPEAT = 8, FLAG_ENERGISED = 16, FLAG_DIAGNOSTICS = 32,
            FLAG_WANTED = 64;
    // The start-up checklist, in order.
    public static final int CHECK_VACUUM = 1, CHECK_COLD = 2, CHECK_FIELD = 4, CHECK_SOLENOID = 8, CHECK_HEATING = 16, CHECK_FUEL = 32;
    public static final int CHECKS = 6;

    // Layout (300x270): the desk across the top (its screen 7..292 x 15..148), the input tanks left of the player
    // inventory and the outputs and FE right of it, the inventory at the bottom.
    public static final int WIDTH = 300, HEIGHT = 270;
    public static final int INVENTORY_X = 70, INVENTORY_Y = 188;

    // Buttons (the machines' 200s): Run (toggle) 200, Fast Shutdown 201, Repeat 202, fuel mode 203, Silence 204; then each
    // setpoint (heating, density, current, field) down and up a step: 205 + 2s + {0 down, 1 up}, and a big step: 213 + 2s +
    // {0, 1}.
    public static final int RUN = 200, FAST_SHUTDOWN = 201, REPEAT = 202, FUEL = 203, SILENCE = 204, STEP = 205, BIG_STEP = 213, LAST = 220;
    public static final int SETPOINTS = 4;
    private static final int[] SMALL = { 1, 1, 1, 1 }, BIG = { 10, 5, 10, 10 };

    public FusionReactorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), FusionReactorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public FusionReactorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.FUSION_REACTOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.FUSION_REACTOR_CONTROLLER.get());
        finish(inventory, INVENTORY_X, INVENTORY_Y);
    }

    @Override
    protected int statusIndex() {
        return DATA_STATUS;
    }

    @Override
    protected int redstoneIndex() {
        return DATA_REDSTONE_MODE;
    }

    @Override
    protected int sidesIndex() {
        return DATA_SIDE_CONFIG;
    }

    public static int stepButton(int setpoint, boolean up, boolean big) {
        return (big ? BIG_STEP : STEP) + setpoint * 2 + (up ? 1 : 0);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId < RUN || buttonId > LAST) {
            return super.clickMenuButton(player, buttonId);
        }
        access.execute((level, at) -> {
            if (level.getBlockEntity(at) instanceof FusionReactorBlockEntity reactor) {
                apply(reactor, buttonId);
            }
        });
        return true;
    }

    public static void apply(FusionReactorBlockEntity reactor, int id) {
        switch (id) {
            case RUN -> reactor.setRun(!reactor.isRunning());
            case FAST_SHUTDOWN -> reactor.fastShutdown();
            case REPEAT -> reactor.setRepeat(!reactor.isRepeat());
            case FUEL -> reactor.setFuelMode(reactor.getFuelMode() == FusionReactorBlockEntity.FuelMode.DT ? FusionReactorBlockEntity.FuelMode.DHE3
                    : FusionReactorBlockEntity.FuelMode.DT);
            case SILENCE -> reactor.silence();
            default -> {
                boolean big = id >= BIG_STEP;
                int offset = id - (big ? BIG_STEP : STEP);
                int setpoint = offset / 2;
                int by = (offset % 2 == 1 ? 1 : -1) * (big ? BIG[setpoint] : SMALL[setpoint]);
                switch (setpoint) {
                    case 0 -> reactor.setHeatingSetpoint(reactor.getHeatingSetpoint() + by);
                    case 1 -> reactor.setDensitySetpoint(reactor.getDensitySetpoint() + by);
                    case 2 -> reactor.setCurrentSetpoint(reactor.getCurrentSetpoint() + by);
                    default -> reactor.setFieldSetpoint(reactor.getFieldSetpoint() + by);
                }
            }
        }
    }

    // --- Readings ---

    public int get(int index) {
        return value(index);
    }

    public boolean isFormed() {
        return value(DATA_FORMED) != 0;
    }

    public boolean flag(int bit) {
        return (value(DATA_FLAGS) & bit) != 0;
    }

    public boolean check(int bit) {
        return (value(DATA_CHECKLIST) & bit) != 0;
    }

    public int radius() {
        return value(DATA_RADIUS);
    }

    public boolean stellarator() {
        return value(DATA_STELLARATOR) != 0;
    }

    public double scaled(int index, double scale) {
        return value(index) / scale;
    }

    public PlasmaSim.Event lastEvent() {
        PlasmaSim.Event[] values = PlasmaSim.Event.values();
        return values[Math.max(0, Math.min(values.length - 1, value(DATA_EVENT)))];
    }

    public FusionReactorBlockEntity.FuelMode fuelMode() {
        return value(DATA_FUEL_MODE) == 1 ? FusionReactorBlockEntity.FuelMode.DHE3 : FusionReactorBlockEntity.FuelMode.DT;
    }

    public int modules(FusionModuleBlock.Kind kind) {
        return value(DATA_MODULES) >> (kind.ordinal() * 4) & 15;
    }

    public int alarms() {
        return value(DATA_ALARMS);
    }

    public int silenced() {
        return value(DATA_SILENCED);
    }

    // A trend reading, ago seconds back (0 the newest): series 0 fusion power (MW), 1 temperature (keV x 10), 2 density
    // (x 100).
    public int history(int series, int ago) {
        int head = value(DATA_HISTORY_HEAD);
        int index = Math.floorMod(head - 1 - ago, FusionReactorBlockEntity.HISTORY);
        return value(DATA_HISTORY + series * FusionReactorBlockEntity.HISTORY + index);
    }
}
