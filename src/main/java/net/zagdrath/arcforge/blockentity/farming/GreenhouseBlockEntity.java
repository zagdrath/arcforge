/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.farming.CropRotation;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.farming.greenhouse.GrowLampBlock;
import net.zagdrath.arcforge.block.farming.greenhouse.PlantingBedBlock;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.farming.ClochePlants;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.heat.SolarModel;
import net.zagdrath.arcforge.item.farming.FertilizerItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.GreenhouseMenu;
import net.zagdrath.arcforge.multiblock.GreenhouseStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Greenhouse Array (see GreenhouseStructure), run by its controller. Every Planting Bed grows its crop on its own
// (ClochePlants: the bed's seed in its soil), harvesting into the output slots and growing again. A bed's growth per tick
// is greenhouse.baseSpeed x the soil's growth x the temperature factor x the bonuses it started its harvest with, and it
// only grows while it has light: sky light by day, or a lit Grow Lamp within lampRange of it.
//
// Systems, all through ports (Input faces take items and every fluid; Heat and Energy faces the rest):
//  - Water (required): each harvest takes waterPerHarvest as it starts; without it nothing starts.
//  - Nutrients (optional): Nutrient Solution (nutrientPerHarvest a harvest, x nutrientBonus), or else a fertilizer point
//    from the fertilizer slot (x fertilizerBonus; NPK Fertilizer's are x nutrientBonus). Legumes take neither and always
//    get x nutrientBonus.
//  - Carbon Dioxide (optional): co2PerHarvest a harvest, x co2Bonus.
//  - Heat (optional): the air inside drifts toward the outside temperature (the biome's, a set cold in dimensions without
//    a sky, and solarGain warmer by day under the sky); with HU in its heat buffer it heats itself up to
//    targetTemperature. Crops grow fastest between idealMin and idealMax (18-30°C) and slower the further outside.
//  - Grow Lamps (optional): at night or without sky, lit lamps draw lampEnergyPerTick FE each, and light the beds round them.
public class GreenhouseBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int SLOT_FERTILIZER = 0;
    public static final int SLOT_OUTPUT_FIRST = 1;
    public static final int OUTPUT_SLOTS = 9;
    public static final int MACHINE_SLOTS = SLOT_OUTPUT_FIRST + OUTPUT_SLOTS;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY, SideMode.HEAT);
    // How often the beds' sky light is looked at, in ticks.
    private static final int LIGHT_INTERVAL = 20;

    // What each system is doing, for the GUI and Jade: its LED.
    public enum SystemState {
        OFF("led_off"), ON("led_running"), IDLE("led_idle"), BLOCKED("led_blocked");

        private final String led;

        SystemState(String led) {
            this.led = led;
        }

        public String led() {
            return led;
        }

        public static SystemState byId(int id) {
            SystemState[] values = values();
            return id >= 0 && id < values.length ? values[id] : OFF;
        }
    }

    private GreenhouseStructure.@Nullable House house;
    private boolean checkRequested = true;
    private int checkTimer;
    // Found when it forms or rechecks.
    private final List<BlockPos> beds = new ArrayList<>();
    private final List<BlockPos> lamps = new ArrayList<>();
    private final Set<BlockPos> lampLit = new HashSet<>();
    private final Set<BlockPos> sunlit = new HashSet<>();

    private final ConsumerEnergyHandler energy;
    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank water;
    private final FilteredFluidTank nutrients;
    private final FilteredFluidTank co2;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    // The air inside, °C (NaN until it first forms: it starts at the outside temperature).
    private double temperature = Double.NaN;
    private double ambient;
    private int fertilizer;
    private boolean enriched;
    private boolean lampsOn;
    private boolean running;
    // This tick, for the GUI.
    private int energyUsage;
    private int heatUsage;
    private int growing;
    private SystemState waterState = SystemState.OFF, nutrientState = SystemState.OFF, co2State = SystemState.OFF,
            heatState = SystemState.OFF, lampState = SystemState.OFF;
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    public GreenhouseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GREENHOUSE.get(), pos, state, MACHINE_SLOTS, GreenhouseBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE), SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.GREENHOUSE_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.GREENHOUSE_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.heat = new HeatBuffer(ArcforgeConfig.GREENHOUSE_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.GREENHOUSE_HEAT_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        this.water = new FilteredFluidTank(ArcforgeConfig.GREENHOUSE_WATER_CAPACITY.getAsInt(),
                resource -> resource.getFluid().defaultFluidState().is(FluidTags.WATER), this::setChanged);
        this.nutrients = new FilteredFluidTank(ArcforgeConfig.GREENHOUSE_NUTRIENT_CAPACITY.getAsInt(),
                resource -> resource.getFluid() == ModFluids.NUTRIENT_SOLUTION.get(), this::setChanged);
        this.co2 = new FilteredFluidTank(ArcforgeConfig.GREENHOUSE_CO2_CAPACITY.getAsInt(),
                resource -> resource.getFluid() == ModFluids.CARBON_DIOXIDE.get(), this::setChanged);
        // Each tank takes only its own fluid, so one Input face takes all three.
        this.fluidInput = new AutomationResourceHandler<>(new CombinedResourceHandler<>(water, nutrients, co2), index -> true, index -> false);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_FERTILIZER, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, GreenhouseBlockEntity::isOutputSlot);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_FERTILIZER, GreenhouseBlockEntity::isOutputSlot);
        this.data = new WideIntContainerData(GreenhouseMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case GreenhouseMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case GreenhouseMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case GreenhouseMenu.DATA_ENERGY_USAGE -> energyUsage;
                    case GreenhouseMenu.DATA_WATER -> water.getAmount();
                    case GreenhouseMenu.DATA_WATER_CAPACITY -> water.getCapacity();
                    case GreenhouseMenu.DATA_NUTRIENTS -> nutrients.getAmount();
                    case GreenhouseMenu.DATA_NUTRIENTS_CAPACITY -> nutrients.getCapacity();
                    case GreenhouseMenu.DATA_CO2 -> co2.getAmount();
                    case GreenhouseMenu.DATA_CO2_CAPACITY -> co2.getCapacity();
                    case GreenhouseMenu.DATA_HEAT -> heat.getStored();
                    case GreenhouseMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case GreenhouseMenu.DATA_HEAT_USAGE -> heatUsage;
                    case GreenhouseMenu.DATA_TEMPERATURE -> (int) Math.round(getTemperature() * 10);
                    case GreenhouseMenu.DATA_AMBIENT -> (int) Math.round(ambient * 10);
                    case GreenhouseMenu.DATA_SPEED -> (int) Math.round(currentSpeed() * 100);
                    case GreenhouseMenu.DATA_BEDS -> beds.size();
                    case GreenhouseMenu.DATA_GROWING -> growing;
                    case GreenhouseMenu.DATA_LAMPS -> lamps.size();
                    case GreenhouseMenu.DATA_FERTILIZER -> fertilizer;
                    case GreenhouseMenu.DATA_SYSTEM_WATER -> waterState.ordinal();
                    case GreenhouseMenu.DATA_SYSTEM_NUTRIENTS -> nutrientState.ordinal();
                    case GreenhouseMenu.DATA_SYSTEM_CO2 -> co2State.ordinal();
                    case GreenhouseMenu.DATA_SYSTEM_HEAT -> heatState.ordinal();
                    case GreenhouseMenu.DATA_SYSTEM_LAMPS -> lampState.ordinal();
                    case GreenhouseMenu.DATA_STATUS -> status.ordinal();
                    case GreenhouseMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case GreenhouseMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static boolean isOutputSlot(int slot) {
        return slot >= SLOT_OUTPUT_FIRST && slot < MACHINE_SLOTS;
    }

    // The fertilizer slot takes fertilizers and bone meal; the harvest only comes out.
    public static boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_FERTILIZER && ClocheBlockEntity.fertilizerPoints(resource.toStack(1)) > 0;
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, GreenhouseBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    // --- Structure ---

    @Override
    public void onLoad() {
        super.onLoad();
        GreenhouseStructure.register(this);
    }

    @Override
    public void setRemoved() {
        GreenhouseStructure.unregister(this);
        super.setRemoved();
    }

    @Override
    public boolean isFormed() {
        return house != null;
    }

    public GreenhouseStructure.@Nullable House getHouse() {
        return house;
    }

    public void requestCheck() {
        checkRequested = true;
    }

    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            checkRequested = false;
            checkTimer = 0;
            updateFormed(serverLevel);
        }
    }

    // Breaking the controller un-forms the rest of the greenhouse.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && house != null) {
            GreenhouseStructure.unform(serverLevel, house, pos);
            house = null;
        }
    }

    private void updateFormed(ServerLevel level) {
        GreenhouseStructure.House found = house != null && GreenhouseStructure.isValid(level, house, worldPosition, house)
                ? house : GreenhouseStructure.find(level, worldPosition, house);
        if (Objects.equals(found, house)) {
            if (found != null) {
                findParts(level);
            }
            return;
        }
        if (house != null) {
            GreenhouseStructure.unform(level, house, null);
        }
        house = found;
        beds.clear();
        lamps.clear();
        sunlit.clear();
        // Unforming turned the lamps off.
        lampsOn = false;
        if (found != null) {
            GreenhouseStructure.form(level, found, worldPosition);
            findParts(level);
            if (Double.isNaN(temperature)) {
                temperature = outsideTemperature(level);
            }
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        running = false;
        setChanged();
        // Clients learn the shape too, so Jade can name the machine from any of its blocks.
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // The beds in the floor and the lamps under the roof, and which beds each lamp reaches.
    private void findParts(ServerLevel level) {
        beds.clear();
        lamps.clear();
        lampLit.clear();
        if (house == null) {
            return;
        }
        int floor = house.min().getY();
        int range = ArcforgeConfig.GREENHOUSE_LAMP_RANGE.getAsInt();
        for (BlockPos pos : house.positions()) {
            BlockState state = level.getBlockState(pos);
            if (pos.getY() == floor && state.getBlock() instanceof PlantingBedBlock) {
                beds.add(pos.immutable());
            } else if (pos.getY() == house.lampLayer() && state.getBlock() instanceof GrowLampBlock) {
                lamps.add(pos.immutable());
            }
        }
        for (BlockPos bed : beds) {
            for (BlockPos lamp : lamps) {
                if (Math.abs(lamp.getX() - bed.getX()) <= range && Math.abs(lamp.getZ() - bed.getZ()) <= range) {
                    lampLit.add(bed);
                    break;
                }
            }
        }
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return house != null ? house.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return house != null ? house.max() : worldPosition;
    }

    // The blocks of the greenhouse (not the air inside it).
    @Override
    public boolean isPart(BlockPos pos) {
        return house != null && house.contains(pos) && (house.boundaries(pos) > 0 || lamps.contains(pos));
    }

    // --- Climate ---

    // Outside, °C: the biome's temperature scaled, or noSkyAmbient in a dimension without a sky (the Nether reads cold).
    private double outsideTemperature(ServerLevel level) {
        if (!level.dimensionType().hasSkyLight()) {
            return ArcforgeConfig.GREENHOUSE_NO_SKY_AMBIENT.getAsInt();
        }
        float biome = level.getBiome(worldPosition).value().getBaseTemperature();
        return Math.clamp(biome * ArcforgeConfig.GREENHOUSE_CELSIUS_PER_BIOME_TEMPERATURE.getAsDouble(), -40.0, 60.0);
    }

    private static boolean isDay(ServerLevel level) {
        return level.dimensionType().hasSkyLight() && !SolarModel.isNight(level.getDefaultClockTime());
    }

    // The air drifts toward outside (warmer by day under the glass), and heat from the buffer warms it to the target.
    private void updateClimate(ServerLevel level) {
        heatUsage = 0;
        double outside = outsideTemperature(level);
        boolean day = isDay(level);
        ambient = outside + (day && !sunlit.isEmpty() ? ArcforgeConfig.GREENHOUSE_SOLAR_GAIN.getAsInt() : 0);
        if (Double.isNaN(temperature)) {
            temperature = ambient;
        }
        temperature += (ambient - temperature) * ArcforgeConfig.GREENHOUSE_HEAT_EXCHANGE.getAsDouble();
        int target = ArcforgeConfig.GREENHOUSE_TARGET_TEMPERATURE.getAsInt();
        int needed = heatPerTick();
        if (temperature < target && heat.getStored() >= needed) {
            heatUsage = heat.remove(needed);
            temperature = Math.min(target, temperature + ArcforgeConfig.GREENHOUSE_HEATING_RATE.getAsDouble());
            heatState = SystemState.ON;
        } else if (heat.getStored() >= needed) {
            heatState = SystemState.IDLE;
        } else {
            heatState = temperatureFactor() < 1.0 ? SystemState.BLOCKED : SystemState.OFF;
        }
    }

    // HU/t while heating: heatBase plus heatPerSurface for each block of walls and roof.
    public int heatPerTick() {
        int surface = house != null ? house.surface() : 0;
        return ArcforgeConfig.GREENHOUSE_HEAT_BASE.getAsInt() + (int) Math.ceil(surface * ArcforgeConfig.GREENHOUSE_HEAT_PER_SURFACE.getAsDouble());
    }

    public double getTemperature() {
        return Double.isNaN(temperature) ? ambient : temperature;
    }

    // 1 in the ideal range, falling off outside it (to 0).
    public double temperatureFactor() {
        return temperatureFactor(getTemperature());
    }

    public static double temperatureFactor(double celsius) {
        int min = ArcforgeConfig.GREENHOUSE_IDEAL_MIN.getAsInt();
        int max = ArcforgeConfig.GREENHOUSE_IDEAL_MAX.getAsInt();
        double outside = celsius < min ? min - celsius : celsius > max ? celsius - max : 0;
        return Math.max(0.0, 1.0 - outside * ArcforgeConfig.GREENHOUSE_TEMPERATURE_FALLOFF.getAsDouble());
    }

    // The growth speed a harvest starting now would get (before the soil): base x temperature x nutrients x CO2.
    public double currentSpeed() {
        if (house == null) {
            return 0;
        }
        return ArcforgeConfig.GREENHOUSE_BASE_SPEED.getAsDouble() * temperatureFactor() * nutrientBonus(false) * co2Bonus(false);
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        if (checkRequested || ++checkTimer >= ArcforgeConfig.GREENHOUSE_CHECK_INTERVAL.getAsInt()) {
            checkNow();
        }
        energyUsage = 0;
        growing = 0;
        if (house == null) {
            status = MachineStatus.NOT_FORMED;
            heatUsage = 0;
            setRunning(false);
            setLamps(level, false);
            return;
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % LIGHT_INTERVAL == 0) {
            updateSunlight(level);
        }
        updateClimate(level);
        if (!canRun(level)) {
            status = stoppedStatus();
            setLamps(level, false);
        } else {
            status = grow(level);
        }
        setRunning(status == MachineStatus.GROWING);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this);
        }
    }

    private void updateSunlight(ServerLevel level) {
        sunlit.clear();
        boolean day = isDay(level);
        int min = ArcforgeConfig.GREENHOUSE_MIN_SKY_LIGHT.getAsInt();
        for (BlockPos bed : beds) {
            if (day && level.getBrightness(LightLayer.SKY, bed.above()) >= min) {
                sunlit.add(bed);
            }
        }
    }

    private MachineStatus grow(ServerLevel level) {
        int per = ArcforgeConfig.GREENHOUSE_WATER_PER_HARVEST.getAsInt();
        waterState = water.getAmount() >= per ? SystemState.ON : SystemState.BLOCKED;
        int solution = ArcforgeConfig.GREENHOUSE_NUTRIENT_PER_HARVEST.getAsInt();
        nutrientState = solution > 0 && nutrients.getAmount() >= solution || fertilizer > 0
                || ClocheBlockEntity.fertilizerPoints(items.getStack(SLOT_FERTILIZER)) > 0 ? SystemState.ON : SystemState.OFF;
        co2State = co2.getAmount() >= ArcforgeConfig.GREENHOUSE_CO2_PER_HARVEST.getAsInt() ? SystemState.ON : SystemState.OFF;

        // Lamps: lit while a planted bed they reach has no sun, if there's FE for all of them.
        boolean lampsWanted = false;
        for (BlockPos pos : beds) {
            if (!sunlit.contains(pos) && lampLit.contains(pos) && bed(level, pos) instanceof PlantingBedBlockEntity bed && bed.plant() != null) {
                lampsWanted = true;
                break;
            }
        }
        int lampCost = lamps.size() * ArcforgeConfig.GREENHOUSE_LAMP_ENERGY.getAsInt();
        boolean lit = lampsWanted && (lampCost == 0 || energy.consume(lampCost));
        if (lit) {
            energyUsage = lampCost;
        }
        setLamps(level, lit);
        lampState = lamps.isEmpty() ? SystemState.OFF : lit ? SystemState.ON : lampsWanted ? SystemState.BLOCKED : SystemState.IDLE;

        double temperatureFactor = temperatureFactor();
        double base = ArcforgeConfig.GREENHOUSE_BASE_SPEED.getAsDouble() * temperatureFactor;
        boolean planted = false, noWater = false, dark = false, full = false, harvestedAll = false;
        for (BlockPos pos : beds) {
            if (!(bed(level, pos) instanceof PlantingBedBlockEntity bed)) {
                continue;
            }
            ClochePlants.Plant plant = bed.plant();
            if (plant == null) {
                if (bed.getPlantKey() != null || bed.growth() > 0) {
                    bed.restart(null);
                    bed.syncGrowth();
                }
                continue;
            }
            planted = true;
            if (!plant.key().equals(bed.getPlantKey())) {
                bed.restart(plant.key());
            }
            bed.setTotal(plant.time());
            if (!bed.isCycling()) {
                if (water.getAmount() < per) {
                    noWater = true;
                    continue;
                }
                int fluidsBefore = water.getAmount() + nutrients.getAmount() + co2.getAmount();
                int fertilizerBefore = items.getStack(SLOT_FERTILIZER).getCount();
                drain(water, per);
                // Legumes need no Nutrient Solution or fertilizer, and grow as if they had Nutrient Solution (CropRotation).
                double nutrientBonus = CropRotation.isLegumeSeed(bed.getSeed()) ? ArcforgeConfig.GREENHOUSE_NUTRIENT_BONUS.getAsDouble() : nutrientBonus(true);
                bed.startCycle((float) (nutrientBonus * co2Bonus(true)));
                // What a harvest takes as it starts (its harvest is counted as the operation when it comes out).
                controlState.consumedFluid(fluidsBefore - water.getAmount() - nutrients.getAmount() - co2.getAmount());
                controlState.consumedItems(fertilizerBefore - items.getStack(SLOT_FERTILIZER).getCount());
            }
            if (!bed.isGrown()) {
                if (!sunlit.contains(pos) && !(lit && lampLit.contains(pos))) {
                    dark = true;
                    continue;
                }
                if (base <= 0) {
                    continue;
                }
                bed.grow(base * bed.soilGrowth() * bed.getCycleBonus());
                growing++;
            }
            if (bed.isGrown()) {
                if (harvest(level, bed, plant, lit && lampLit.contains(pos) && !sunlit.contains(pos))) {
                    bed.harvested();
                } else {
                    bed.setWaiting(true);
                    full = true;
                }
            }
            bed.syncGrowth();
        }
        setChanged();
        if (!planted) {
            return MachineStatus.NO_CROPS;
        }
        if (growing > 0) {
            return MachineStatus.GROWING;
        }
        if (full) {
            return MachineStatus.OUTPUT_FULL;
        }
        if (noWater) {
            return MachineStatus.NO_WATER;
        }
        if (temperatureFactor <= 0) {
            return getTemperature() < ArcforgeConfig.GREENHOUSE_IDEAL_MIN.getAsInt() ? MachineStatus.TOO_COLD : MachineStatus.TOO_HOT;
        }
        return dark ? MachineStatus.NO_LIGHT : MachineStatus.IDLE;
    }

    private @Nullable PlantingBedBlockEntity bed(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PlantingBedBlockEntity bed ? bed : null;
    }

    // The nutrient multiplier: Nutrient Solution first, else a fertilizer point. take: use it up (a harvest starting).
    private double nutrientBonus(boolean take) {
        int solution = ArcforgeConfig.GREENHOUSE_NUTRIENT_PER_HARVEST.getAsInt();
        if (solution > 0 && nutrients.getAmount() >= solution) {
            if (take) {
                drain(nutrients, solution);
            }
            return ArcforgeConfig.GREENHOUSE_NUTRIENT_BONUS.getAsDouble();
        }
        if (fertilizer <= 0) {
            ItemStack stack = items.getStack(SLOT_FERTILIZER);
            int points = ClocheBlockEntity.fertilizerPoints(stack);
            if (points <= 0) {
                return 1.0;
            }
            if (!take) {
                return stack.getItem() instanceof FertilizerItem item && item.enriches()
                        ? ArcforgeConfig.GREENHOUSE_NUTRIENT_BONUS.getAsDouble() : ArcforgeConfig.GREENHOUSE_FERTILIZER_BONUS.getAsDouble();
            }
            fertilizer = points;
            enriched = stack.getItem() instanceof FertilizerItem item && item.enriches();
            items.setStack(SLOT_FERTILIZER, stack.copyWithCount(stack.getCount() - 1));
        }
        if (take) {
            fertilizer--;
        }
        return enriched ? ArcforgeConfig.GREENHOUSE_NUTRIENT_BONUS.getAsDouble() : ArcforgeConfig.GREENHOUSE_FERTILIZER_BONUS.getAsDouble();
    }

    private double co2Bonus(boolean take) {
        int per = ArcforgeConfig.GREENHOUSE_CO2_PER_HARVEST.getAsInt();
        if (co2.getAmount() < per) {
            return 1.0;
        }
        if (take) {
            drain(co2, per);
        }
        return ArcforgeConfig.GREENHOUSE_CO2_BONUS.getAsDouble();
    }

    private static void drain(FilteredFluidTank tank, int amount) {
        if (amount <= 0 || tank.getAmount() <= 0) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            tank.extract(0, tank.getResource(0), amount, tx);
            tx.commit();
        }
    }

    // Puts one harvest in the output slots if it all fits.
    private boolean harvest(ServerLevel level, PlantingBedBlockEntity bed, ClochePlants.Plant plant, boolean byLamp) {
        List<ItemStack> harvest = plant.harvest(level, bed.getBlockPos(), level.getRandom());
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            slots.add(items.getStack(SLOT_OUTPUT_FIRST + i).copy());
        }
        for (ItemStack stack : harvest) {
            ItemStack left = stack.copy();
            for (int i = 0; i < OUTPUT_SLOTS && !left.isEmpty(); i++) {
                ItemStack slot = slots.get(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, left)) {
                    int moved = Math.min(left.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    left.shrink(moved);
                }
            }
            for (int i = 0; i < OUTPUT_SLOTS && !left.isEmpty(); i++) {
                if (slots.get(i).isEmpty()) {
                    int moved = Math.min(left.getCount(), left.getMaxStackSize());
                    slots.set(i, left.copyWithCount(moved));
                    left.shrink(moved);
                }
            }
            if (!left.isEmpty()) {
                return false;
            }
        }
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            items.setStack(SLOT_OUTPUT_FIRST + i, slots.get(i));
        }
        controlState.completed(harvest.stream().map(ItemStack::copy).toList(), List.of(), 0, 0);
        if (!harvest.isEmpty()) {
            ItemStack first = harvest.getFirst();
            ArcforgeAdvancements.produced(this, first, null, null);
            if (byLamp) {
                ArcforgeAdvancements.produced(this, first, null, "greenhouse_lamps");
            }
            if (fullyFed()) {
                ArcforgeAdvancements.produced(this, first, null, "greenhouse_fully_fed");
            }
        }
        return true;
    }

    // Every system connected and working at once: heat (warm enough), nutrients, Carbon Dioxide and lamps.
    private boolean fullyFed() {
        return heatState != SystemState.OFF && heatState != SystemState.BLOCKED && temperatureFactor() >= 1.0
                && nutrientState == SystemState.ON && co2State == SystemState.ON && !lamps.isEmpty() && lampState != SystemState.BLOCKED;
    }

    // Sets every lamp's LIT (each is checked, so a new lamp or one a re-form turned off catches up).
    private void setLamps(ServerLevel level, boolean on) {
        lampsOn = on;
        for (BlockPos lamp : lamps) {
            BlockState state = level.getBlockState(lamp);
            if (state.getBlock() instanceof GrowLampBlock && state.getValue(GrowLampBlock.LIT) != on) {
                level.setBlock(lamp, state.setValue(GrowLampBlock.LIT, on), Block.UPDATE_ALL);
            }
        }
    }

    private void setRunning(boolean running) {
        if (this.running != running) {
            this.running = running;
            setLit(running);
        }
    }

    @Override
    protected void setLit(boolean lit) {
        if (house != null || !lit) {
            super.setLit(lit);
        }
    }

    // --- For the GUI, Jade and tests ---

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public FilteredFluidTank getNutrients() {
        return nutrients;
    }

    public FilteredFluidTank getCo2() {
        return co2;
    }

    public List<BlockPos> getBeds() {
        return beds;
    }

    public List<BlockPos> getLamps() {
        return lamps;
    }

    public int getGrowing() {
        return growing;
    }

    public int getEnergyUsage() {
        return energyUsage;
    }

    public int getHeatUsage() {
        return heatUsage;
    }

    public boolean areLampsOn() {
        return lampsOn;
    }

    public SystemState getWaterState() {
        return waterState;
    }

    public SystemState getNutrientState() {
        return nutrientState;
    }

    public SystemState getCo2State() {
        return co2State;
    }

    public SystemState getHeatState() {
        return heatState;
    }

    public SystemState getLampState() {
        return lampState;
    }

    // For tests: set the air inside.
    public void setTemperature(double celsius) {
        temperature = celsius;
        setChanged();
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.INPUT ? fluidInput : null;
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
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
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID, GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
        };
    }

    // Conduits next to the controller ask it about the wall it lies in.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        if (level != null && house != null) {
            MultiblockAutomation.refresh(level, house.min(), house.max());
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        house = input.getLong("house_min").flatMap(min -> input.getLong("house_max").map(max -> new GreenhouseStructure.House(BlockPos.of(min),
                BlockPos.of(max), Direction.from2DDataValue(input.getIntOr("house_front", 0))))).orElse(null);
        energy.deserialize(input.childOrEmpty("energy"));
        heat.deserialize(input);
        water.deserialize(input.childOrEmpty("water"));
        nutrients.deserialize(input.childOrEmpty("nutrients"));
        co2.deserialize(input.childOrEmpty("co2"));
        temperature = input.getDoubleOr("temperature", Double.NaN);
        fertilizer = input.getIntOr("fertilizer", 0);
        enriched = input.getBooleanOr("enriched", false);
        lampsOn = input.getBooleanOr("lamps_on", false);
        running = input.getBooleanOr("running", false);
        portDefaults.load(input);
        // The beds and lamps are found again on the first check.
        checkRequested = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (house != null) {
            output.putLong("house_min", house.min().asLong());
            output.putLong("house_max", house.max().asLong());
            output.putInt("house_front", house.front().get2DDataValue());
        }
        energy.serialize(output.child("energy"));
        heat.serialize(output);
        water.serialize(output.child("water"));
        nutrients.serialize(output.child("nutrients"));
        co2.serialize(output.child("co2"));
        if (!Double.isNaN(temperature)) {
            output.putDouble("temperature", temperature);
        }
        output.putInt("fertilizer", fertilizer);
        output.putBoolean("enriched", enriched);
        output.putBoolean("lamps_on", lampsOn);
        output.putBoolean("running", running);
        portDefaults.save(output);
    }

    // Clients get the saved state when the chunk loads and whenever the structure forms or breaks up.
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
        return Component.translatable("container.arcforge.greenhouse");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GreenhouseMenu(containerId, inventory, worldPosition, items, data);
    }
}
