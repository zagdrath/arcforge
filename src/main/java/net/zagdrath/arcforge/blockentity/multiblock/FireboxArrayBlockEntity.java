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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.heat.OxyFuel;
import net.zagdrath.arcforge.machine.CombustionFuel;
import net.zagdrath.arcforge.machine.MachineOutputs;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.FireboxArrayMenu;
import net.zagdrath.arcforge.multiblock.FireboxArrayStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.heat.FlueGas;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Firebox Array (see FireboxArrayStructure), run by its controller: a big firebox that burns solid fuels and Fuel
// Burner fuels into heat.
//  - It makes heatPerTickPerBlock (160) HU/t for every block of its volume while it burns: 4,320 HU/t for a 3x3x3,
//    50,400 for a 7x5x9. Its heat buffer, fuel tank and oxygen tank scale with the volume too.
//  - Solid fuels (#arcforge:combustion_fuel) give the heat they give in a Firebox (coal 64,000 HU) and burn at 1,100°C;
//    Coal Coke gives 1.5 times a coal's (and its block 1.5 times a coal block's) and burns at 1,300°C. Liquid and gas
//    fuels (arcforge:burner_fuels) give their HU per mB, at their own burn temperature (or the array's maximum).
//  - It burns the item it started until it is spent; between items it takes its tank's fuel first, then the next item.
//  - It pauses while its buffer is as hot as the fuel burns (the Fuel Burner's rule), so heat isn't wasted.
//  - Fed oxygen through an Oxygen port it burns on oxy-fuel (see OxyFuel): 300°C hotter (up to 1,600°C) and 1.25 times
//    the heat from each item or mB, using oxygenPerThousandHeat mB for every 1,000 HU.
//  - With a Gas Output port (named Flue Gas) it gives off Carbon Dioxide through it while burning carbon fuels, in
//    proportion to the heat made (see FlueGas); with none, nothing changes.
// It does IO only through its ports: fuel items, liquids and gases in (Input), oxygen in (Oxygen), heat out (Heat). The
// controller also takes fuel from a held bucket. Clients get the box and whether it burns, for the renderer's fire.
public class FireboxArrayBlockEntity extends MachineBlockEntity implements MultiblockController, FluidInteractable {
    public static final int SLOT_FUEL = 0;
    public static final int MACHINE_SLOTS = 1;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OXYGEN, SideMode.HEAT, SideMode.GAS_OUTPUT);
    private static final int REDSTONE_CHECK_INTERVAL = 10;
    // How often it rechecks its box anyway (something placed inside it breaks it).
    private static final int STRUCTURE_CHECK_INTERVAL = 40;
    // Clients hear about the fire when it changes, and about its strength at most this often.
    private static final int SYNC_INTERVAL = 10;

    private FireboxArrayStructure.@Nullable Box box;
    private boolean checkRequested = true;
    private boolean powered;
    // Client: the renderer's window tiles (see windowQuads), the box they were found for, and when.
    private static final int WINDOW_REFRESH_INTERVAL = 20;
    private @Nullable Set<Long> windowQuads;
    private FireboxArrayStructure.@Nullable Box windowQuadsBox;
    private long windowQuadsTime;

    // Replaced (keeping what it holds) when the box's size changes.
    private HeatBuffer heat;
    private HeatHandler heatOutput;
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> fuelInput;
    private final ResourceHandler<ItemResource> itemInput;
    private final OxyFuel oxy;
    private final ContainerData data;
    private final List<BlockCapabilityCache<HeatHandler, @Nullable Direction>> heatTargets = new ArrayList<>();
    private final List<BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>> flueTargets = new ArrayList<>();
    private final FlueGas flue;
    private boolean targetsDirty = true;

    // The solid fuel burning: its heat left (unmultiplied HU), its whole heat, its temperature and the item.
    private double solidLeft;
    private int solidTotal;
    private int solidCelsius;
    private @Nullable Item burning;
    // Fuel taken from the tank (in whole mB) but not burnt yet.
    private double fuelTaken;
    // mB of oxygen this tick of oxy-fuel takes (set before each burn).
    private double oxygenRate;
    private int heatPerTick;
    private int burnCelsius;
    private boolean running;
    private boolean syncedRunning;
    private long lastSync;
    // Gives the structure its first ports (none for a new one; see MultiblockPorts.Defaults).
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    public FireboxArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FIREBOX_ARRAY.get(), pos, state, MACHINE_SLOTS, FireboxArrayBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE),
                SIDE_MODES);
        int volume = 27;
        this.heat = newHeat(volume);
        this.heatOutput = heat.output();
        this.tank = new FilteredFluidTank(ArcforgeConfig.FIREBOX_ARRAY_TANK_PER_BLOCK.getAsInt() * volume, resource -> BurnerFuel.of(resource) != null,
                this::setChanged);
        this.fuelInput = new AutomationResourceHandler<>(tank, index -> true, index -> false);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_FUEL, slot -> false);
        this.oxy = new OxyFuel(ArcforgeConfig.FIREBOX_ARRAY_OXYGEN_TANK_PER_BLOCK.getAsInt() * volume, () -> oxygenRate, this::setChanged);
        this.flue = new FlueGas(ArcforgeConfig.FLUE_GAS_ARRAY_TANK_CAPACITY.getAsInt(), this::setChanged);
        this.data = new WideIntContainerData(FireboxArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case FireboxArrayMenu.DATA_HEAT -> heat.getStored();
                    case FireboxArrayMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case FireboxArrayMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case FireboxArrayMenu.DATA_BURN_TEMPERATURE -> burnCelsius;
                    case FireboxArrayMenu.DATA_HEAT_PER_TICK -> heatPerTick;
                    case FireboxArrayMenu.DATA_MAX_HEAT_PER_TICK -> maxHeatPerTick();
                    case FireboxArrayMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case FireboxArrayMenu.DATA_FLUID_AMOUNT -> tank.getAmount();
                    case FireboxArrayMenu.DATA_FLUID_CAPACITY -> tank.getCapacity();
                    case FireboxArrayMenu.DATA_OXYGEN -> oxy.getTank().getAmount();
                    case FireboxArrayMenu.DATA_OXYGEN_CAPACITY -> oxy.getTank().getCapacity();
                    case FireboxArrayMenu.DATA_OXY_ACTIVE -> oxy.isActive() ? 1 : 0;
                    case FireboxArrayMenu.DATA_SOLID_LEFT -> (int) Math.ceil(solidLeft);
                    case FireboxArrayMenu.DATA_SOLID_TOTAL -> solidTotal;
                    case FireboxArrayMenu.DATA_BURNING_ITEM -> burning == null ? -1 : BuiltInRegistries.ITEM.getId(burning);
                    case FireboxArrayMenu.DATA_VOLUME -> volume();
                    case FireboxArrayMenu.DATA_STATUS -> status.ordinal();
                    case FireboxArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case FireboxArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The fuel slot takes solid fuels: #arcforge:combustion_fuel, Coal Coke and its block.
    public static boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_FUEL && isSolidFuel(resource.toStack(1));
    }

    public static boolean isSolidFuel(ItemStack stack) {
        return CombustionFuel.isFuel(stack) || isCoke(stack);
    }

    private static boolean isCoke(ItemStack stack) {
        return stack.is(ModItems.COAL_COKE.get()) || stack.is(ModItems.COAL_COKE_BLOCK.get());
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, FireboxArrayBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    // --- The numbers (see the class comment) ---

    public int volume() {
        return box != null ? box.volume() : 27;
    }

    public int maxHeatPerTick() {
        return (int) Math.min(Integer.MAX_VALUE, (long) ArcforgeConfig.FIREBOX_ARRAY_HEAT_PER_BLOCK.getAsInt() * volume());
    }

    private HeatBuffer newHeat(int volume) {
        return new HeatBuffer((int) Math.min(Integer.MAX_VALUE, (long) ArcforgeConfig.FIREBOX_ARRAY_HEAT_CAPACITY_PER_BLOCK.getAsInt() * volume),
                ArcforgeConfig.FIREBOX_ARRAY_MAX_TEMPERATURE.getAsInt(), this::setChanged);
    }

    // The heat buffer and tanks follow the box's volume; what they hold carries over. A box that breaks keeps its size
    // until it forms again, so breaking a casing loses no heat.
    private void resize() {
        if (box == null) {
            targetsDirty = true;
            return;
        }
        int volume = volume();
        HeatBuffer resized = newHeat(volume);
        if (resized.getCapacity() != heat.getCapacity()) {
            resized.add(heat.getStored());
            heat = resized;
            heatOutput = heat.output();
        }
        tank.setCapacity(ArcforgeConfig.FIREBOX_ARRAY_TANK_PER_BLOCK.getAsInt() * volume);
        oxy.getTank().setCapacity(ArcforgeConfig.FIREBOX_ARRAY_OXYGEN_TANK_PER_BLOCK.getAsInt() * volume);
        targetsDirty = true;
    }

    // Heat in a solid fuel item (unmultiplied HU): what it gives in a Firebox; Coal Coke cokeHeatMultiplier times a coal's
    // (its block, a coal block's).
    public static double solidHeat(ServerLevel level, net.minecraft.world.level.block.entity.BlockEntity machine, ItemStack stack) {
        if (isCoke(stack)) {
            ItemStack coal = new ItemStack(stack.is(ModItems.COAL_COKE_BLOCK.get()) ? Items.COAL_BLOCK : Items.COAL);
            return ArcforgeConfig.FIREBOX_ARRAY_COKE_HEAT.getAsDouble() * fireboxHeat(CombustionFuel.vanillaBurnTicks(level, machine, coal));
        }
        return fireboxHeat(CombustionFuel.vanillaBurnTicks(level, machine, stack));
    }

    // The heat a Firebox (without upgrades) gets from a fuel that burns this many ticks in a furnace.
    public static double fireboxHeat(int vanillaTicks) {
        return vanillaTicks / ArcforgeConfig.FIREBOX_BURN_SPEED.getAsDouble() * ArcforgeConfig.FIREBOX_HEAT_PER_TICK.getAsInt();
    }

    public static int solidTemperature(ItemStack stack) {
        return isCoke(stack) ? ArcforgeConfig.FIREBOX_ARRAY_COKE_TEMPERATURE.getAsInt() : ArcforgeConfig.FIREBOX_ARRAY_SOLID_TEMPERATURE.getAsInt();
    }

    // --- Structure ---

    @Override
    public void onLoad() {
        super.onLoad();
        FireboxArrayStructure.register(this);
    }

    @Override
    public void setRemoved() {
        FireboxArrayStructure.unregister(this);
        super.setRemoved();
    }

    @Override
    public boolean isFormed() {
        return box != null;
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

    // Breaking the controller un-forms the rest of the box, so a new controller can claim it.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && box != null) {
            FireboxArrayStructure.unform(serverLevel, box, pos);
            box = null;
        }
    }

    private void updateFormed(ServerLevel level) {
        FireboxArrayStructure.Box found = FireboxArrayStructure.find(level, worldPosition, box);
        if (Objects.equals(found, box)) {
            return;
        }
        if (box != null) {
            FireboxArrayStructure.unform(level, box, null);
        }
        box = found;
        resize();
        if (found != null) {
            FireboxArrayStructure.form(level, found, worldPosition);
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        setRunning(false);
        setChanged();
        sync(level);
    }

    public FireboxArrayStructure.@Nullable Box getBox() {
        return box;
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return box != null ? box.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return box != null ? box.max() : worldPosition;
    }

    // The box's walls, not the air inside.
    @Override
    public boolean isPart(BlockPos pos) {
        return box != null && box.contains(pos) && !box.isInterior(pos);
    }

    @Override
    public void onPortsChanged() {
        targetsDirty = true;
    }

    // For advancements (arcforge:multiblock_formed's min_length): its volume, in blocks.
    @Override
    public int length() {
        return box != null ? box.volume() : 0;
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        if (checkRequested || level.getGameTime() % STRUCTURE_CHECK_INTERVAL == 0) {
            checkNow();
        }
        heatPerTick = 0;
        if (box == null) {
            status = MachineStatus.NOT_FORMED;
            heat.setProducing(false);
            oxy.idle();
            setRunning(false);
            syncIfChanged(level);
            return;
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }
        if (!redstoneMode.canRun(powered)) {
            // Disabled, it makes nothing but still gives away the heat it holds.
            status = MachineStatus.DISABLED;
            oxy.idle();
        } else {
            status = burn(level);
        }
        if (heatPerTick > 0) {
            heat.setProducingAt(burnCelsius);
        } else {
            heat.setProducing(false);
            oxy.idle();
        }
        if (heatPerTick <= 0) {
            flue.idle();
        }
        pushHeat(level);
        flue.pushTo(flueTargets);
        setRunning(heatPerTick > 0);
        if (oxy.isActive() && level.getGameTime() % 20 == 0) {
            ArcforgeAdvancements.oxyFuel(this, heat.getTemperature());
        }
        syncIfChanged(level);
    }

    // One tick of burning (see the class comment); returns the status.
    private MachineStatus burn(ServerLevel level) {
        BurnerFuel liquid = BurnerFuel.of(tank.getResource(0));
        boolean solid = solidLeft > 1.0E-6;
        ItemStack next = items.getStack(SLOT_FUEL);
        if (!solid && liquid == null && !isSolidFuel(next)) {
            burnCelsius = 0;
            return MachineStatus.NO_FUEL;
        }
        // The fuel's temperature: the burning item's, else the tank's, else the next item's.
        int celsius;
        if (solid) {
            celsius = solidCelsius;
        } else if (liquid != null) {
            celsius = liquid.burnTemperature(heat.getMaxCelsius());
        } else {
            celsius = solidTemperature(next);
        }
        // Whether there's oxygen for a tick at full output (this tick's rate is set below, from the heat it will make).
        oxygenRate = maxHeatPerTick() / 1000.0 * ArcforgeConfig.FIREBOX_ARRAY_OXYGEN_PER_THOUSAND_HU.getAsDouble();
        boolean oxyReady = oxy.available();
        if (oxyReady) {
            celsius = OxyFuel.temperature(celsius);
        }
        heat.setCeiling(oxyReady ? celsius : heat.getMaxCelsius());
        burnCelsius = Math.min(celsius, Math.max(heat.getMaxCelsius(), heat.getCeiling()));
        int room = heat.storedAt(burnCelsius) - heat.getStored();
        if (room <= 0 || heat.isFull()) {
            return MachineStatus.FULL;
        }
        int want = Math.min(room, maxHeatPerTick());
        // A fresh item is lit before any oxygen is used.
        if (!solid && liquid == null && !startSolid(level, next)) {
            return MachineStatus.NO_FUEL;
        }
        oxygenRate = want / 1000.0 * ArcforgeConfig.FIREBOX_ARRAY_OXYGEN_PER_THOUSAND_HU.getAsDouble();
        double multiplier = oxyReady && oxy.burn() ? OxyFuel.heatMultiplier() : 1.0;
        int made;
        // Whether what burns this tick is a carbon fuel, for the flue (the item before it can burn out).
        boolean carbon = solid || liquid == null ? FlueGas.isCarbonFuel(burning) : FlueGas.isCarbonFuel(tank.getResource(0).getFluid());
        if (solid || liquid == null) {
            made = (int) Math.min(want, Math.floor(solidLeft * multiplier));
            if (made <= 0 && solidLeft > 0) {
                // The last crumb of the item.
                made = Math.min(want, 1);
            }
            solidLeft = Math.max(0.0, solidLeft - made / multiplier);
            if (solidLeft <= 1.0E-6) {
                solidLeft = 0;
                burning = null;
            }
        } else {
            // Whole mB at a time, as the burn needs them.
            double neededMb = want / (liquid.huPerMb() * multiplier);
            if (fuelTaken < neededMb) {
                int take = Math.min(tank.getAmount(), (int) Math.ceil(neededMb - fuelTaken - 1.0E-9));
                if (take > 0) {
                    try (Transaction tx = Transaction.openRoot()) {
                        fuelTaken += tank.extract(0, tank.getResource(0), take, tx);
                        tx.commit();
                    }
                }
            }
            double burnt = Math.min(neededMb, fuelTaken);
            fuelTaken -= burnt;
            made = (int) Math.min(want, Math.round(burnt * liquid.huPerMb() * multiplier));
        }
        heatPerTick = heat.add(made);
        if (heatPerTick > 0 && !flueTargets.isEmpty()) {
            flue.emit(heatPerTick, carbon);
        } else {
            flue.idle();
        }
        setChanged();
        return heatPerTick <= 0 ? MachineStatus.NO_FUEL : oxy.isActive() ? MachineStatus.OXY_FUEL : MachineStatus.BURNING;
    }

    // Takes one solid fuel item from the slot and starts burning it.
    private boolean startSolid(ServerLevel level, ItemStack stack) {
        if (!isSolidFuel(stack)) {
            return false;
        }
        double heatIn = solidHeat(level, this, stack);
        if (heatIn <= 0) {
            return false;
        }
        burning = stack.getItem();
        solidLeft = heatIn;
        solidTotal = (int) Math.min(Integer.MAX_VALUE, Math.round(heatIn));
        solidCelsius = solidTemperature(stack);
        items.setStack(SLOT_FUEL, stack.copyWithCount(stack.getCount() - 1));
        return true;
    }

    // Heat goes out of every Heat port into what touches it, as fast as the array makes it (at least the contact rate).
    private void pushHeat(ServerLevel level) {
        if (targetsDirty) {
            refreshTargets(level);
        }
        if (heat.getStored() <= 0) {
            return;
        }
        int rate = Math.max(ArcforgeConfig.HEAT_CONTACT_RATE.getAsInt(), maxHeatPerTick());
        for (BlockCapabilityCache<HeatHandler, @Nullable Direction> target : heatTargets) {
            HeatHandler handler = target.getCapability();
            if (handler != null) {
                MachineOutputs.moveHeat(heat, handler, rate);
            }
        }
    }

    // What touches the Heat port faces, found again when the ports or the box change.
    private void refreshTargets(ServerLevel level) {
        targetsDirty = false;
        heatTargets.clear();
        flueTargets.clear();
        if (box == null) {
            return;
        }
        for (BlockPos pos : box.positions()) {
            if (!isPart(pos)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (faceMode(pos, side) == SideMode.HEAT) {
                    heatTargets.add(BlockCapabilityCache.create(ModCapabilities.HEAT, level, pos.relative(side).immutable(), side.getOpposite()));
                } else if (faceMode(pos, side) == SideMode.GAS_OUTPUT) {
                    flueTargets.add(BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, pos.relative(side).immutable(), side.getOpposite()));
                }
            }
        }
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : box.positions()) {
            if (isPart(pos) && level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    private void setRunning(boolean running) {
        if (this.running != running) {
            this.running = running;
            setLit(running);
        }
    }

    @Override
    protected void setLit(boolean lit) {
        if (box != null || !lit) {
            super.setLit(lit);
        }
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    public OxyFuel getOxyFuel() {
        return oxy;
    }

    public FlueGas getFlue() {
        return flue;
    }

    public int getHeatPerTick() {
        return heatPerTick;
    }

    // The temperature of the fuel burning (with oxy-fuel), or 0.
    public int getBurnTemperature() {
        return burnCelsius;
    }

    public boolean isRunning() {
        return running;
    }

    // --- The renderer: the fire inside the box and the windows ---

    // The renderer's window tiles for this box (the lining left out behind its windows), worked out by find when the box
    // changes, and again now and then, as the panes' own block updates can reach the client after this one's.
    public Set<Long> windowQuads(java.util.function.Function<FireboxArrayStructure.Box, Set<Long>> find) {
        if (box == null) {
            return Set.of();
        }
        long time = level != null ? level.getGameTime() : 0L;
        if (windowQuads == null || !box.equals(windowQuadsBox) || Math.abs(time - windowQuadsTime) >= WINDOW_REFRESH_INTERVAL) {
            windowQuads = find.apply(box);
            windowQuadsBox = box;
            windowQuadsTime = time;
        }
        return windowQuads;
    }

    public AABB getRenderBox() {
        return box != null ? box.aabb() : new AABB(worldPosition);
    }

    private void syncIfChanged(ServerLevel level) {
        if (running != syncedRunning && level.getGameTime() - lastSync >= SYNC_INTERVAL) {
            sync(level);
        }
    }

    private void sync(Level level) {
        syncedRunning = running;
        lastSync = level.getGameTime();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.INPUT ? itemInput : null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return fuelInput;
        }
        return switch (mode) {
            case INPUT -> fuelInput;
            case OXYGEN -> oxy.getInput();
            case GAS_OUTPUT -> flue.getOutput();
            default -> null;
        };
    }

    public @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return heatOutput;
        }
        return faceMode(pos, side) == SideMode.HEAT ? heatOutput : null;
    }

    // Held buckets of fuel fill the tank through the controller, whatever its ports.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fuelInput;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case ITEM, FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            // Pressurized Conduits bring gas fuels to Input ports and oxygen to Oxygen ports.
            case GAS -> mode == SideMode.INPUT || mode == SideMode.OXYGEN ? ConnectionMode.INPUT
                    : mode == SideMode.GAS_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> ConnectionMode.NONE;
        };
    }

    // Conduits next to the controller ask it about the box face it lies on.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        if (level != null && box != null) {
            MultiblockAutomation.refresh(level, box.min(), box.max());
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        box = in.getLong("box_min").flatMap(min -> in.getLong("box_max").map(max -> new FireboxArrayStructure.Box(BlockPos.of(min), BlockPos.of(max),
                Direction.from2DDataValue(in.getIntOr("box_front", 0))))).orElse(null);
        resize();
        heat.deserialize(in);
        tank.deserialize(in.childOrEmpty("tank"));
        oxy.load(in);
        flue.load(in);
        solidLeft = in.getDoubleOr("solid_left", 0.0);
        solidTotal = in.getIntOr("solid_total", 0);
        solidCelsius = in.getIntOr("solid_celsius", 0);
        burning = in.getString("burning").map(Identifier::tryParse).flatMap(id -> id == null ? java.util.Optional.empty()
                : BuiltInRegistries.ITEM.getOptional(id)).orElse(null);
        fuelTaken = in.getDoubleOr("fuel_taken", 0.0);
        running = in.getBooleanOr("running", false);
        syncedRunning = running;
        portDefaults.load(in);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (box != null) {
            out.putLong("box_min", box.min().asLong());
            out.putLong("box_max", box.max().asLong());
            out.putInt("box_front", box.front().get2DDataValue());
        }
        heat.serialize(out);
        tank.serialize(out.child("tank"));
        oxy.save(out);
        flue.save(out);
        out.putDouble("solid_left", solidLeft);
        out.putInt("solid_total", solidTotal);
        out.putInt("solid_celsius", solidCelsius);
        if (burning != null) {
            out.putString("burning", BuiltInRegistries.ITEM.getKey(burning).toString());
        }
        out.putDouble("fuel_taken", fuelTaken);
        out.putBoolean("running", running);
        portDefaults.save(out);
    }

    // Clients get the saved state when the chunk loads and whenever the box or the fire changes.
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
        return box != null ? Component.translatable("container.arcforge.firebox_array.sized", box.sizeText())
                : Component.translatable("container.arcforge.firebox_array");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FireboxArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
