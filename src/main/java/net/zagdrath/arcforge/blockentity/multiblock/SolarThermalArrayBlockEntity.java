/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayControllerBlock;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.heat.SolarModel;
import net.zagdrath.arcforge.machine.MachineOutputs;
import net.zagdrath.arcforge.machine.ClimateHelper;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.SolarThermalArrayMenu;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.SolarThermalStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Runs the Solar Thermal Array: its receiver takes in heat by the sun (see SolarModel), into a heat buffer at
// the temperature the sun allows, and gives it out through heat ports (only ever to something cooler). A
// buffer hotter than the sun now allows loses the difference over a few seconds, so the receiver cools with
// the weather and the evening. Which collectors see the sky and the biome are rechecked every 2 seconds.
// Its only port mode is heat; a new tower gets one on the bottom-layer block behind the controller.
public class SolarThermalArrayBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.HEAT);
    private static final int REDSTONE_CHECK_INTERVAL = 10;
    private static final int SCAN_INTERVAL = 40;
    // A buffer hotter than the sun allows loses up to this share of its capacity a tick.
    private static final int COOLING_SHARE = 200;

    private SolarThermalStructure.@Nullable Tower tower;
    private SolarThermalStructure.Problem problem = SolarThermalStructure.Problem.INCOMPLETE;
    private boolean checkRequested = true;
    private boolean powered;
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    private final HeatBuffer heat;
    private final HeatHandler heatOutput;
    private final ContainerData data;
    private final List<BlockCapabilityCache<HeatHandler, @Nullable Direction>> heatTargets = new ArrayList<>();
    private boolean targetsDirty = true;

    // Rechecked every SCAN_INTERVAL ticks: which collectors see the sky (bit per collector, see
    // Tower.collector), the biome's multiplier, and whether the dimension has a sky at all.
    private int skyMask;
    private double biomeMultiplier = 1.0;
    private boolean hasSky = true;
    private long lastScan = Long.MIN_VALUE;
    // This tick's conditions and results.
    private SolarModel.Weather weather = SolarModel.Weather.CLEAR;
    private int heatPerTick;
    private int receiverTemperature = HeatBuffer.AMBIENT_CELSIUS;
    private boolean stowed;
    private int lastSyncKey = -1;

    // Client side: the trough's angle as last drawn, and when.
    private float clientAngle = Float.NaN;
    private double clientTime = -1;

    public SolarThermalArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SOLAR_THERMAL_ARRAY.get(), pos, state, 0, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.HEAT, SideMode.NONE), SIDE_MODES);
        this.heat = new HeatBuffer(ArcforgeConfig.SOLAR_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.SOLAR_MAX_TEMPERATURE.getAsInt(), this::setChanged);
        this.heatOutput = heat.output();
        this.data = new WideIntContainerData(SolarThermalArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case SolarThermalArrayMenu.DATA_HEAT -> heat.getStored();
                    case SolarThermalArrayMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case SolarThermalArrayMenu.DATA_TEMPERATURE -> receiverTemperature;
                    case SolarThermalArrayMenu.DATA_HEAT_PER_TICK -> heatPerTick;
                    case SolarThermalArrayMenu.DATA_SKY_MASK -> skyMask;
                    case SolarThermalArrayMenu.DATA_WEATHER -> weather.ordinal();
                    case SolarThermalArrayMenu.DATA_BIOME -> (int) Math.round(biomeMultiplier * 100);
                    case SolarThermalArrayMenu.DATA_AXIS -> tower == null || tower.northSouth() ? 0 : 1;
                    case SolarThermalArrayMenu.DATA_STOWED -> stowed ? 1 : 0;
                    case SolarThermalArrayMenu.DATA_STATUS -> status.ordinal();
                    case SolarThermalArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case SolarThermalArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static SolarModel.Settings settings() {
        return new SolarModel.Settings(ArcforgeConfig.SOLAR_PEAK_HEAT.getAsInt(), ArcforgeConfig.SOLAR_MAX_TEMPERATURE.getAsInt(),
                ArcforgeConfig.SOLAR_RAIN_MULTIPLIER.getAsDouble(), ArcforgeConfig.SOLAR_THUNDER_MULTIPLIER.getAsDouble(),
                ArcforgeConfig.SOLAR_NORTH_SOUTH_MULTIPLIER.getAsDouble(), ArcforgeConfig.SOLAR_EAST_WEST_MULTIPLIER.getAsDouble());
    }

    public SolarThermalStructure.@Nullable Tower getTower() {
        return tower;
    }

    public SolarThermalStructure.Problem getProblem() {
        return problem;
    }

    // --- Structure ---

    @Override
    public boolean isFormed() {
        return tower != null;
    }

    public void requestCheck() {
        checkRequested = true;
    }

    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            checkRequested = false;
            updateFormed(serverLevel);
        }
    }

    // Breaking the controller un-forms the rest of the tower: the parts only ever tell a controller they
    // changed, so with this one gone nothing else would, and a new controller couldn't claim them.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && tower != null) {
            SolarThermalStructure.unform(serverLevel, tower, pos);
            tower = null;
        }
    }

    private void updateFormed(ServerLevel level) {
        SolarThermalStructure.Found found = SolarThermalStructure.find(level, worldPosition, tower);
        problem = found.problem();
        if (Objects.equals(found.tower(), tower)) {
            return;
        }
        if (tower != null) {
            SolarThermalStructure.unform(level, tower);
        }
        tower = found.tower();
        if (tower != null) {
            SolarThermalStructure.form(level, tower);
            MultiblockEffects.formed(level, tower.min(), tower.max());
            lastScan = Long.MIN_VALUE;
        }
        targetsDirty = true;
        setChanged();
        sync();
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return tower != null ? tower.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return tower != null ? tower.max() : worldPosition;
    }

    @Override
    public boolean isPart(BlockPos pos) {
        return tower != null && tower.contains(pos);
    }

    @Override
    public void onPortsChanged() {
        targetsDirty = true;
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        if (checkRequested) {
            checkNow();
        }
        if (tower == null) {
            status = MachineStatus.NOT_FORMED;
            heatPerTick = 0;
            heat.setProducing(false);
            return;
        }
        portDefaults.tick(level, this);
        long time = level.getGameTime();
        if (time % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }
        if (time - lastScan >= SCAN_INTERVAL || lastScan == Long.MIN_VALUE) {
            lastScan = time;
            scan(level);
        }
        weather = weatherAt(level);
        SolarModel.Result result = SolarModel.compute(new SolarModel.Conditions(level.getDefaultClockTime(), weather, biomeMultiplier,
                tower.northSouth(), Integer.bitCount(skyMask), hasSky), settings());
        stowed = result.stowed();
        receiverTemperature = result.temperature();
        boolean enabled = redstoneMode.canRun(powered);
        heatPerTick = enabled ? result.heatPerTick() : 0;

        // Heat goes in up to what the buffer holds at the receiver's temperature; any more than that bleeds away.
        int limit = heat.storedAt(receiverTemperature);
        boolean full = heat.getStored() >= limit;
        if (heatPerTick > 0) {
            heat.add(Math.min(heatPerTick, Math.max(0, limit - heat.getStored())));
            heat.setProducingAt(receiverTemperature);
        } else {
            heat.setProducing(false);
        }
        if (heat.getStored() > limit) {
            heat.remove(Math.min(heat.getStored() - limit, Math.max(1, heat.getCapacity() / COOLING_SHARE)));
        }
        pushHeat(level);

        if (!enabled) {
            status = MachineStatus.DISABLED;
        } else if (!hasSky || skyMask == 0) {
            status = MachineStatus.NO_SKY;
        } else if (SolarModel.isNight(level.getDefaultClockTime())) {
            status = MachineStatus.NIGHT;
        } else if (weather == SolarModel.Weather.THUNDER) {
            status = MachineStatus.STOWED;
        } else if (heatPerTick > 0 && full) {
            status = MachineStatus.FULL;
        } else {
            status = MachineStatus.TRACKING;
        }
        setLit(heatPerTick > 0);
        int key = syncKey();
        if (key != lastSyncKey) {
            lastSyncKey = key;
            sync();
        }
    }

    // What clients draw: the receiver's glow (to 5°C), whether the trough is stowed, and the collectors.
    private int syncKey() {
        return ((receiverTemperature / 5 * 2 + (stowed ? 1 : 0)) * 16 + skyMask) * 4 + weather.ordinal();
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : tower.positions()) {
            if (level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    private void scan(ServerLevel level) {
        hasSky = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
        skyMask = 0;
        for (int i = 0; i < SolarModel.COLLECTORS; i++) {
            if (level.canSeeSky(tower.collector(i).above())) {
                skyMask |= 1 << i;
            }
        }
        biomeMultiplier = biomeMultiplier(level, worldPosition);
    }

    // 1.2 in hot biomes, 0.85 in cold ones (or anywhere cold enough to snow), else 1 (see the config).
    public static double biomeMultiplier(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (ClimateHelper.inAny(biome, ArcforgeConfig.SOLAR_HOT_BIOME_TAGS.get())) {
            return ArcforgeConfig.SOLAR_HOT_BIOME_MULTIPLIER.getAsDouble();
        }
        if (ClimateHelper.isCold(level, pos, ArcforgeConfig.SOLAR_COLD_BIOME_TAGS.get())) {
            return ArcforgeConfig.SOLAR_COLD_BIOME_MULTIPLIER.getAsDouble();
        }
        return 1.0;
    }

    // Rain or snow over any collector (none in a biome where it doesn't rain); a thunderstorm only counts
    // where it's raining or snowing.
    private SolarModel.Weather weatherAt(Level level) {
        boolean rain = false, snow = false;
        for (int i = 0; i < SolarModel.COLLECTORS; i++) {
            Biome.Precipitation precipitation = level.precipitationAt(tower.collector(i).above());
            rain |= precipitation == Biome.Precipitation.RAIN;
            snow |= precipitation == Biome.Precipitation.SNOW;
        }
        if ((rain || snow) && level.isThundering()) {
            return SolarModel.Weather.THUNDER;
        }
        return rain ? SolarModel.Weather.RAIN : snow ? SolarModel.Weather.SNOW : SolarModel.Weather.CLEAR;
    }

    // Heat out of the face of every heat port, into whatever cooler heat handler it touches, at the contact
    // rate or the array's output, whichever is more.
    private void pushHeat(ServerLevel level) {
        if (targetsDirty) {
            targetsDirty = false;
            heatTargets.clear();
            for (MultiblockPorts.Port port : MultiblockPorts.list(level, this)) {
                if (port.mode() != SideMode.HEAT) {
                    continue;
                }
                heatTargets.add(BlockCapabilityCache.create(ModCapabilities.HEAT, level, port.pos().relative(port.face()), port.face().getOpposite()));
            }
        }
        // At least what it's making, so none of it is lost when the contact rate is lower.
        int rate = Math.max(ArcforgeConfig.HEAT_CONTACT_RATE.getAsInt(), heatPerTick);
        for (BlockCapabilityCache<HeatHandler, @Nullable Direction> target : heatTargets) {
            HeatHandler handler = target.getCapability();
            if (handler != null) {
                MachineOutputs.moveHeat(heat, handler, rate);
            }
        }
    }

    @Override
    protected void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(SolarThermalArrayControllerBlock.LIT) != lit && state.getValue(SolarThermalArrayControllerBlock.FORMED)) {
            level.setBlock(worldPosition, state.setValue(SolarThermalArrayControllerBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public int getHeatPerTick() {
        return heatPerTick;
    }

    public int getReceiverTemperature() {
        return receiverTemperature;
    }

    public boolean isStowed() {
        return stowed;
    }

    public int getSkyCount() {
        return Integer.bitCount(skyMask);
    }

    // --- Client side: the trough ---

    // The trough's angle in degrees at this moment, easing toward target (-180..180, 0 facing up) at up to
    // speed degrees a tick the short way round, so it never snaps when the chunk is redrawn.
    public float advancePanelAngle(double time, float target, float speed) {
        if (Float.isNaN(clientAngle) || clientTime < 0) {
            clientAngle = target;
        } else {
            float step = (float) Math.max(0.0, time - clientTime) * speed;
            float difference = Mth.wrapDegrees(target - clientAngle);
            clientAngle = Mth.wrapDegrees(clientAngle + Mth.clamp(difference, -step, step));
        }
        clientTime = time;
        return clientAngle;
    }

    // The tower and a block round it, which the trough swings through.
    public AABB getRenderBox() {
        return tower != null ? AABB.encapsulatingFullBlocks(tower.min(), tower.max()).inflate(1.0, 0.0, 1.0).expandTowards(0.0, 1.0, 0.0)
                : new AABB(worldPosition);
    }

    // --- Capabilities (MultiblockController) ---

    public @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return heatOutput;
        }
        return faceMode(pos, side) == SideMode.HEAT ? heatOutput : null;
    }

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return type == ConduitType.THERMAL && mode == SideMode.HEAT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tower = input.getLong("tower_min").map(min -> new SolarThermalStructure.Tower(BlockPos.of(min),
                Direction.Axis.byName(input.getStringOr("tower_axis", "z")))).orElse(null);
        heat.deserialize(input);
        portDefaults.load(input);
        receiverTemperature = input.getIntOr("receiver_temperature", HeatBuffer.AMBIENT_CELSIUS);
        stowed = input.getBooleanOr("stowed", false);
        skyMask = input.getIntOr("sky_mask", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (tower != null) {
            output.putLong("tower_min", tower.min().asLong());
            output.putString("tower_axis", tower.axis().getSerializedName());
        }
        heat.serialize(output);
        portDefaults.save(output);
        output.putInt("receiver_temperature", receiverTemperature);
        output.putBoolean("stowed", stowed);
        output.putInt("sky_mask", skyMask);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.solar_thermal_array");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SolarThermalArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
