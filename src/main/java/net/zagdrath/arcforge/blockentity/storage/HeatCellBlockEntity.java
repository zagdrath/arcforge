/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.storage.HeatCellBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineOutputs;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.storage.HeatCellMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Stores heat (HU). Input faces accept heat, output faces give it and push it into colder neighbours
// each tick, both capped at the tier's rate. Heat always leaks away: once a second the cell loses a
// share of what it holds (less for better-insulated tiers), so an idle cell drifts back to 20°C.
// Redstone control pauses pushing; leaking never stops.
public class HeatCellBlockEntity extends StorageBlockEntity {
    public static final int MAX_CELSIUS = 1_100;
    private static final int LEAK_INTERVAL = 20;
    // Temperatures at which the cell shows heat level 1, 2, 3 and 4.
    private static final int[] HEAT_LEVELS = { 300, 650, 850, 1_000 };

    private final HeatBuffer heat;
    private final HeatHandler inputView;
    private final HeatHandler outputView;
    private final ContainerData data;
    private final Map<Direction, BlockCapabilityCache<HeatHandler, @Nullable Direction>> heatTargets = new EnumMap<>(Direction.class);

    // Rate caps: HU received and given out during the current game tick.
    private long window = -1;
    private int receivedInWindow;
    private int extractedInWindow;
    // HU that came in and went out since the last tick, shown in the GUI.
    private int receivedSinceTick;
    private int extractedSinceTick;
    private int lastReceived;
    private int lastExtracted;
    // Leaking: the fraction of an HU not yet lost, and the current loss for the GUI.
    private double leakRemainder;
    private int leakPerTick;
    private int comparatorSignal = -1;

    public HeatCellBlockEntity(BlockPos pos, BlockState state) {
        // Defaults as the energy cell: take heat on every side, give it from the front.
        super(ModBlockEntityTypes.HEAT_CELL.get(), pos, state, ((HeatCellBlock) state.getBlock()).getTier(),
                new SideConfig(SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.OUTPUT));
        this.heat = new HeatBuffer(tier.heatCellCapacity(), MAX_CELSIUS, this::setChanged);
        this.inputView = new View(true);
        this.outputView = new View(false);
        this.data = new WideIntContainerData(HeatCellMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case HeatCellMenu.DATA_HEAT -> heat.getStored();
                    case HeatCellMenu.DATA_CAPACITY -> heat.getCapacity();
                    case HeatCellMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case HeatCellMenu.DATA_RECEIVED -> lastReceived;
                    case HeatCellMenu.DATA_EXTRACTED -> lastExtracted;
                    case HeatCellMenu.DATA_LEAK -> leakPerTick;
                    case HeatCellMenu.DATA_TIER -> tier.ordinal();
                    case HeatCellMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case HeatCellMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // Heat cells have no item slots.
    @Override
    protected boolean isItemValid(int slot, ItemResource resource) {
        return false;
    }

    @Override
    protected ConduitType conduitType() {
        return ConduitType.THERMAL;
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public int getLeakPerTick() {
        return leakPerTick;
    }

    // The tier's leak for display: "10", "1.5".
    public static String leakPercentText(ConduitTier tier) {
        double percent = tier.heatCellLeakPercentPerMinute();
        return percent == Math.floor(percent) ? Integer.toString((int) percent) : String.format(java.util.Locale.ROOT, "%.1f", percent);
    }

    public static int heatLevel(int celsius) {
        int level = 0;
        while (level < HEAT_LEVELS.length && celsius >= HEAT_LEVELS[level]) {
            level++;
        }
        return level;
    }

    @Override
    public int getComparatorSignal() {
        return Mth.floor(15.0 * heat.getStored() / heat.getCapacity());
    }

    // --- Ticking ---

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % LEAK_INTERVAL == 0) {
            leak();
        }
        if (isRedstoneEnabled()) {
            pushHeat(level, pos);
        }

        lastReceived = receivedSinceTick;
        lastExtracted = extractedSinceTick;
        receivedSinceTick = 0;
        extractedSinceTick = 0;

        int heatLevel = heatLevel(heat.getTemperature());
        if (state.getValue(HeatCellBlock.HEAT) != heatLevel) {
            level.setBlock(pos, state.setValue(HeatCellBlock.HEAT, heatLevel), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        int signal = getComparatorSignal();
        if (signal != comparatorSignal) {
            comparatorSignal = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    // Loses a share of the stored heat, keeping the fraction so small amounts still drain smoothly.
    private void leak() {
        double perSecond = heat.getStored() * tier.heatCellLeakPercentPerMinute() / 100.0 / 60.0;
        leakPerTick = (int) Math.round(perSecond / LEAK_INTERVAL);
        double total = perSecond + leakRemainder;
        int lost = (int) Math.floor(total);
        leakRemainder = total - lost;
        heat.remove(lost);
        if (heat.getStored() == 0) {
            leakRemainder = 0;
        }
    }

    // Pushes heat out of every output face into colder neighbours, up to the tier's rate in total.
    private void pushHeat(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            int budget = remaining(false);
            if (budget <= 0 || heat.getStored() <= 0) {
                return;
            }
            if (modeFor(direction) != SideMode.OUTPUT) {
                continue;
            }
            HeatHandler target = heatTargets
                    .computeIfAbsent(direction, dir -> BlockCapabilityCache.create(ModCapabilities.HEAT, level, pos.relative(dir), dir.getOpposite()))
                    .getCapability();
            if (target != null) {
                countExtracted(MachineOutputs.moveHeat(heat, target, budget));
            }
        }
    }

    // What's left of this tick's rate for heat coming in (or going out).
    private int remaining(boolean in) {
        long now = level != null ? level.getGameTime() : 0;
        if (now != window) {
            window = now;
            receivedInWindow = 0;
            extractedInWindow = 0;
        }
        return Math.max(0, tier.heatCellRate() - (in ? receivedInWindow : extractedInWindow));
    }

    private void countReceived(int amount) {
        receivedInWindow += amount;
        receivedSinceTick += amount;
    }

    private void countExtracted(int amount) {
        extractedInWindow += amount;
        extractedSinceTick += amount;
    }

    // --- Capabilities ---

    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return inputView;
        }
        return switch (mode) {
            case INPUT -> inputView;
            case OUTPUT -> outputView;
            default -> null;
        };
    }

    // One direction of the cell, capped at the tier's rate per tick. Whoever moves the heat checks
    // that it flows from hotter to colder.
    private class View implements HeatHandler {
        private final boolean in;

        View(boolean in) {
            this.in = in;
        }

        @Override
        public int getHeat() {
            return heat.getStored();
        }

        @Override
        public int getMaxHeat() {
            return heat.getCapacity();
        }

        @Override
        public int getTemperature() {
            return heat.getTemperature();
        }

        @Override
        public int receiveHeat(int amount, boolean simulate) {
            if (!in) {
                return 0;
            }
            int accepted = Math.min(Math.min(Math.max(0, amount), remaining(true)), heat.getRoom());
            if (!simulate && accepted > 0) {
                heat.add(accepted);
                countReceived(accepted);
            }
            return accepted;
        }

        @Override
        public int extractHeat(int amount, boolean simulate) {
            if (in) {
                return 0;
            }
            int extracted = Math.min(Math.min(Math.max(0, amount), remaining(false)), heat.getStored());
            if (!simulate && extracted > 0) {
                heat.remove(extracted);
                countExtracted(extracted);
            }
            return extracted;
        }
    }

    // --- Item form: the heat travels in a data component (and doesn't leak while it's an item) ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer stored = components.get(ModDataComponents.HEAT.get());
        if (stored != null) {
            heat.remove(heat.getStored());
            heat.add(stored);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (heat.getStored() > 0) {
            components.set(ModDataComponents.HEAT.get(), heat.getStored());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("heat");
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        leakRemainder = input.getDoubleOr("leak_remainder", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        output.putDouble("leak_remainder", leakRemainder);
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HeatCellMenu(containerId, inventory, worldPosition, items, data);
    }
}
