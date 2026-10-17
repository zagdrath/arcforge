/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.SteamGeneratorMenu;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.multiblock.VesselStructures;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Steam Generator (see VesselStructures.STEAM_GENERATOR): a vertical heat exchanger between a pressurized Fission
// Reactor's coolant loop and the turbines. Hot Coolant (or Hot Heavy Water) from the reactor's hot leg comes in through
// Input ports with feedwater (Water); each Steam Generator Tubes block cools hotCoolantPerTube mB of it a tick, its
// heat (fissionReactor.hotCoolantHeat HU a mB) boiling feedwater into High-Pressure Steam at steamHeat HU a mB. The
// cooled coolant (Water or Heavy Water again) goes back out of Output ports to the reactor's cold leg, the steam out of
// Steam ports to Steam Turbine Arrays. Both are pushed into what touches the ports. The advanced cores' loops work it
// the same way: a lead-bismuth fast reactor's Hot Lead-Bismuth (about 900 °C) boils it into Superheated Steam
// (SUPERHEATED_FACTOR times the heat a mB), and a molten salt reactor's Hot Fuel Salt into High-Pressure Steam, each
// going back out as Lead-Bismuth or Fuel Salt.
public class SteamGeneratorBlockEntity extends VesselControllerBlockEntity {
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.STEAM);
    private static final Set<SideMode> PUSHED = EnumSet.of(SideMode.OUTPUT, SideMode.STEAM);
    private static final int LIT_HOLD = 20;

    private int tubes;
    private final FilteredFluidTank hot;
    private final FilteredFluidTank feed;
    private final FilteredFluidTank cooled;
    private final FilteredFluidTank steam;
    private final ResourceHandler<FluidResource> input;
    private final ResourceHandler<FluidResource> cooledOutput;
    private final ResourceHandler<FluidResource> steamOutput;
    private final ResourceHandler<FluidResource> all;
    private final ContainerData data;
    private double heatLeft;
    private int lastHot;
    private int lastSteam;
    private long lastWorked = Long.MIN_VALUE / 2;
    public static Consumer<SteamGeneratorBlockEntity> clientHook = generator -> {};

    public SteamGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.STEAM_GENERATOR.get(), pos, state, 0, (slot, resource) -> false, SIDE_MODES);
        int tank = ArcforgeConfig.STEAM_GENERATOR_TANK_PER_BLOCK.getAsInt();
        this.hot = new FilteredFluidTank(tank, SteamGeneratorBlockEntity::isHot, this::setChanged);
        this.feed = new FilteredFluidTank(tank, resource -> resource.is(Fluids.WATER), this::setChanged);
        this.cooled = new FilteredFluidTank(tank, resource -> resource.is(Fluids.WATER) || resource.is(ModFluids.HEAVY_WATER.get())
                || resource.is(ModFluids.LEAD_BISMUTH.get()) || resource.is(ModFluids.FUEL_SALT.get()), this::setChanged);
        this.steam = new FilteredFluidTank(tank * 4, resource -> resource.is(ModFluids.HIGH_PRESSURE_STEAM.get()) || resource.is(ModFluids.SUPERHEATED_STEAM.get()),
                this::setChanged);
        this.input = new CombinedResourceHandler<>(new SidedResourceHandler<>(hot, true, false, Integer.MAX_VALUE),
                new SidedResourceHandler<>(feed, true, false, Integer.MAX_VALUE));
        this.cooledOutput = new SidedResourceHandler<>(cooled, false, true, Integer.MAX_VALUE);
        this.steamOutput = new SidedResourceHandler<>(steam, false, true, Integer.MAX_VALUE);
        this.all = new CombinedResourceHandler<>(input, cooledOutput, steamOutput);
        this.data = new WideIntContainerData(SteamGeneratorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case SteamGeneratorMenu.DATA_STATUS -> status.ordinal();
                    case SteamGeneratorMenu.DATA_FORMED -> box != null ? 1 : 0;
                    case SteamGeneratorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case SteamGeneratorMenu.DATA_SIDE_CONFIG -> packedSides();
                    case SteamGeneratorMenu.DATA_TUBES -> box != null ? tubes : 0;
                    case SteamGeneratorMenu.DATA_HOT -> hot.getAmount();
                    case SteamGeneratorMenu.DATA_FEED -> feed.getAmount();
                    case SteamGeneratorMenu.DATA_COOLED -> cooled.getAmount();
                    case SteamGeneratorMenu.DATA_STEAM -> steam.getAmount();
                    case SteamGeneratorMenu.DATA_CAPACITY -> hot.getCapacity();
                    case SteamGeneratorMenu.DATA_STEAM_CAPACITY -> steam.getCapacity();
                    case SteamGeneratorMenu.DATA_LAST_HOT -> lastHot;
                    case SteamGeneratorMenu.DATA_LAST_STEAM -> lastSteam;
                    case SteamGeneratorMenu.DATA_RATE -> maxHotPerTick();
                    case SteamGeneratorMenu.DATA_HOT_FLUID -> fluidId(hot);
                    case SteamGeneratorMenu.DATA_COOLED_FLUID -> fluidId(cooled);
                    default -> 0;
                };
            }
        };
    }

    // A Superheated Steam mB takes this many times a High-Pressure Steam mB's heat.
    public static final double SUPERHEATED_FACTOR = 1.6;

    // What comes in hot: a pressurized core's coolant, or an advanced core's loop.
    public static boolean isHot(FluidResource resource) {
        return resource.is(ModFluids.HOT_COOLANT.get()) || resource.is(ModFluids.HOT_HEAVY_WATER.get()) || resource.is(ModFluids.HOT_LEAD_BISMUTH.get())
                || resource.is(ModFluids.HOT_FUEL_SALT.get());
    }

    // What a hot fluid goes back out as, cooled.
    public static Fluid cooledFrom(Fluid hot) {
        if (hot.isSame(ModFluids.HOT_HEAVY_WATER.get())) {
            return ModFluids.HEAVY_WATER.get();
        }
        if (hot.isSame(ModFluids.HOT_LEAD_BISMUTH.get())) {
            return ModFluids.LEAD_BISMUTH.get();
        }
        if (hot.isSame(ModFluids.HOT_FUEL_SALT.get())) {
            return ModFluids.FUEL_SALT.get();
        }
        return Fluids.WATER;
    }

    // The steam a hot fluid boils feedwater into: Superheated from lead-bismuth, else High-Pressure.
    public static Fluid steamFrom(Fluid hot) {
        return hot.isSame(ModFluids.HOT_LEAD_BISMUTH.get()) ? ModFluids.SUPERHEATED_STEAM.get() : ModFluids.HIGH_PRESSURE_STEAM.get();
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(0, (slot, resource) -> false, Set.of(), () -> {});
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    @Override
    public VesselStructure structure() {
        return VesselStructures.STEAM_GENERATOR;
    }

    @Override
    protected void onShapeChanged(ServerLevel level) {
        tubes = box != null ? VesselStructures.count(level, box, ModBlocks.STEAM_GENERATOR_TUBES.get()) : 0;
        sizeTanks();
    }

    // Each tank holds tankPerBlock for every block inside (the steam tank four times that).
    private void sizeTanks() {
        int tank = ArcforgeConfig.STEAM_GENERATOR_TANK_PER_BLOCK.getAsInt() * Math.max(1, box != null ? box.interiorVolume() : 1);
        hot.setCapacity(tank);
        feed.setCapacity(tank);
        cooled.setCapacity(tank);
        steam.setCapacity(tank * 4);
    }

    @Override
    public void requestCheck() {
        super.requestCheck();
        if (level instanceof ServerLevel serverLevel && box != null) {
            onShapeChanged(serverLevel);
        }
    }

    public int maxHotPerTick() {
        return tubes * ArcforgeConfig.STEAM_GENERATOR_TUBE_RATE.getAsInt();
    }

    public int getTubes() {
        return box != null ? tubes : 0;
    }

    public FilteredFluidTank getHot() {
        return hot;
    }

    public FilteredFluidTank getFeed() {
        return feed;
    }

    public FilteredFluidTank getCooled() {
        return cooled;
    }

    public FilteredFluidTank getSteam() {
        return steam;
    }

    public int getLastSteam() {
        return lastSteam;
    }

    @Override
    protected void work(ServerLevel level) {
        lastHot = 0;
        lastSteam = 0;
        if (!controlState.isEnabled() || !redstoneMode.canRun(redstone > 0)) {
            status = stoppedStatus();
        } else {
            status = exchange();
        }
        if (lastSteam > 0) {
            lastWorked = level.getGameTime();
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.HIGH_PRESSURE_STEAM.get(), "steam_generator");
        }
        setLit(level.getGameTime() - lastWorked < LIT_HOLD);
        MultiblockAutomation.pushFluids(level, this, PUSHED, 64_000);
    }

    // One tick of exchange: hot coolant in, its heat into steam, the coolant back out cooled.
    private MachineStatus exchange() {
        if (hot.getAmount() <= 0) {
            return MachineStatus.NO_HEAT;
        }
        if (feed.getAmount() <= 0) {
            return MachineStatus.NO_WATER;
        }
        Fluid hotFluid = hot.getResource(0).getFluid();
        Fluid back = cooledFrom(hotFluid);
        Fluid made = steamFrom(hotFluid);
        if (cooled.getAmount() > 0 && !cooled.getResource(0).is(back) || cooled.getSpace() <= 0) {
            return MachineStatus.OUTPUT_FULL;
        }
        if (steam.getAmount() > 0 && !steam.getResource(0).is(made)) {
            return MachineStatus.STEAM_FULL;
        }
        double perHot = ArcforgeConfig.REACTOR_HOT_COOLANT_HEAT.getAsDouble();
        double perSteam = ArcforgeConfig.STEAM_GENERATOR_STEAM_HEAT.getAsDouble() * (made == ModFluids.SUPERHEATED_STEAM.get() ? SUPERHEATED_FACTOR : 1.0);
        int amount = Math.min(Math.min(hot.getAmount(), maxHotPerTick()), cooled.getSpace());
        // Only as much as the feedwater and the steam tank can take.
        double heat = heatLeft + amount * perHot;
        int steamMade = (int) Math.min(Math.min(heat / perSteam, feed.getAmount()), steam.getSpace());
        if (steamMade <= 0) {
            return steam.getSpace() <= 0 ? MachineStatus.STEAM_FULL : MachineStatus.NO_WATER;
        }
        // Cool only what the steam made used, so no heat is lost.
        double used = steamMade * perSteam - heatLeft;
        int cooledMb = (int) Math.min(amount, Math.ceil(Math.max(0.0, used) / perHot));
        heatLeft = Math.max(0.0, heatLeft + cooledMb * perHot - steamMade * perSteam);
        take(hot, cooledMb);
        take(feed, steamMade);
        cooled.set(0, FluidResource.of(back), cooled.getAmount() + cooledMb);
        steam.set(0, FluidResource.of(made), steam.getAmount() + steamMade);
        lastHot = cooledMb;
        lastSteam = steamMade;
        return MachineStatus.BOILING;
    }

    private static void take(FilteredFluidTank tank, int amount) {
        int left = tank.getAmount() - amount;
        tank.set(0, left > 0 ? tank.getResource(0) : FluidResource.EMPTY, Math.max(0, left));
    }

    @Override
    public void clientTick() {
        clientHook.accept(this);
    }

    // --- Capabilities ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (box == null) {
            return null;
        }
        if (mode == null) {
            return all;
        }
        return switch (mode) {
            case INPUT -> input;
            case OUTPUT -> cooledOutput;
            case STEAM -> steamOutput;
            default -> null;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.STEAM ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tubes = in.getIntOr("tubes", 0);
        heatLeft = in.getDoubleOr("heat_left", 0.0);
        sizeTanks();
        hot.deserialize(in.childOrEmpty("hot"));
        feed.deserialize(in.childOrEmpty("feed"));
        cooled.deserialize(in.childOrEmpty("cooled"));
        steam.deserialize(in.childOrEmpty("steam"));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("tubes", tubes);
        out.putDouble("heat_left", heatLeft);
        hot.serialize(out.child("hot"));
        feed.serialize(out.child("feed"));
        cooled.serialize(out.child("cooled"));
        steam.serialize(out.child("steam"));
    }

    @Override
    public Component getDisplayName() {
        return box != null ? Component.translatable("container.arcforge.steam_generator.sized", box.sizeText())
                : Component.translatable("container.arcforge.steam_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SteamGeneratorMenu(containerId, inventory, worldPosition, items, data);
    }
}
