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
import net.zagdrath.arcforge.block.multiblock.SteamBoilerArrayCasingBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.machine.BucketSlots;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.SteamBoilerMenu;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.Boiler;
import net.zagdrath.arcforge.steam.BoilerCore;
import net.zagdrath.arcforge.steam.BoilerPressure;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

import com.mojang.serialization.Codec;

// The Steam Boiler Array (see ShellMultiblockBlockEntity): boils water into steam with heat, and grows with
// its height h. It holds 100,000 x h HU and boils with up to 600 x h HU/t; each mB costs 8 / 12 / 16 HU for
// Steam / High-Pressure / Superheated (Superheated from a 7-tall array: up to 262 mB/t). Water and steam
// tanks hold 16,000 x h mB. The grade and pressure rules are in BoilerCore.
public class SteamBoilerArrayBlockEntity extends ShellMultiblockBlockEntity implements Boiler {
    // Water buckets go in the input slot and are poured into the tank; the empty buckets come out below.
    public static final int SLOT_BUCKET_IN = 0;
    public static final int SLOT_BUCKET_OUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT);
    // Levels are sent to clients (for the water and steam in the windows) at most this often.
    private static final int SYNC_INTERVAL = 10;

    private HeatBuffer heat;
    private HeatHandler heatInput;
    private final FilteredFluidTank water;
    private final FilteredFluidTank steam;
    private final ResourceHandler<FluidResource> waterInput;
    private final ResourceHandler<FluidResource> steamOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final ResourceHandler<ItemResource> itemAutomation;
    private BoilerCore core;
    private final ContainerData data;
    private boolean boiling;
    private boolean syncPending;
    private long lastSync;
    // Client side: the water and steam levels (0-1) the renderer shows, easing toward the synced ones.
    private float shownWater = -1;
    private float shownSteam = -1;

    public SteamBoilerArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.STEAM_BOILER_ARRAY.get(), pos, state, MACHINE_SLOTS, SteamBoilerArrayBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.OUTPUT, SideMode.NONE, SideMode.INPUT, SideMode.NONE, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES);
        int tank = ArcforgeConfig.BOILER_ARRAY_TANK_PER_HEIGHT.getAsInt() * 3;
        this.water = new FilteredFluidTank(tank, BoilerCore::isWater, this::onFluidChanged);
        this.steam = new FilteredFluidTank(tank, BoilerCore::isSteam, this::onFluidChanged);
        this.waterInput = new AutomationResourceHandler<>(water, index -> true, index -> false);
        this.steamOutput = new AutomationResourceHandler<>(steam, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(waterInput, steamOutput);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_BUCKET_IN, slot -> slot == SLOT_BUCKET_OUT);
        resize(3);
        this.data = new WideIntContainerData(SteamBoilerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return SteamBoilerMenu.value(index, heat, water, steam, core, status, redstoneMode, sideConfig, getHeight());
            }
        };
    }

    // The bucket slot takes full buckets of water.
    public static boolean isItemValid(int slot, ItemResource resource) {
        FluidResource fluid = slot == SLOT_BUCKET_IN ? BucketSlots.contents(resource.toStack(1)) : null;
        return fluid != null && BoilerCore.isWater(fluid);
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, SteamBoilerArrayBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    // The heat buffer and tanks scale with the height. Stored heat and fluid carry over (clamped).
    private void resize(int height) {
        int stored = heat != null ? heat.getStored() : 0;
        heat = new HeatBuffer(
                ArcforgeConfig.BOILER_ARRAY_HEAT_PER_HEIGHT.getAsInt() * height,
                ArcforgeConfig.BOILER_ARRAY_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        heat.add(stored);
        heatInput = heat.input(Integer.MAX_VALUE);
        int tank = ArcforgeConfig.BOILER_ARRAY_TANK_PER_HEIGHT.getAsInt() * height;
        water.setCapacity(tank);
        steam.setCapacity(tank);
        BoilerPressure pressure = core != null ? core.getPressure() : BoilerPressure.AUTO;
        core = new BoilerCore(heat, water, steam);
        core.setPressure(pressure);
    }

    public int getHeight() {
        ShellStructure.Shell shell = getShell();
        return shell != null ? shell.length() : 3;
    }

    private void onFluidChanged() {
        setChanged();
        syncPending = true;
    }

    @Override
    protected ShellStructure structure() {
        return SteamBoilerArrayCasingBlock.STRUCTURE;
    }

    @Override
    public void setShell(ShellStructure.@Nullable Shell shell) {
        super.setShell(shell);
        if (shell != null) {
            resize(shell.length());
        }
    }

    @Override
    protected void tickMaster(ServerLevel level) {
        BucketSlots.pour(items, SLOT_BUCKET_IN, SLOT_BUCKET_OUT, water);
        boolean wasBoiling = boiling;
        if (!redstoneMode.canRun(isPowered())) {
            status = MachineStatus.DISABLED;
            boiling = false;
        } else {
            int height = getHeight();
            status = switch (core.tick(ArcforgeConfig.BOILER_ARRAY_MAX_HEAT_PER_HEIGHT.getAsInt() * height,
                    ArcforgeConfig.BOILER_ARRAY_HEAT_COST.getAsDouble())) {
                case BOILING -> MachineStatus.BOILING;
                case HEATING -> MachineStatus.HEATING;
                case NO_WATER -> MachineStatus.NO_WATER;
                case STEAM_FULL -> MachineStatus.STEAM_FULL;
            };
            boiling = status == MachineStatus.BOILING;
        }
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this);
        }
        if (boiling != wasBoiling || (syncPending && level.getGameTime() - lastSync >= SYNC_INTERVAL)) {
            syncPending = false;
            lastSync = level.getGameTime();
            sync();
        }
    }

    @Override
    public BoilerCore getCore() {
        return core;
    }

    @Override
    public void setPressure(BoilerPressure pressure) {
        core.setPressure(pressure);
        setChanged();
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public FilteredFluidTank getSteam() {
        return steam;
    }

    // Client side: moves the shown water level toward target by a share of the gap, and returns it.
    public float easeWater(float target, float share) {
        shownWater = shownWater < 0 ? target : shownWater + (target - shownWater) * share;
        return shownWater;
    }

    public float easeSteam(float target, float share) {
        shownSteam = shownSteam < 0 ? target : shownSteam + (target - shownSteam) * share;
        return shownSteam;
    }

    // Client side: whether it was boiling at the last sync (for particles).
    public boolean isBoilingOnClient() {
        return boiling;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.INPUT ? itemAutomation : null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return fluidAutomation;
        }
        return switch (mode) {
            case INPUT -> waterInput;
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
            case FLUID, ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> ConnectionMode.NONE;
        };
    }

    // --- Saving ---

    // The Settings Card also copies the pressure setting.
    @Override
    public void writeSettings(ValueOutput output) {
        super.writeSettings(output);
        output.putInt("pressure", getCore().getPressure().ordinal());
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
        resize(getHeight());
        heat.deserialize(input);
        water.deserialize(input.childOrEmpty("water"));
        steam.deserialize(input.childOrEmpty("steam"));
        core.deserialize(input);
        boiling = input.getBooleanOr("boiling", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        water.serialize(output.child("water"));
        steam.serialize(output.child("steam"));
        core.serialize(output);
        output.putBoolean("boiling", boiling);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.steam_boiler_array.sized", getHeight());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return SteamBoilerMenu.array(containerId, inventory, worldPosition, items, data);
    }
}
