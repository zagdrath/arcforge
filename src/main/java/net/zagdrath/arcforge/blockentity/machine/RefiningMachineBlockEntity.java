/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.machine.refining.RefiningCore;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.RefiningMenu;
import net.zagdrath.arcforge.recipe.RefiningProcess;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The refinery's single-block downstream units (see RefiningCore for how they work): the Catalytic Reformer (Naphtha +
// heat -> Gasoline + Hydrogen), the Coker (Bitumen + heat -> Petroleum Coke, Refinery Gas and Heavy Oil) and the Lubricant Blender
// (Heavy Oil + FE -> Lubricant), the Hydrotreater (Sour Kerosene or Sour Diesel + Hydrogen + heat + FE -> clean fuel +
// Hydrogen Sulfide), the Sulfur Recovery Unit (Hydrogen Sulfide + Oxygen + heat -> Sulfur + Steam) and the Steam
// Methane Reformer (Natural Gas + Steam + heat -> Hydrogen + Carbon Dioxide). The feeds go in through Input faces (both
// feeds, where there are two; the second also through its own Hydrogen, Oxygen or Steam faces); liquid products (and
// the Coker's coke, the Sulfur) come out of Output faces and gases out of Gas Output faces (the Steam Methane
// Reformer's Hydrogen out of Hydrogen faces and only its Carbon Dioxide out of Gas Output faces), pushed out every tick
// (items with auto-eject); heat goes in through Heat faces and FE through Energy faces. Speed upgrades make them
// faster; the powered ones also take Energy upgrades.
public class RefiningMachineBlockEntity extends MachineBlockEntity implements FluidInteractable {
    private final RefiningProcess process;
    private final RefiningCore core;
    private final ResourceHandler<ItemResource> itemOutput;
    // What Input faces take: the feed, and the second feed where there is one.
    private final ResourceHandler<FluidResource> inputs;
    private final ContainerData data;

    public RefiningMachineBlockEntity(RefiningProcess process, BlockPos pos, BlockState state) {
        super(type(process), pos, state, slots(process), (level, slot, resource) -> false, upgrades(process), defaultSides(process),
                sideModes(process));
        this.process = process;
        this.core = new RefiningCore(process, layout(process), items, () -> level, this::setChanged);
        int itemSlot = layout(process).itemSlot();
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == itemSlot);
        ResourceHandler<FluidResource> second = core.secondFeedInput();
        this.inputs = second != null ? new net.neoforged.neoforge.transfer.CombinedResourceHandler<>(core.feedInput(), second) : core.feedInput();
        this.data = menuData(core, access());
    }

    private static BlockEntityType<?> type(RefiningProcess process) {
        return switch (process) {
            case REFORMING -> ModBlockEntityTypes.CATALYTIC_REFORMER.get();
            case COKING -> ModBlockEntityTypes.COKER.get();
            case BLENDING -> ModBlockEntityTypes.LUBRICANT_BLENDER.get();
            case HYDROTREATING -> ModBlockEntityTypes.HYDROTREATER.get();
            case SULFUR_RECOVERY -> ModBlockEntityTypes.SULFUR_RECOVERY_UNIT.get();
            case STEAM_REFORMING -> ModBlockEntityTypes.STEAM_METHANE_REFORMER.get();
            case CRACKING, STEAM_CRACKING -> throw new IllegalArgumentException("The Cracking Unit and the Steam Cracker are multiblocks");
        };
    }

    public static int slots(RefiningProcess process) {
        return process.makesItem() ? 1 : 0;
    }

    public static RefiningCore.Layout layout(RefiningProcess process) {
        return switch (process) {
            case REFORMING -> new RefiningCore.Layout(-1, -1, 0, 0, ArcforgeConfig.REFORMER_HEAT_CAPACITY.getAsInt(),
                    ArcforgeConfig.REFORMER_MAX_TEMPERATURE.getAsInt(), ArcforgeConfig.REFORMER_FEED_CAPACITY.getAsInt(),
                    ArcforgeConfig.REFORMER_PRODUCT_CAPACITY.getAsInt());
            case COKING -> new RefiningCore.Layout(-1, 0, 0, 0, ArcforgeConfig.COKER_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.COKER_MAX_TEMPERATURE.getAsInt(),
                    ArcforgeConfig.COKER_FEED_CAPACITY.getAsInt(), ArcforgeConfig.COKER_PRODUCT_CAPACITY.getAsInt());
            case BLENDING -> new RefiningCore.Layout(-1, -1, ArcforgeConfig.BLENDER_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.BLENDER_MAX_INPUT.getAsInt(),
                    0, 0, ArcforgeConfig.BLENDER_FEED_CAPACITY.getAsInt(), ArcforgeConfig.BLENDER_PRODUCT_CAPACITY.getAsInt());
            case HYDROTREATING -> new RefiningCore.Layout(-1, -1, ArcforgeConfig.HYDROTREATER_ENERGY_CAPACITY.getAsInt(),
                    ArcforgeConfig.HYDROTREATER_MAX_INPUT.getAsInt(), ArcforgeConfig.HYDROTREATER_HEAT_CAPACITY.getAsInt(),
                    ArcforgeConfig.HYDROTREATER_MAX_TEMPERATURE.getAsInt(), ArcforgeConfig.HYDROTREATER_FEED_CAPACITY.getAsInt(),
                    ArcforgeConfig.HYDROTREATER_PRODUCT_CAPACITY.getAsInt(), ArcforgeConfig.HYDROTREATER_SECOND_FEED_CAPACITY.getAsInt());
            case SULFUR_RECOVERY -> new RefiningCore.Layout(-1, 0, 0, 0, ArcforgeConfig.SULFUR_RECOVERY_HEAT_CAPACITY.getAsInt(),
                    ArcforgeConfig.SULFUR_RECOVERY_MAX_TEMPERATURE.getAsInt(), ArcforgeConfig.SULFUR_RECOVERY_FEED_CAPACITY.getAsInt(),
                    ArcforgeConfig.SULFUR_RECOVERY_PRODUCT_CAPACITY.getAsInt(), ArcforgeConfig.SULFUR_RECOVERY_SECOND_FEED_CAPACITY.getAsInt());
            case STEAM_REFORMING -> new RefiningCore.Layout(-1, -1, 0, 0, ArcforgeConfig.SMR_HEAT_CAPACITY.getAsInt(),
                    ArcforgeConfig.SMR_MAX_TEMPERATURE.getAsInt(), ArcforgeConfig.SMR_FEED_CAPACITY.getAsInt(), ArcforgeConfig.SMR_PRODUCT_CAPACITY.getAsInt(),
                    ArcforgeConfig.SMR_SECOND_FEED_CAPACITY.getAsInt());
            case CRACKING, STEAM_CRACKING -> throw new IllegalArgumentException("The Cracking Unit and the Steam Cracker are multiblocks");
        };
    }

    public static Set<UpgradeType> upgrades(RefiningProcess process) {
        return process.powered() ? EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY) : EnumSet.of(UpgradeType.SPEED);
    }

    // Defaults (top, bottom, left, right, back, front): the feeds into the top, heat into the bottom (FE into the back
    // for the Blender and the Hydrotreater, Oxygen into the back of the Sulfur Recovery Unit), liquids and items out of
    // the left (the Steam Methane Reformer's Hydrogen), gases out of the right.
    private static SideConfig defaultSides(RefiningProcess process) {
        return switch (process) {
            case REFORMING, COKING -> new SideConfig(SideMode.INPUT, SideMode.HEAT, SideMode.OUTPUT, SideMode.GAS_OUTPUT, SideMode.NONE, SideMode.NONE);
            case HYDROTREATING -> new SideConfig(SideMode.INPUT, SideMode.HEAT, SideMode.OUTPUT, SideMode.GAS_OUTPUT, SideMode.ENERGY, SideMode.NONE);
            case SULFUR_RECOVERY -> new SideConfig(SideMode.INPUT, SideMode.HEAT, SideMode.OUTPUT, SideMode.GAS_OUTPUT, SideMode.OXYGEN, SideMode.NONE);
            case STEAM_REFORMING -> new SideConfig(SideMode.INPUT, SideMode.HEAT, SideMode.HYDROGEN, SideMode.GAS_OUTPUT, SideMode.NONE, SideMode.NONE);
            default -> new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.OUTPUT, SideMode.NONE, SideMode.ENERGY, SideMode.NONE);
        };
    }

    private static List<SideMode> sideModes(RefiningProcess process) {
        return switch (process) {
            case HYDROTREATING -> List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.GAS_OUTPUT, SideMode.HEAT, SideMode.ENERGY,
                    SideMode.HYDROGEN);
            case SULFUR_RECOVERY -> List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.GAS_OUTPUT, SideMode.HEAT, SideMode.OXYGEN);
            case STEAM_REFORMING -> List.of(SideMode.NONE, SideMode.INPUT, SideMode.HYDROGEN, SideMode.GAS_OUTPUT, SideMode.HEAT, SideMode.STEAM);
            default -> process.powered() ? List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY)
                    : List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.GAS_OUTPUT, SideMode.HEAT);
        };
    }

    // The Steam Methane Reformer gives its Hydrogen (product 0) out of Hydrogen faces and only its Carbon Dioxide out of
    // Gas Output faces.
    private boolean splitsHydrogen() {
        return process == RefiningProcess.STEAM_REFORMING;
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems(RefiningProcess process) {
        return new MachineItemHandler(slots(process), (slot, resource) -> false, upgrades(process), () -> {});
    }

    // The menu's values (see RefiningMenu), for this unit and the Cracking Unit alike.
    public static ContainerData menuData(RefiningCore core, MachineBlockEntityAccess machine) {
        return new WideIntContainerData(RefiningMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= RefiningMenu.DATA_PRODUCT_FLUID && index < RefiningMenu.DATA_PRODUCT_FLUID + RefiningMenu.PRODUCTS) {
                    int tank = index - RefiningMenu.DATA_PRODUCT_FLUID;
                    return tank < core.productCount() ? RefiningCore.fluidId(core.product(tank)) : -1;
                }
                if (index >= RefiningMenu.DATA_PRODUCT && index < RefiningMenu.DATA_PRODUCT + RefiningMenu.PRODUCTS) {
                    int tank = index - RefiningMenu.DATA_PRODUCT;
                    return tank < core.productCount() ? core.product(tank).getAmount() : 0;
                }
                return switch (index) {
                    case RefiningMenu.DATA_ENERGY -> core.energy() != null ? core.energy().getAmountAsInt() : 0;
                    case RefiningMenu.DATA_CAPACITY -> core.energy() != null ? core.energy().getCapacityAsInt() : 0;
                    case RefiningMenu.DATA_USAGE -> core.usage();
                    case RefiningMenu.DATA_PROGRESS -> core.progress();
                    case RefiningMenu.DATA_TOTAL -> core.total();
                    case RefiningMenu.DATA_HEAT -> core.heat() != null ? core.heat().getStored() : 0;
                    case RefiningMenu.DATA_HEAT_CAPACITY -> core.heat() != null ? core.heat().getCapacity() : 0;
                    case RefiningMenu.DATA_TEMPERATURE -> core.heat() != null ? core.heat().getTemperature() : 0;
                    case RefiningMenu.DATA_MIN_TEMPERATURE -> core.minTemperature();
                    case RefiningMenu.DATA_HEAT_USAGE -> core.heatUsage();
                    case RefiningMenu.DATA_FEED_FLUID -> RefiningCore.fluidId(core.feed());
                    case RefiningMenu.DATA_FEED -> core.feed().getAmount();
                    case RefiningMenu.DATA_FEED_CAPACITY -> core.feed().getCapacity();
                    case RefiningMenu.DATA_PRODUCT_CAPACITY -> core.productCount() > 0 ? core.product(0).getCapacity() : 0;
                    case RefiningMenu.DATA_CATALYST_LEFT -> core.catalystLeft();
                    case RefiningMenu.DATA_CATALYST_LIFE -> core.catalystLife();
                    case RefiningMenu.DATA_STATUS -> machine.statusOrdinal();
                    case RefiningMenu.DATA_REDSTONE_MODE -> machine.redstoneOrdinal();
                    case RefiningMenu.DATA_SIDE_CONFIG -> machine.sides();
                    case RefiningMenu.DATA_SECOND_FLUID -> core.secondFeed() != null ? RefiningCore.fluidId(core.secondFeed()) : -1;
                    case RefiningMenu.DATA_SECOND -> core.secondFeed() != null ? core.secondFeed().getAmount() : 0;
                    case RefiningMenu.DATA_SECOND_CAPACITY -> core.secondFeed() != null ? core.secondFeed().getCapacity() : 0;
                    case RefiningMenu.DATA_MODE -> core.mode().ordinal();
                    default -> 0;
                };
            }
        };
    }

    // What menuData needs of the machine besides its core.
    public interface MachineBlockEntityAccess {
        int statusOrdinal();

        int redstoneOrdinal();

        int sides();
    }

    public RefiningProcess getProcess() {
        return process;
    }

    public RefiningCore getCore() {
        return core;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        status = core.tick(level, redstoneMode.canRun(level.hasNeighborSignal(pos)), upgrades(UpgradeType.SPEED), upgrades(UpgradeType.ENERGY));
        if (status == process.runningStatus() && core.progress() == 0) {
            Fluid made = core.mainProduct();
            ArcforgeAdvancements.produced(this, process.makesItem() ? items.getStack(0) : ItemStack.EMPTY, made, process.getSerializedName());
        }
        setLit(status == process.runningStatus());
        pushProducts(level, pos);
        if (process.makesItem()) {
            autoEject(level, itemOutput);
        }
    }

    // Liquids out of Output faces, gases out of Gas Output faces, up to fluidOutputRate mB/t a tank.
    private void pushProducts(ServerLevel level, BlockPos pos) {
        int rate = switch (process) {
            case REFORMING -> ArcforgeConfig.REFORMER_OUTPUT_RATE.getAsInt();
            case COKING -> ArcforgeConfig.COKER_OUTPUT_RATE.getAsInt();
            case HYDROTREATING -> ArcforgeConfig.HYDROTREATER_OUTPUT_RATE.getAsInt();
            case SULFUR_RECOVERY -> ArcforgeConfig.SULFUR_RECOVERY_OUTPUT_RATE.getAsInt();
            case STEAM_REFORMING -> ArcforgeConfig.SMR_OUTPUT_RATE.getAsInt();
            default -> ArcforgeConfig.BLENDER_OUTPUT_RATE.getAsInt();
        };
        for (SideMode mode : new SideMode[] { SideMode.OUTPUT, SideMode.GAS_OUTPUT, SideMode.HYDROGEN }) {
            ResourceHandler<FluidResource> source = output(mode);
            if (source == null) {
                continue;
            }
            int budget = rate;
            for (Direction direction : Direction.values()) {
                if (budget <= 0) {
                    break;
                }
                if (sideConfig.get(getFacing(), direction) == mode) {
                    budget -= outputs.pushFluid(level, pos, direction, source, budget);
                }
            }
        }
    }

    // What faces in an output mode give out, or null when that mode gives nothing out here.
    private @Nullable ResourceHandler<FluidResource> output(SideMode mode) {
        return switch (mode) {
            case OUTPUT -> core.liquidOutputs();
            case GAS_OUTPUT -> splitsHydrogen() ? core.productOutput(1) : core.gasOutputs();
            case HYDROGEN -> splitsHydrogen() ? core.productOutput(0) : null;
            default -> null;
        };
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        if (!process.makesItem()) {
            return null;
        }
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.OUTPUT ? itemOutput : null;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return core.all();
        }
        if (mode == SideMode.INPUT) {
            return inputs;
        }
        if (mode == process.secondFeed()) {
            return core.secondFeedInput();
        }
        return output(mode);
    }

    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return core.interaction();
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        if (core.energy() == null) {
            return null;
        }
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? core.energy() : null;
    }

    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        if (core.heatInput() == null) {
            return null;
        }
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? core.heatInput() : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            // Gases: on the units that take one (those with a second feed), the feeds come in through Input faces and the
            // second feed's own faces.
            case GAS -> mode == SideMode.GAS_OUTPUT || mode == SideMode.HYDROGEN && splitsHydrogen() ? ConnectionMode.OUTPUT
                    : process.secondFeed() != null && (mode == SideMode.INPUT || mode == process.secondFeed()) ? ConnectionMode.INPUT
                    : ConnectionMode.NONE;
            case ITEM -> process.makesItem() && mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY && core.energy() != null ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT && core.heat() != null ? ConnectionMode.INPUT : ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        core.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        core.save(output);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge." + name(process));
    }

    // The unit's registry name.
    public static String name(RefiningProcess process) {
        return switch (process) {
            case REFORMING -> "catalytic_reformer";
            case COKING -> "coker";
            case BLENDING -> "lubricant_blender";
            case HYDROTREATING -> "hydrotreater";
            case SULFUR_RECOVERY -> "sulfur_recovery_unit";
            case STEAM_REFORMING -> "steam_methane_reformer";
            case CRACKING -> "cracking_unit";
            case STEAM_CRACKING -> "steam_cracker";
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RefiningMenu(process, containerId, inventory, worldPosition, items, data);
    }

    // For menuData.
    public MachineBlockEntityAccess access() {
        return new MachineBlockEntityAccess() {
            @Override
            public int statusOrdinal() {
                return status.ordinal();
            }

            @Override
            public int redstoneOrdinal() {
                return redstoneMode.ordinal();
            }

            @Override
            public int sides() {
                return packedSides();
            }
        };
    }
}
