/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.fusion;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.fusion.TritiumExtractionUnitMenu;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.TritiumExtractingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Tritium Extraction Unit: bakes the tritium out of irradiated Lithium Target Rods with heat
// (arcforge:tritium_extracting; fusion.tritium.extractionTemperature, extractionHeat HU/t). A rod gives its share of the
// recipe's tritium and helium (by how much of its lithium-6 it bred), into tanks that go out of Gas Output faces (Tritium)
// and By-product faces (Helium); its emptied Zircaloy cladding comes out of Output faces. Double-walled: its tritium
// doesn't leak. Heat flows in from hotter blocks on Heat faces. Speed upgrades make it faster, Heat upgrades cut the heat.
public class TritiumExtractionUnitBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final int TANK = 16_000;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT, SideMode.GAS_OUTPUT,
            SideMode.BYPRODUCT);

    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank tritium;
    private final FilteredFluidTank helium;
    private final ResourceHandler<FluidResource> tritiumOutput;
    private final ResourceHandler<FluidResource> heliumOutput;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int heatUsage;
    private int minTemp = 600;

    public TritiumExtractionUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.TRITIUM_EXTRACTION_UNIT.get(), pos, state, MACHINE_SLOTS, TritiumExtractionUnitBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.HEAT, SideMode.GAS_OUTPUT, SideMode.BYPRODUCT, SideMode.OUTPUT, SideMode.NONE), SIDE_MODES);
        this.heat = new HeatBuffer(40_000, 1_200, this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        this.tritium = new FilteredFluidTank(TANK, resource -> resource.is(ModFluids.TRITIUM.get()), this::setChanged);
        this.helium = new FilteredFluidTank(TANK, resource -> resource.is(ModFluids.HELIUM.get()), this::setChanged);
        this.tritiumOutput = new SidedResourceHandler<>(tritium, false, true, Integer.MAX_VALUE);
        this.heliumOutput = new SidedResourceHandler<>(helium, false, true, Integer.MAX_VALUE);
        this.fluidAll = new CombinedResourceHandler<>(tritiumOutput, heliumOutput);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(TritiumExtractionUnitMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case TritiumExtractionUnitMenu.DATA_HEAT -> heat.getStored();
                    case TritiumExtractionUnitMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case TritiumExtractionUnitMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case TritiumExtractionUnitMenu.DATA_MIN_TEMP -> minTemp;
                    case TritiumExtractionUnitMenu.DATA_HEAT_USAGE -> heatUsage;
                    case TritiumExtractionUnitMenu.DATA_PROGRESS -> progress;
                    case TritiumExtractionUnitMenu.DATA_TOTAL -> total;
                    case TritiumExtractionUnitMenu.DATA_TRITIUM -> tritium.getAmount();
                    case TritiumExtractionUnitMenu.DATA_HELIUM -> helium.getAmount();
                    case TritiumExtractionUnitMenu.DATA_TANK -> TANK;
                    case TritiumExtractionUnitMenu.DATA_STATUS -> status.ordinal();
                    case TritiumExtractionUnitMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case TritiumExtractionUnitMenu.DATA_SIDE_CONFIG -> packedSides();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.tritiumExtracting(level, resource.toStack(1)).isPresent();
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    private int heatPerTick(TritiumExtractingRecipe recipe) {
        int base = Math.max(recipe.heatPerTick(), ArcforgeConfig.EXTRACTION_HEAT.getAsInt());
        return (int) Math.ceil(base * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.HEAT)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        heatUsage = 0;
        status = canRun(level) ? work(level) : stoppedStatus();
        setLit(status == MachineStatus.BAKING);
        autoEject(level, itemOutput);
        push(level, pos, SideMode.GAS_OUTPUT, tritiumOutput);
        push(level, pos, SideMode.BYPRODUCT, heliumOutput);
    }

    private void push(ServerLevel level, BlockPos pos, SideMode mode, ResourceHandler<FluidResource> source) {
        int budget = 2_000;
        for (Direction direction : Direction.values()) {
            if (budget > 0 && sideConfig.get(getFacing(), direction) == mode) {
                budget -= outputs.pushFluid(level, pos, direction, source, budget);
            }
        }
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        RecipeHolder<TritiumExtractingRecipe> holder = input.isEmpty() ? null : MachineRecipes.tritiumExtracting(level, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            return input.isEmpty() ? MachineStatus.NO_ROD : MachineStatus.IDLE;
        }
        TritiumExtractingRecipe recipe = holder.value();
        total = UpgradeType.time(Math.max(recipe.time(), ArcforgeConfig.EXTRACTION_TIME.getAsInt()), upgrades(UpgradeType.SPEED));
        minTemp = Math.max(recipe.minTemp(), ArcforgeConfig.EXTRACTION_TEMPERATURE.getAsInt());
        double bred = FuelRod.of(input).burnup();
        int madeTritium = (int) Math.floor(recipe.tritium() * bred);
        int madeHelium = (int) Math.floor(recipe.helium() * bred);
        ItemStack result = recipe.result().create();
        ItemStack current = items.getStack(SLOT_OUTPUT);
        if (!current.isEmpty() && (!ItemStack.isSameItemSameComponents(current, result) || current.getCount() + result.getCount() > current.getMaxStackSize())) {
            return MachineStatus.OUTPUT_FULL;
        }
        if (tritium.getSpace() < madeTritium || helium.getSpace() < madeHelium) {
            return MachineStatus.TANK_FULL;
        }
        int hu = heatPerTick(recipe);
        if (heat.getTemperature() < minTemp || heat.getStored() < hu) {
            return MachineStatus.TOO_COLD;
        }
        heat.remove(hu);
        heatUsage = hu;
        progress++;
        if (progress >= total) {
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
            items.setStack(SLOT_OUTPUT, current.isEmpty() ? result.copy() : current.copyWithCount(current.getCount() + result.getCount()));
            if (madeTritium > 0) {
                tritium.set(0, FluidResource.of(ModFluids.TRITIUM.get()), tritium.getAmount() + madeTritium);
                ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.TRITIUM.get(), "tritium_extraction");
            }
            if (madeHelium > 0) {
                helium.set(0, FluidResource.of(ModFluids.HELIUM.get()), helium.getAmount() + madeHelium);
            }
            controlState.completed(result.copy(), 1);
            progress = 0;
        }
        setChanged();
        return MachineStatus.BAKING;
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public int getHeatUsage() {
        return heatUsage;
    }

    public FilteredFluidTank getTritium() {
        return tritium;
    }

    public FilteredFluidTank getHelium() {
        return helium;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    // --- Capabilities ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAll;
        }
        return switch (mode) {
            case GAS_OUTPUT -> tritiumOutput;
            case BYPRODUCT -> heliumOutput;
            default -> null;
        };
    }

    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatInput : null;
    }

    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidAll;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.GAS_OUTPUT || mode == SideMode.BYPRODUCT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY, FLUID -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        tritium.deserialize(input.childOrEmpty("tritium"));
        helium.deserialize(input.childOrEmpty("helium"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        tritium.serialize(output.child("tritium"));
        helium.serialize(output.child("helium"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.tritium_extraction_unit");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TritiumExtractionUnitMenu(containerId, inventory, worldPosition, items, data);
    }

    static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }
}
