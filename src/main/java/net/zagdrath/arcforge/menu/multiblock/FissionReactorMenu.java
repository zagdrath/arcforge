/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.FissionReactorBlockEntity;
import net.zagdrath.arcforge.nuclear.reactor.ReactorAutopilot;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.nuclear.reactor.ReactorSim;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Fission Reactor's GUI: the control desk (the core's readings, the rod banks, the trend graphs, the core map), the
// protection page (trip points), the SCRAM and Reset buttons, and the nine rod input slots and nine output slots. Every
// reading comes through the data slots (see FissionReactorBlockEntity.dataValue): fixed-point ints.
public class FissionReactorMenu extends MachineMenu {
    public static final int DATA_STATUS = 0;
    public static final int DATA_FORMED = 1;
    public static final int DATA_REDSTONE_MODE = 2;
    public static final int DATA_SIDE_CONFIG = 3;
    public static final int DATA_THERMAL = 4;          // HU/t
    public static final int DATA_POWER_LOG = 5;        // log10(power fraction) x 1000
    public static final int DATA_K = 6;                // x 100000
    public static final int DATA_RHO = 7;              // x 1000000 (pcm x 10)
    public static final int DATA_PERIOD = 8;           // seconds x 10, 0 = none
    public static final int DATA_INSERTION = 9;        // 4 banks, x 1000
    public static final int DATA_TARGET = 13;          // 4 banks, x 1000
    public static final int DATA_FUEL_TEMP = 17;       // hottest, °C x 10
    public static final int DATA_CLAD_TEMP = 18;
    public static final int DATA_COOLANT_TEMP = 19;
    public static final int DATA_PRESSURE = 20;        // bar x 10
    public static final int DATA_XENON = 21;           // of full-power equilibrium, x 1000
    public static final int DATA_DECAY = 22;           // HU/t
    public static final int DATA_COOLANT = 23;         // mB in the vessel
    public static final int DATA_COOLANT_CAPACITY = 24;
    public static final int DATA_COOLANT_TYPE = 25;
    public static final int DATA_MODE = 26;
    public static final int DATA_TRIP = 27;
    public static final int DATA_SCRAMMED = 28;
    public static final int DATA_FLAGS = 29;           // trips on (bits 0-4), redstone rods, auto-unload, unloading, can refuel, damaged
    public static final int DATA_TRIP_POWER = 30;
    public static final int DATA_TRIP_PERIOD = 31;
    public static final int DATA_TRIP_FUEL = 32;
    public static final int DATA_TRIP_PRESSURE = 33;   // bar, absolute
    public static final int DATA_TRIP_FLOW = 34;
    public static final int DATA_RODS = 35;           // fuel rods loaded
    public static final int DATA_CHANNELS = 36;       // Fuel Channels, loaded or not
    public static final int DATA_CONTROL_RODS = 37;   // control rods loaded
    public static final int DATA_BURNUP = 38;          // mean, x 1000
    public static final int DATA_DOPPLER = 39;         // pcm/°C x 100
    public static final int DATA_VOID = 40;            // pcm per % void x 100
    public static final int DATA_FLOW = 41;            // x 1000
    public static final int DATA_PUMP = 42;            // %
    public static final int DATA_HOT_LEG = 43;
    public static final int DATA_STEAM = 44;
    public static final int DATA_INTAKE = 45;
    public static final int DATA_RATED = 46;           // HU/t at full power
    public static final int DATA_PEAKING = 47;         // x 100
    public static final int DATA_HISTORY_HEAD = 48;
    public static final int DATA_VOID_FRACTION = 49;   // x 1000
    // Safety: the alarms up and those silenced (ReactorAlarm bits), the containment (0 none, else its status + 1), the
    // Hydrogen in its air (% x 100), its air's radioactivity (mSv/h x 10), the Suppression Pool (blocks, °C x 10), Relief
    // Valves, ECCS units and their water, recombiners, the cladding oxidised and the coolant's activity (x 1000), a salt
    // core's fission products (x 1000), its Freeze Plug (0 none, 1 frozen, 2 melted), the air's volume, the salt's
    // fissile (pcm) and the vessel's burst pressure (bar x 10).
    public static final int DATA_ALARMS = 50;
    public static final int DATA_SILENCED = 51;
    public static final int DATA_CONTAINMENT = 52;
    public static final int DATA_HYDROGEN = 53;
    public static final int DATA_AIR_ACTIVITY = 54;
    public static final int DATA_POOL = 55;
    public static final int DATA_POOL_TEMP = 56;
    public static final int DATA_VALVES = 57;
    public static final int DATA_ECCS_UNITS = 58;
    public static final int DATA_ECCS_WATER = 59;
    public static final int DATA_RECOMBINERS = 60;
    public static final int DATA_OXIDISED = 61;
    public static final int DATA_COOLANT_ACTIVITY = 62;
    public static final int DATA_SALT_FISSION = 63;
    public static final int DATA_FREEZE_PLUG = 64;
    public static final int DATA_AIR_VOLUME = 65;
    public static final int DATA_SALT_FISSILE = 66;
    public static final int DATA_BURST_PRESSURE = 67;
    public static final int DATA_HISTORY = 70;         // 3 series x HISTORY
    public static final int DATA_CONTROL_CHANNELS = DATA_HISTORY + 3 * FissionReactorBlockEntity.HISTORY; // loaded or not
    // The core map: the core's size (see packMapSize), then each cell (see packCell), in the lattice's order (x fastest,
    // then z, then y from the bottom).
    public static final int DATA_MAP_SIZE = DATA_CONTROL_CHANNELS + 1;
    public static final int DATA_MAP = DATA_MAP_SIZE + 1;
    // The largest core inside a 7x9x7 vessel.
    public static final int MAP_CELLS = 5 * 7 * 5;
    // The Reactor Autopilot: -1 without its card, else its state (ReactorAutopilot.State) and goal (see packAutopilot).
    public static final int DATA_AUTOPILOT = DATA_MAP + MAP_CELLS;
    public static final int DATA_VALUES = DATA_AUTOPILOT + 1;

    // What a core map cell holds.
    public static final int CELL_COOLANT = 0, CELL_GRAPHITE = 1, CELL_STEEL = 2, CELL_CONTROL = 3, CELL_EMPTY_CHANNEL = 4, CELL_FUEL = 5, CELL_SALT = 6;

    public static final int FLAG_REDSTONE_RODS = 32, FLAG_AUTO_UNLOAD = 64, FLAG_UNLOADING = 128, FLAG_CAN_REFUEL = 256, FLAG_DAMAGED = 512;

    // Layout (300x288): the desk across the top; under it the rod input slots in a 3x3 grid over the player
    // inventory's left end and the output slots in a 3x3 grid over its right end; the player inventory at the bottom.
    public static final int WIDTH = 300, HEIGHT = 288;
    public static final int INVENTORY_X = 70, INVENTORY_Y = 206, INPUT_X = INVENTORY_X, OUTPUT_X = INVENTORY_X + 6 * 18, SLOTS_Y = 134;

    // Buttons (each machine's own range is the 200s; the 300s are the Security tab's): bank b down (out) or up (in) a
    // step (1%) or a big step (10%): 200 + b * 4 + {0 out, 1 in, 2 out big, 3 in big}; every bank at once the same:
    // 216..219; SCRAM 220; Reset 221; trip i on/off 222 + i; trip i down/up 227 + 2i / 228 + 2i; pump down/up 237/238;
    // Redstone Rods 239; auto-unload 240; unload every rod 241; bank b down or up a fine step (0.1%): 242 + b * 2 + {0 out,
    // 1 in}, every bank at once 250/251; silence the alarms 252; bring the most-burned third out 253. From the core map, a
    // fuel rod to bring out (or not): 1000 + its cell.
    public static final int BANK_FIRST = 200, ALL_BANKS = 216, SCRAM = 220, RESET = 221, TRIP_TOGGLE = 222, TRIP_STEP = 227, PUMP_DOWN = 237,
            PUMP_UP = 238, REDSTONE_RODS = 239, AUTO_UNLOAD = 240, UNLOAD_ALL = 241, FINE = 242, SILENCE = 252, PULL_THIRD = 253, LAST = 253,
            PULL_CELL = 1_000;

    public FissionReactorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), FissionReactorBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public FissionReactorMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.FISSION_REACTOR.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.REACTOR_CONTROLLER.get());
        for (int i = 0; i < FissionReactorBlockEntity.INPUT_SLOTS; i++) {
            addMachineSlot(i, INPUT_X + i % 3 * 18, SLOTS_Y + i / 3 * 18);
        }
        for (int i = 0; i < FissionReactorBlockEntity.OUTPUT_SLOTS; i++) {
            addMachineSlot(FissionReactorBlockEntity.INPUT_SLOTS + i, OUTPUT_X + i % 3 * 18, SLOTS_Y + i / 3 * 18);
        }
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

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        boolean pull = buttonId >= PULL_CELL && buttonId < PULL_CELL + MAP_CELLS;
        if (!pull && (buttonId < BANK_FIRST || buttonId > LAST)) {
            return super.clickMenuButton(player, buttonId);
        }
        access.execute((level, at) -> {
            if (level.getBlockEntity(at) instanceof FissionReactorBlockEntity reactor) {
                apply(reactor, buttonId);
            }
        });
        return true;
    }

    public static void apply(FissionReactorBlockEntity reactor, int id) {
        if (id >= PULL_CELL) {
            reactor.togglePull(id - PULL_CELL);
        } else if (id == PULL_THIRD) {
            reactor.pullBurnedThird();
        } else if (id == SILENCE) {
            reactor.silence();
        } else if (id >= FINE) {
            int bank = (id - FINE) / 2;
            double by = (id - FINE) % 2 == 1 ? 0.001 : -0.001;
            for (int b = 0; b < ReactorSim.BANKS; b++) {
                if (bank == b || bank == ReactorSim.BANKS) {
                    reactor.moveBank(b, by);
                }
            }
        } else if (id < ALL_BANKS) {
            int bank = (id - BANK_FIRST) / 4, step = (id - BANK_FIRST) % 4;
            reactor.moveBank(bank, stepSize(step));
        } else if (id < SCRAM) {
            for (int bank = 0; bank < ReactorSim.BANKS; bank++) {
                reactor.moveBank(bank, stepSize(id - ALL_BANKS));
            }
        } else if (id == SCRAM) {
            reactor.scram();
        } else if (id == RESET) {
            reactor.resetTrip();
        } else if (id < TRIP_STEP) {
            reactor.toggleTrip(id - TRIP_TOGGLE);
        } else if (id < PUMP_DOWN) {
            reactor.stepTrip((id - TRIP_STEP) / 2, (id - TRIP_STEP) % 2 == 1);
        } else if (id == PUMP_DOWN || id == PUMP_UP) {
            reactor.stepPump(id == PUMP_UP);
        } else if (id == REDSTONE_RODS) {
            reactor.toggleRedstoneRods();
        } else if (id == AUTO_UNLOAD) {
            reactor.toggleAutoUnload();
        } else if (id == UNLOAD_ALL) {
            reactor.unloadAll();
        }
    }

    // A step's change in insertion: out (negative) or in, small or big.
    private static double stepSize(int step) {
        return switch (step) {
            case 0 -> -0.01;
            case 1 -> 0.01;
            case 2 -> -0.10;
            default -> 0.10;
        };
    }

    public static int bankButton(int bank, boolean in, boolean big) {
        return BANK_FIRST + bank * 4 + (big ? 2 : 0) + (in ? 1 : 0);
    }

    public static int allBanksButton(boolean in, boolean big) {
        return ALL_BANKS + (big ? 2 : 0) + (in ? 1 : 0);
    }

    // A fine step (0.1%) of bank b, or of every bank for b == ReactorSim.BANKS.
    public static int fineButton(int bank, boolean in) {
        return FINE + bank * 2 + (in ? 1 : 0);
    }

    // --- Readings ---

    public int get(int index) {
        return value(index);
    }

    public int alarms() {
        return value(DATA_ALARMS);
    }

    public int silenced() {
        return value(DATA_SILENCED);
    }

    public boolean isFormed() {
        return value(DATA_FORMED) != 0;
    }

    public int thermal() {
        return value(DATA_THERMAL);
    }

    public double power() {
        return Math.pow(10.0, value(DATA_POWER_LOG) / 1000.0);
    }

    public double k() {
        return value(DATA_K) / 100_000.0;
    }

    public double rho() {
        return value(DATA_RHO) / 1.0E6;
    }

    // Seconds, or +inf when steady.
    public double period() {
        int period = value(DATA_PERIOD);
        return period == 0 ? Double.POSITIVE_INFINITY : period / 10.0;
    }

    public double insertion(int bank) {
        return value(DATA_INSERTION + bank) / 1000.0;
    }

    public double target(int bank) {
        return value(DATA_TARGET + bank) / 1000.0;
    }

    public double fuelTemp() {
        return value(DATA_FUEL_TEMP) / 10.0;
    }

    public double cladTemp() {
        return value(DATA_CLAD_TEMP) / 10.0;
    }

    public double coolantTemp() {
        return value(DATA_COOLANT_TEMP) / 10.0;
    }

    public double pressure() {
        return value(DATA_PRESSURE) / 10.0;
    }

    public double xenon() {
        return value(DATA_XENON) / 1000.0;
    }

    public int decay() {
        return value(DATA_DECAY);
    }

    public int coolant() {
        return value(DATA_COOLANT);
    }

    public int coolantCapacity() {
        return value(DATA_COOLANT_CAPACITY);
    }

    public boolean heavyWater() {
        return value(DATA_COOLANT_TYPE) == 1;
    }

    public net.zagdrath.arcforge.nuclear.reactor.CoreLattice.Coolant coolantType() {
        net.zagdrath.arcforge.nuclear.reactor.CoreLattice.Coolant[] values = net.zagdrath.arcforge.nuclear.reactor.CoreLattice.Coolant.values();
        return values[Math.max(0, Math.min(values.length - 1, value(DATA_COOLANT_TYPE)))];
    }

    public ReactorSim.Mode mode() {
        return ReactorSim.Mode.values()[Math.max(0, Math.min(ReactorSim.Mode.values().length - 1, value(DATA_MODE)))];
    }

    public ReactorSim.Trip trip() {
        return ReactorSim.Trip.values()[Math.max(0, Math.min(ReactorSim.Trip.values().length - 1, value(DATA_TRIP)))];
    }

    public boolean scrammed() {
        return value(DATA_SCRAMMED) != 0;
    }

    public boolean flag(int bit) {
        return (value(DATA_FLAGS) & bit) != 0;
    }

    public boolean tripOn(int which) {
        return flag(1 << which);
    }

    public int tripValue(int which) {
        return value(DATA_TRIP_POWER + which);
    }

    public int rods() {
        return value(DATA_RODS);
    }

    public int channels() {
        return value(DATA_CHANNELS);
    }

    public int controlRods() {
        return value(DATA_CONTROL_RODS);
    }

    // The Autopilot's state (an ordinal of ReactorAutopilot.State) and its goal in tenths of a percent.
    public static int packAutopilot(int state, double goal) {
        return state << 16 | (int) Math.round(Math.max(0.0, Math.min(1.0, goal)) * 1000.0);
    }

    // Whether the Autopilot's card is in.
    public boolean hasAutopilot() {
        return value(DATA_AUTOPILOT) >= 0;
    }

    public ReactorAutopilot.State autopilotState() {
        ReactorAutopilot.State[] states = ReactorAutopilot.State.values();
        int packed = value(DATA_AUTOPILOT);
        return packed < 0 ? ReactorAutopilot.State.OFF : states[Math.min(states.length - 1, packed >> 16)];
    }

    // Its goal, as a share of full power.
    public double autopilotGoal() {
        int packed = value(DATA_AUTOPILOT);
        return packed < 0 ? 0.0 : (packed & 0xFFFF) / 1000.0;
    }

    // The Upgrades tab hangs off this GUI's own right edge.
    @Override
    protected int upgradeSlotX() {
        return WIDTH - 4 + UPGRADE_SLOT_X - 172;
    }

    public static int packMapSize(int nx, int ny, int nz) {
        return nx | ny << 8 | nz << 16;
    }

    // The core's size across (x), up (y) and deep (z), or 0s when it isn't formed.
    public int[] mapSize() {
        int packed = value(DATA_MAP_SIZE);
        return new int[] { packed & 0xFF, packed >> 8 & 0xFF, packed >> 16 & 0xFF };
    }

    // A cell: what it holds (3 bits), and for a fuel rod its burnup in thousandths (10 bits), its enrichment in
    // hundredths of a percent (11 bits), its fuel type (2 bits), damaged and to come out.
    public static int packCell(int kind, double burnup, int enrichmentPcm, int type, boolean damaged, boolean pull) {
        int permille = (int) Math.round(Math.max(0.0, Math.min(1.0, burnup)) * 1000.0);
        int hundredths = Math.max(0, Math.min(2_047, enrichmentPcm / 10));
        return kind | permille << 3 | hundredths << 13 | (type & 3) << 24 | (damaged ? 1 << 26 : 0) | (pull ? 1 << 27 : 0);
    }

    public int cell(int i) {
        return value(DATA_MAP + i);
    }

    public static int cellKind(int cell) {
        return cell & 7;
    }

    public static double cellBurnup(int cell) {
        return (cell >> 3 & 0x3FF) / 1000.0;
    }

    // Its enrichment in pcm.
    public static int cellEnrichment(int cell) {
        return (cell >> 13 & 0x7FF) * 10;
    }

    public static int cellType(int cell) {
        return cell >> 24 & 3;
    }

    public static boolean cellDamaged(int cell) {
        return (cell & 1 << 26) != 0;
    }

    public static boolean cellPull(int cell) {
        return (cell & 1 << 27) != 0;
    }

    public int controlChannels() {
        return value(DATA_CONTROL_CHANNELS);
    }

    public double burnup() {
        return value(DATA_BURNUP) / 1000.0;
    }

    public double doppler() {
        return value(DATA_DOPPLER) / 100.0;
    }

    public double voidCoefficient() {
        return value(DATA_VOID) / 100.0;
    }

    public double flow() {
        return value(DATA_FLOW) / 1000.0;
    }

    public int pump() {
        return value(DATA_PUMP);
    }

    public int hotLeg() {
        return value(DATA_HOT_LEG);
    }

    public int steam() {
        return value(DATA_STEAM);
    }

    public int intake() {
        return value(DATA_INTAKE);
    }

    public int rated() {
        return value(DATA_RATED);
    }

    public double peaking() {
        return value(DATA_PEAKING) / 100.0;
    }

    public double voidFraction() {
        return value(DATA_VOID_FRACTION) / 1000.0;
    }

    // A trend reading, ago seconds back (0 the newest): series 0 power (log10 x 1000), 1 coolant °C x 10, 2 k x 100000.
    public int history(int series, int ago) {
        int head = value(DATA_HISTORY_HEAD);
        int index = Math.floorMod(head - ago, FissionReactorBlockEntity.HISTORY);
        return value(DATA_HISTORY + series * FissionReactorBlockEntity.HISTORY + index);
    }
}
