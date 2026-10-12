/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.output;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.logistics.HeatPipeBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.steam.SteamGrade;

// "Limited by connections": whether what a producer is connected to holds its output back. Each tick the machine
// reports whether it is running (it has fuel, sun, steam or heat to work with and isn't switched off), how full its
// buffer is against what it may hold, everything given out so far (a counter on the buffer: pushed out, or drawn by
// conduits and pipes), and what it could give out a tick. The warning comes on when, for outputLimit.delayTicks in a
// row, it runs, something is connected to take its output, its buffer is at least outputLimit.bufferFullPercent full
// and it gives out outputLimit.shortfallPercent or more less than it could (both rates smoothed over about a second).
// It goes off as soon as any of that stops: output caught up, the buffer drained, or the machine idle or unconnected.
public final class OutputLimit {
    public enum Unit {
        HU("hu"), FE("fe"), STEAM("steam");

        private final String key;

        Unit(String key) {
            this.key = key;
        }

        public Component rateName() {
            return Component.translatable("gui.arcforge.output_limited.unit." + key);
        }
    }

    // What the menus and Jade are sent: the rate given out, the rate it could give, and the unit (0 when not limited).
    public record Shown(int given, int could, Unit unit) {
        public Component line() {
            return Component.translatable("gui.arcforge.output_limited", grouped(given), grouped(could), unit.rateName());
        }

        // "Limited: 3,200 / 50,400 HU/t", for a GUI too narrow for the whole line.
        public Component shortLine() {
            return Component.translatable("gui.arcforge.output_limited.short", grouped(given), grouped(could), unit.rateName());
        }

        private static String grouped(int value) {
            return String.format(java.util.Locale.ROOT, "%,d", value);
        }
    }

    public static final int DATA_VALUES = 3;
    private static final int DATA_GIVEN = 0, DATA_COULD = 1, DATA_UNIT = 2;
    // Smoothing of the rates: a share of the gap per tick (about a second to settle).
    private static final double SMOOTHING = 0.1;
    // How often the connections are looked at again.
    private static final int CONNECTION_CHECK_INTERVAL = 20;
    private static final int STALE_TICKS = 20;

    private final Unit unit;
    private long lastDrained = -1;
    private double given;
    private double could;
    private int heldTicks;
    private boolean limited;
    private boolean connected;
    private long connectionCheckedAt = Long.MIN_VALUE;
    // When it last ticked: a warning from a machine that stopped ticking (a multiblock that came apart) isn't shown.
    private long tickedAt = Long.MIN_VALUE;

    public OutputLimit(Unit unit) {
        this.unit = unit;
    }

    public Unit unit() {
        return unit;
    }

    // One tick, after the machine has made and pushed its output. drained: the buffer's running total of what was given
    // out (it may restart from 0 when the buffer is replaced); couldPerTick: what it could give out a tick right now.
    public void tick(ServerLevel level, boolean running, BooleanSupplier hasConnections, long stored, long limit, long drained, int couldPerTick) {
        long givenNow = lastDrained < 0 || drained < lastDrained ? 0 : drained - lastDrained;
        lastDrained = drained;
        given += (givenNow - given) * SMOOTHING;
        could += (Math.max(0, couldPerTick) - could) * SMOOTHING;
        long time = level.getGameTime();
        tickedAt = time;
        if (!running) {
            connectionCheckedAt = Long.MIN_VALUE;
        } else if (connectionCheckedAt == Long.MIN_VALUE || time - connectionCheckedAt >= CONNECTION_CHECK_INTERVAL) {
            connectionCheckedAt = time;
            // Whatever it's giving output to is connected, even if it has taken all it can this tick (a Heat Cell or conduit
            // at its rate refuses even a simulated 1 more).
            connected = given >= 1 || hasConnections.getAsBoolean();
        }
        boolean full = limit > 0 && stored * 100 >= limit * (long) ArcforgeConfig.OUTPUT_LIMIT_FULL_PERCENT.getAsInt();
        boolean short_ = couldPerTick > 0 && given * 100 < could * (100 - ArcforgeConfig.OUTPUT_LIMIT_SHORTFALL_PERCENT.getAsInt());
        if (running && connected && full && short_) {
            heldTicks = Math.min(heldTicks + 1, Integer.MAX_VALUE - 1);
        } else {
            heldTicks = 0;
        }
        limited = heldTicks >= ArcforgeConfig.OUTPUT_LIMIT_DELAY.getAsInt();
    }

    public boolean isLimited() {
        return limited;
    }

    // What to show, or null while it isn't limited.
    public @Nullable Shown shown() {
        return limited ? new Shown((int) Math.round(given), (int) Math.round(could), unit) : null;
    }

    // The same, or null if it hasn't ticked in the last second (its multiblock came apart, or it stopped being the master).
    public @Nullable Shown shown(long gameTime) {
        return gameTime - tickedAt <= STALE_TICKS ? shown() : null;
    }

    // Forgets the warning: the machine stopped (its structure broke).
    public void reset() {
        heldTicks = 0;
        limited = false;
    }

    // --- Syncing to menus (added by MachineMenu for every machine; zeros for machines without one) ---

    public ContainerData data(LongSupplier gameTime) {
        return new WideIntContainerData(DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                Shown shown = shown(gameTime.getAsLong());
                if (shown == null) {
                    return 0;
                }
                return switch (index) {
                    case DATA_GIVEN -> shown.given();
                    case DATA_COULD -> shown.could();
                    default -> shown.unit().ordinal() + 1;
                };
            }
        };
    }

    public static ContainerData emptyData() {
        return WideIntContainerData.client(DATA_VALUES);
    }

    public static @Nullable Shown read(ContainerData data) {
        int unit = WideIntContainerData.read(data, DATA_UNIT);
        if (unit <= 0 || unit > Unit.values().length) {
            return null;
        }
        return new Shown(WideIntContainerData.read(data, DATA_GIVEN), WideIntContainerData.read(data, DATA_COULD), Unit.values()[unit - 1]);
    }

    // --- Is anything connected? ---

    // Whether any face of a single-block machine set to mode has something beyond it that takes this output.
    public static boolean anyFaceTakes(ServerLevel level, BlockPos pos, Direction facing, SideConfig sides, SideMode mode, Unit unit) {
        for (Direction direction : Direction.values()) {
            if (sides.get(facing, direction) == mode && takes(level, pos, direction, unit)) {
                return true;
            }
        }
        return false;
    }

    // Whether any of a multiblock's ports in mode has something beyond its face that takes this output.
    public static boolean anyPortTakes(ServerLevel level, MultiblockController controller, SideMode mode, Unit unit) {
        for (MultiblockPorts.Port port : MultiblockPorts.list(level, controller)) {
            if (port.mode() == mode && takes(level, port.pos(), port.face(), unit)) {
                return true;
            }
        }
        return false;
    }

    // Whether the block beyond face of pos can take this output: something with the capability on that side, a conduit
    // of the matching type, or (for heat) the end of a Heat Pipe.
    public static boolean takes(ServerLevel level, BlockPos pos, Direction face, Unit unit) {
        BlockPos beyond = pos.relative(face);
        if (!level.isLoaded(beyond)) {
            return false;
        }
        BlockCapability<?, @Nullable Direction> capability = switch (unit) {
            case HU -> ModCapabilities.HEAT;
            case FE -> Capabilities.Energy.BLOCK;
            case STEAM -> Capabilities.Fluid.BLOCK;
        };
        if (accepts(level.getCapability(capability, beyond, face.getOpposite()), unit)) {
            return true;
        }
        if (level.getBlockEntity(beyond) instanceof ConduitBlockEntity conduit) {
            ConduitType type = conduit.getConduitType();
            return switch (unit) {
                case HU -> type == ConduitType.THERMAL;
                case FE -> type == ConduitType.ENERGY;
                case STEAM -> type == ConduitType.GAS;
            };
        }
        return unit == Unit.HU && HeatPipeBlock.isEndFacing(level.getBlockState(beyond), face.getOpposite());
    }

    // Whether a handler beyond a face would take some of this output now (simulated). One that only gives out (another
    // producer's face) or is full doesn't count: a full consumer is a bottleneck of its own, and shows it there.
    @SuppressWarnings("unchecked")
    private static boolean accepts(@Nullable Object handler, Unit unit) {
        if (handler == null) {
            return false;
        }
        try (Transaction tx = Transaction.openRoot()) {
            return switch (unit) {
                case HU -> ((HeatHandler) handler).receiveHeat(1, true) > 0;
                case FE -> ((EnergyHandler) handler).insert(1, tx) > 0;
                case STEAM -> {
                    ResourceHandler<FluidResource> fluids = (ResourceHandler<FluidResource>) handler;
                    for (SteamGrade grade : SteamGrade.values()) {
                        for (int index = 0; index < fluids.size(); index++) {
                            if (fluids.insert(index, grade.resource(), 1, tx) > 0) {
                                yield true;
                            }
                        }
                    }
                    yield false;
                }
            };
        }
    }
}
