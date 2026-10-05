/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.SuperheaterArrayCasingBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.SuperheaterArrayMenu;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.BoilerPressure;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

import com.mojang.serialization.Codec;

// The Superheater Array (see CubeMultiblockBlockEntity): a solid box, 3x3x3 up to 7x7x7, that upgrades steam with heat.
// Steam comes in through input ports and heat through heat ports; the next grade goes out of output ports.
// Each mB costs the difference in HU/mB between the grades (5 Steam to High-Pressure, 5 High-Pressure to
// Superheated, 10 Steam to Superheated), up to 2,000 HU/t per cube (27 blocks of its volume; its buffer, tanks and flow
// scale the same way, so a 7x7x7 takes up to 25,400 HU/t and 12,700 mB/t), and only once the array is at least as hot as the
// grade it makes (500 / 900°C). It only spends the heat above that temperature, so it never cools itself
// below it. Colder, or with steam already at or above its target, steam passes through unchanged. Its
// pressure setting picks the target: Auto (the best its heat allows), High-Pressure or Superheated.
public class SuperheaterArrayBlockEntity extends CubeMultiblockBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    public static final List<BoilerPressure> PRESSURES = List.of(BoilerPressure.AUTO, BoilerPressure.HIGH_PRESSURE, BoilerPressure.SUPERHEATED);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT);

    // Replaced (keeping what they hold) when the structure's size changes.
    private HeatBuffer heat;
    private HeatHandler heatInput;
    private final FilteredFluidTank steamIn;
    private final FilteredFluidTank steamOut;
    private final ResourceHandler<FluidResource> steamInput;
    private final ResourceHandler<FluidResource> steamOutput;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ContainerData data;
    private BoilerPressure pressure = BoilerPressure.AUTO;
    // Last tick: mB moved, HU used, and the grade made (null when passing through).
    private int flow;
    private int heatUsed;
    private @Nullable SteamGrade target;

    public SuperheaterArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SUPERHEATER_ARRAY.get(), pos, state, 0, (level, slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES, false, 0, 0, new int[0], slot -> false);
        this.heat = new HeatBuffer(ArcforgeConfig.SUPERHEATER_HEAT_PER_CUBE.getAsInt(), ArcforgeConfig.SUPERHEATER_MAX_TEMPERATURE.getAsInt(), this::setChanged);
        this.heatInput = heat.input(ArcforgeConfig.SUPERHEATER_MAX_HEAT_PER_CUBE.getAsInt());
        int tank = ArcforgeConfig.SUPERHEATER_TANK_PER_CUBE.getAsInt();
        // Any grade goes in, so nothing clogs: a grade already at or above the target passes through.
        this.steamIn = new FilteredFluidTank(tank, resource -> SteamGrade.of(resource) != null, this::setChanged);
        this.steamOut = new FilteredFluidTank(tank, resource -> SteamGrade.of(resource) != null, this::setChanged);
        this.steamInput = new AutomationResourceHandler<>(steamIn, index -> true, index -> false);
        this.steamOutput = new AutomationResourceHandler<>(steamOut, index -> false, index -> true);
        this.fluidAll = new CombinedResourceHandler<>(steamInput, steamOutput);
        this.data = new WideIntContainerData(SuperheaterArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case SuperheaterArrayMenu.DATA_HEAT -> heat.getStored();
                    case SuperheaterArrayMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case SuperheaterArrayMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case SuperheaterArrayMenu.DATA_IN_FLUID -> fluidId(steamIn);
                    case SuperheaterArrayMenu.DATA_IN_AMOUNT -> steamIn.getAmount();
                    case SuperheaterArrayMenu.DATA_OUT_FLUID -> fluidId(steamOut);
                    case SuperheaterArrayMenu.DATA_OUT_AMOUNT -> steamOut.getAmount();
                    case SuperheaterArrayMenu.DATA_TANK_CAPACITY -> steamOut.getCapacity();
                    case SuperheaterArrayMenu.DATA_FLOW -> flow;
                    case SuperheaterArrayMenu.DATA_HEAT_USED -> heatUsed;
                    case SuperheaterArrayMenu.DATA_TARGET -> target != null ? target.ordinal() : -1;
                    case SuperheaterArrayMenu.DATA_PRESSURE -> pressure.ordinal();
                    case SuperheaterArrayMenu.DATA_STATUS -> status.ordinal();
                    case SuperheaterArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case SuperheaterArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    // A client-side copy of the (no) slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(0, (slot, resource) -> false, UPGRADES, () -> {});
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return SuperheaterArrayCasingBlock.STRUCTURE;
    }

    // Most HU/t it takes in and uses, and most mB/t it moves: per cube, times its cubes.
    public int maxHeatPerTick() {
        return perCube(ArcforgeConfig.SUPERHEATER_MAX_HEAT_PER_CUBE.getAsInt());
    }

    public int maxFlow() {
        return perCube(ArcforgeConfig.SUPERHEATER_MAX_FLOW_PER_CUBE.getAsInt());
    }

    // The cube grew into a box: its heat and steam go to the box's master.
    @Override
    public void moveContentsTo(CubeMultiblockBlockEntity master) {
        if (master instanceof SuperheaterArrayBlockEntity to) {
            heat.remove(to.heat.add(heat.getStored()));
            moveTank(steamIn, to.steamIn);
            moveTank(steamOut, to.steamOut);
            to.pressure = pressure;
            setChanged();
            to.setChanged();
        }
    }

    static void moveTank(FilteredFluidTank from, FilteredFluidTank to) {
        if (from.getAmount() <= 0) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int moved = to.insert(0, from.getResource(0), from.getAmount(), tx);
            from.extract(0, from.getResource(0), moved, tx);
            tx.commit();
        }
    }

    // The heat buffer and tanks follow the size; stored heat and steam carry over. A box that breaks keeps its size until it
    // forms again, so breaking a casing loses nothing.
    @Override
    protected void onSizeChanged() {
        if (getBox() == null && heat.getCapacity() > ArcforgeConfig.SUPERHEATER_HEAT_PER_CUBE.getAsInt()) {
            return;
        }
        int stored = heat.getStored();
        heat = new HeatBuffer(perCube(ArcforgeConfig.SUPERHEATER_HEAT_PER_CUBE.getAsInt()), ArcforgeConfig.SUPERHEATER_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        heat.add(stored);
        heatInput = heat.input(maxHeatPerTick());
        int tank = perCube(ArcforgeConfig.SUPERHEATER_TANK_PER_CUBE.getAsInt());
        steamIn.setCapacity(tank);
        steamOut.setCapacity(tank);
    }

    // The grade steam of this grade becomes at this temperature under this pressure setting, or null to pass it
    // through: Auto makes the best grade above the input the heat allows; High-Pressure only turns Steam into
    // High-Pressure; Superheated makes only Superheated.
    public static @Nullable SteamGrade target(SteamGrade in, BoilerPressure pressure, int celsius) {
        return switch (pressure) {
            case HIGH_PRESSURE -> in == SteamGrade.STEAM && celsius >= SteamGrade.HIGH_PRESSURE.minCelsius() ? SteamGrade.HIGH_PRESSURE : null;
            case SUPERHEATED -> in != SteamGrade.SUPERHEATED && celsius >= SteamGrade.SUPERHEATED.minCelsius() ? SteamGrade.SUPERHEATED : null;
            default -> {
                SteamGrade best = null;
                for (SteamGrade grade : SteamGrade.values()) {
                    if (grade.ordinal() > in.ordinal() && grade.minCelsius() <= celsius) {
                        best = grade;
                    }
                }
                yield best;
            }
        };
    }

    // HU per mB to take steam from one grade to another.
    public static double cost(SteamGrade from, SteamGrade to) {
        return (to.huPerMb() - from.huPerMb()) * ArcforgeConfig.SUPERHEATER_HEAT_COST.getAsDouble();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        tickPorts(level);
        if (!isFormed()) {
            return;
        }
        flow = 0;
        heatUsed = 0;
        target = null;
        SteamGrade in = SteamGrade.of(steamIn.getResource(0));
        if (!canRun(level)) {
            status = stoppedStatus();
        } else if (in == null || steamIn.getAmount() == 0) {
            status = MachineStatus.NO_STEAM;
        } else {
            target = target(in, pressure, heat.getTemperature());
            SteamGrade out = target != null ? target : in;
            int space = steamOut.getAmount() == 0 || SteamGrade.of(steamOut.getResource(0)) == out ? steamOut.getSpace() : 0;
            int n = Math.min(Math.min(steamIn.getAmount(), space), maxFlow());
            if (target != null) {
                double cost = cost(in, target);
                // Only the heat above the target's temperature, so it never cools itself below it.
                int available = Math.min(heat.getStored() - heat.minStoredAt(target.minCelsius()), maxHeatPerTick());
                n = Math.min(n, cost > 0 ? (int) Math.floor(Math.max(0, available) / cost) : n);
            }
            if (n > 0) {
                move(in, out, n);
                flow = n;
                if (target != null) {
                    heatUsed = heat.remove((int) Math.ceil(n * cost(in, target)));
                    // Only upgraded steam counts as made; steam passing through unchanged doesn't.
                    controlState.consumedFluid(n);
                    controlState.producedFluid(n);
                }
                setChanged();
            }
            status = target == null ? MachineStatus.IDLE : n > 0 ? MachineStatus.SUPERHEATING : space == 0 ? MachineStatus.OUTPUT_FULL : MachineStatus.NO_HEAT;
        }
        setLit(status == MachineStatus.SUPERHEATING);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this, true);
        }
    }

    // Drains n mB of one grade and fills n mB of another (or the same one, passing through).
    private void move(SteamGrade in, SteamGrade out, int n) {
        try (Transaction tx = Transaction.openRoot()) {
            steamIn.extract(0, in.resource(), n, tx);
            steamOut.insert(0, out.resource(), n, tx);
            tx.commit();
        }
        if (n > 0) {
            ArcforgeAdvancements.produced(this, net.minecraft.world.item.ItemStack.EMPTY, out.resource().getFluid(), null);
        }
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getSteamIn() {
        return steamIn;
    }

    public FilteredFluidTank getSteamOut() {
        return steamOut;
    }

    public BoilerPressure getPressure() {
        return pressure;
    }

    // Only the settings on its Pressure tab.
    public void setPressure(BoilerPressure pressure) {
        if (PRESSURES.contains(pressure)) {
            this.pressure = pressure;
            setChanged();
        }
    }

    public int getFlow() {
        return flow;
    }

    public int getHeatUsed() {
        return heatUsed;
    }

    // The grade it made last tick, or null when passing steam through (or idle).
    public @Nullable SteamGrade getTarget() {
        return target;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return fluidAll;
        }
        return switch (mode) {
            case INPUT -> steamInput;
            case OUTPUT -> steamOutput;
            default -> null;
        };
    }

    public @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return heatInput;
        }
        return faceMode(pos, side) == SideMode.HEAT ? heatInput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ITEM, FLUID, ENERGY -> ConnectionMode.NONE;
        };
    }

    // --- Saving ---

    // The Settings Card also copies the pressure setting.
    @Override
    public void writeSettings(ValueOutput output) {
        super.writeSettings(output);
        output.putInt("pressure", getPressure().ordinal());
    }

    @Override
    public int readSettings(ValueInput input) {
        input.getInt("pressure").ifPresent(id -> setPressure(BoilerPressure.values()[Math.clamp(id, 0, BoilerPressure.values().length - 1)]));
        return super.readSettings(input);
    }

    @Override
    public List<Component> describe(ValueInput input) {
        List<Component> lines = new java.util.ArrayList<>(super.describe(input));
        input.getInt("pressure").ifPresent(id -> lines.add(Component.translatable("settings.arcforge.pressure",
                BoilerPressure.values()[Math.clamp(id, 0, BoilerPressure.values().length - 1)].getDescription())));
        return lines;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        steamIn.deserialize(input.childOrEmpty("steam_in"));
        steamOut.deserialize(input.childOrEmpty("steam_out"));
        pressure = BoilerPressure.byId(input.getIntOr("pressure", BoilerPressure.AUTO.ordinal()));
        if (!PRESSURES.contains(pressure)) {
            pressure = BoilerPressure.AUTO;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        steamIn.serialize(output.child("steam_in"));
        steamOut.serialize(output.child("steam_out"));
        output.putInt("pressure", pressure.ordinal());
    }

    @Override
    public Component getDisplayName() {
        return getBox() != null ? Component.translatable("container.arcforge.superheater_array.sized", sizeText())
                : Component.translatable("container.arcforge.superheater_array");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SuperheaterArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
