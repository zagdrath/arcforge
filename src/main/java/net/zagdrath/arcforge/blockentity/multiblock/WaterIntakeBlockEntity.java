/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.WaterIntakeCasingBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ClimateHelper;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.WaterIntakeMenu;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.BoilerCore;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Industrial Water Intake (see CubeMultiblockBlockEntity): a 3x3x3 pump house standing in water. Its intake is the
// middle of its bottom face: the block under it must be a water source, part of a body of at least
// waterIntake.minWaterBlocks connected water sources (counted every scanInterval ticks, 6 ways through water sources
// only). Then it pumps waterIntake.waterPerTick mB/t (x openWaterMultiplier in rivers and oceans) into its tank for
// energyPerMb FE each, as far as its FE and tank room allow. Its Output ports push the water out on their own, each up to
// what it pumps between pushes, and Fluid Conduits can pull it. The water isn't taken from the world.
public class WaterIntakeBlockEntity extends CubeMultiblockBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.OUTPUT, SideMode.ENERGY);

    private final FilteredFluidTank water;
    private final ResourceHandler<FluidResource> waterOutput;
    private final ContainerData data;
    // The last count of connected water sources (up to the scan's limit), and whether it's in a river or ocean.
    private int waterBlocks;
    private boolean openWater;
    private long lastScan;
    private boolean scanDue = true;
    // mB pumped last tick.
    private int pumped;

    public WaterIntakeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WATER_INTAKE.get(), pos, state, 0, (level, slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.OUTPUT, SideMode.OUTPUT, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES, true, ArcforgeConfig.WATER_INTAKE_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.WATER_INTAKE_ENERGY_CAPACITY.getAsInt(),
                new int[0], slot -> false);
        this.water = new FilteredFluidTank(ArcforgeConfig.WATER_INTAKE_TANK.getAsInt(), BoilerCore::isWater, this::setChanged);
        this.waterOutput = new AutomationResourceHandler<>(water, index -> false, index -> true);
        this.data = new WideIntContainerData(WaterIntakeMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case WaterIntakeMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case WaterIntakeMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case WaterIntakeMenu.DATA_USAGE -> usage;
                    case WaterIntakeMenu.DATA_WATER -> water.getAmount();
                    case WaterIntakeMenu.DATA_TANK_CAPACITY -> water.getCapacity();
                    case WaterIntakeMenu.DATA_RATE -> rate();
                    case WaterIntakeMenu.DATA_PUMPED -> pumped;
                    case WaterIntakeMenu.DATA_WATER_BLOCKS -> waterBlocks;
                    case WaterIntakeMenu.DATA_OPEN_WATER -> openWater ? 1 : 0;
                    case WaterIntakeMenu.DATA_STATUS -> status.ordinal();
                    case WaterIntakeMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case WaterIntakeMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // A client-side copy of the (no) slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(0, (slot, resource) -> false, UPGRADES, () -> {});
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return WaterIntakeCasingBlock.STRUCTURE;
    }

    // --- The water around it ---

    // Its intake: the block under the middle of its bottom face.
    public BlockPos intakePos() {
        return getMinCorner().offset(1, -1, 1);
    }

    // How many water sources are connected to the one at start, counting up to limit (unloaded chunks aren't followed).
    public static int countWater(Level level, BlockPos start, int limit) {
        if (!level.isLoaded(start) || !level.getFluidState(start).isSourceOfType(Fluids.WATER)) {
            return 0;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && seen.size() < limit) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.contains(next) && level.isLoaded(next) && level.getFluidState(next).isSourceOfType(Fluids.WATER)) {
                    seen.add(next);
                    queue.add(next);
                    if (seen.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return Math.min(seen.size(), limit);
    }

    public static boolean isOpenWater(Level level, BlockPos pos) {
        return ClimateHelper.inAny(level.getBiome(pos), ArcforgeConfig.WATER_INTAKE_OPEN_WATER_TAGS.get());
    }

    private void scan(Level level) {
        // Up to four times what it needs, so the GUI can show how much more there is.
        int needed = ArcforgeConfig.WATER_INTAKE_MIN_WATER.getAsInt();
        waterBlocks = countWater(level, intakePos(), Math.max(needed, Math.min(needed * 4, 16_384)));
        openWater = isOpenWater(level, intakePos());
    }

    public boolean hasEnoughWater() {
        return waterBlocks >= ArcforgeConfig.WATER_INTAKE_MIN_WATER.getAsInt();
    }

    // mB/t it can pump here: the base rate, more in rivers and oceans; 0 without enough water.
    public int rate() {
        if (!hasEnoughWater()) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(ArcforgeConfig.WATER_INTAKE_RATE.getAsInt()
                * (openWater ? ArcforgeConfig.WATER_INTAKE_OPEN_WATER_MULTIPLIER.getAsDouble() : 1.0)));
    }

    // Looks again on the next tick (a casing moved, the cube turned or re-formed).
    @Override
    public void onStructureChanged() {
        super.onStructureChanged();
        scanDue = true;
    }

    // --- Running ---

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        tickPorts(level);
        if (!isFormed()) {
            return;
        }
        if (scanDue || level.getGameTime() - lastScan >= ArcforgeConfig.WATER_INTAKE_SCAN_INTERVAL.getAsInt()) {
            scanDue = false;
            lastScan = level.getGameTime();
            scan(level);
        }
        pumped = 0;
        usage = 0;
        int rate = rate();
        if (!canRun(level)) {
            status = stoppedStatus();
        } else if (rate <= 0) {
            status = MachineStatus.NO_WATER;
        } else if (water.getSpace() <= 0) {
            status = MachineStatus.OUTPUT_FULL;
        } else {
            double fePerMb = ArcforgeConfig.WATER_INTAKE_FE_PER_MB.getAsDouble();
            int n = Math.min(rate, water.getSpace());
            if (fePerMb > 0) {
                n = (int) Math.min(n, Math.floor(energy.getAmountAsInt() / fePerMb));
            }
            int cost = (int) Math.ceil(n * fePerMb);
            if (n > 0 && energy.consume(cost)) {
                try (Transaction tx = Transaction.openRoot()) {
                    water.insert(0, FluidResource.of(Fluids.WATER), n, tx);
                    tx.commit();
                }
                pumped = n;
                usage = cost;
                controlState.producedFluid(n);
                setChanged();
            }
            status = pumped > 0 ? MachineStatus.PUMPING : MachineStatus.NO_POWER;
        }
        setLit(pumped > 0);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            // Each port face up to what it pumps between pushes, so the ports keep up with it.
            MultiblockAutomation.pushOutputs(level, this, true,
                    Math.max(FluidType.BUCKET_VOLUME, rate * ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt()));
        }
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public int getWaterBlocks() {
        return waterBlocks;
    }

    public boolean isOpenWater() {
        return openWater;
    }

    public int getPumped() {
        return pumped;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.OUTPUT ? waterOutput : null;
    }

    // Water leaves by Fluid Conduit; FE comes in by Energy Conduit.
    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ITEM, GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        water.deserialize(input.childOrEmpty("water"));
        scanDue = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        water.serialize(output.child("water"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.water_intake");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WaterIntakeMenu(containerId, inventory, worldPosition, items, data);
    }
}
