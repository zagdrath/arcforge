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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.EnergyBalance;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.CarbonReclaimerMenu;
import net.zagdrath.arcforge.recipe.CarbonReclaimingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.fluid.InputTankRouter;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Turns Carbon Dioxide and Hydrogen back into Carbon Dust and Water with FE (arcforge:carbon_reclaiming recipes; by
// default 250 mB of Carbon Dioxide and 500 mB of Hydrogen make a Carbon Dust and 250 mB of Water). Each operation costs
// the recipe's energy (energyPerOperation, 800,000 FE), cut by Energy upgrades but never below EnergyBalance.minEnergyFor:
// balanceSafetyFactor times the most FE the Carbon Dust could give back (burnt, baked into Coal Coke or gasified, in the
// best setup). So it renews carbon, never energy. It draws energyPerTick FE/t (Speed upgrades draw it faster), so the cost
// sets the time. Both gases go in through input faces (sorted into their own tanks); the Carbon Dust and the Water come
// out of output faces.
public class CarbonReclaimerBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_OUTPUT = 0;
    public static final int MACHINE_SLOTS = 1;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);
    private static final TagKey<Fluid> HYDROGEN = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "hydrogen"));
    // How often the balance floor is worked out again (it follows the config and the recipes, which rarely change).
    private static final int FLOOR_INTERVAL = 100;

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank carbonDioxide;
    private final FilteredFluidTank hydrogen;
    private final FilteredFluidTank water;
    private final InputTankRouter router;
    private final ResourceHandler<FluidResource> gasInput;
    private final ResourceHandler<FluidResource> waterOutput;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ResourceHandler<FluidResource> fluidInteraction;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ContainerData data;

    // FE spent on this operation so far, its whole cost, and this tick's draw.
    private int spent;
    private int cost;
    private int usage;
    // The balance floor for the recipe shown, and when it was worked out.
    private int floor;
    private long floorTime = Long.MIN_VALUE;
    private @Nullable Identifier floorRecipe;
    private @Nullable Identifier current;

    public CarbonReclaimerBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: gases in the top, Carbon Dust and Water out of the bottom, FE into the back.
        super(ModBlockEntityTypes.CARBON_RECLAIMER.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.RECLAIMER_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.RECLAIMER_MAX_INPUT.getAsInt(),
                this::setChanged);
        int capacity = ArcforgeConfig.RECLAIMER_TANK_CAPACITY.getAsInt();
        this.carbonDioxide = new FilteredFluidTank(capacity, resource -> resource.getFluid() == ModFluids.CARBON_DIOXIDE.get(), this::setChanged);
        this.hydrogen = new FilteredFluidTank(capacity, resource -> !resource.isEmpty() && resource.getFluid().defaultFluidState().is(HYDROGEN),
                this::setChanged);
        this.water = new FilteredFluidTank(capacity, resource -> resource.getFluid() == Fluids.WATER, this::setChanged);
        this.router = new InputTankRouter(carbonDioxide, hydrogen);
        this.gasInput = new AutomationResourceHandler<>(router, index -> true, index -> false);
        this.waterOutput = new AutomationResourceHandler<>(water, index -> false, index -> true);
        this.fluidAll = new CombinedResourceHandler<>(carbonDioxide, hydrogen, water);
        this.fluidInteraction = new CombinedResourceHandler<>(waterOutput, router);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(CarbonReclaimerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case CarbonReclaimerMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case CarbonReclaimerMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case CarbonReclaimerMenu.DATA_USAGE -> usage;
                    case CarbonReclaimerMenu.DATA_SPENT -> spent;
                    case CarbonReclaimerMenu.DATA_COST -> cost;
                    case CarbonReclaimerMenu.DATA_FLOOR -> floor;
                    case CarbonReclaimerMenu.DATA_CARBON_DIOXIDE -> carbonDioxide.getAmount();
                    case CarbonReclaimerMenu.DATA_HYDROGEN -> hydrogen.getAmount();
                    case CarbonReclaimerMenu.DATA_WATER -> water.getAmount();
                    case CarbonReclaimerMenu.DATA_TANK_CAPACITY -> water.getCapacity();
                    case CarbonReclaimerMenu.DATA_STATUS -> status.ordinal();
                    case CarbonReclaimerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case CarbonReclaimerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> false, UPGRADES, () -> {});
    }

    // FE/t while working: Speed upgrades draw it faster.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.RECLAIMER_ENERGY_PER_TICK.getAsInt() * speedMultiplier());
    }

    // The balance floor for this recipe, worked out again every FLOOR_INTERVAL ticks.
    public int floorFor(ServerLevel level, RecipeHolder<CarbonReclaimingRecipe> holder) {
        Identifier id = holder.id().identifier();
        if (!id.equals(floorRecipe) || level.getGameTime() - floorTime >= FLOOR_INTERVAL) {
            floor = EnergyBalance.minEnergyFor(holder.value(), level, this);
            floorRecipe = id;
            floorTime = level.getGameTime();
        }
        return floor;
    }

    // FE for one operation: Energy upgrades cut it, down to the balance floor.
    public int costFor(ServerLevel level, RecipeHolder<CarbonReclaimingRecipe> holder) {
        int upgraded = EnergyBalance.ceil(holder.value().totalEnergy() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
        return Math.max(upgraded, floorFor(level, holder));
    }

    private CarbonReclaimingRecipe.Input input() {
        return new CarbonReclaimingRecipe.Input(carbonDioxide.getAmount(), hydrogen.getAmount());
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        RecipeHolder<CarbonReclaimingRecipe> holder = MachineRecipes.carbonReclaiming(level, input()).orElse(null);
        Identifier id = holder != null ? holder.id().identifier() : null;
        if (id == null || !id.equals(current)) {
            if (spent != 0) {
                spent = 0;
                setChanged();
            }
            current = id;
        }
        RecipeHolder<CarbonReclaimingRecipe> shown = holder != null ? holder : MachineRecipes.anyCarbonReclaiming(level).orElse(null);
        cost = shown != null ? costFor(level, shown) : 0;

        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else if (holder == null) {
            status = carbonDioxide.getAmount() > 0 || hydrogen.getAmount() > 0 ? MachineStatus.MISSING_FLUID : MachineStatus.IDLE;
        } else if (!fits(holder.value())) {
            status = MachineStatus.OUTPUT_FULL;
        } else {
            // The last tick of an operation takes only what's left, so it costs exactly `cost`.
            int draw = Math.min(energyPerTick(), Math.max(0, cost - spent));
            if (draw > 0 && !energy.consume(draw)) {
                status = MachineStatus.NO_POWER;
            } else {
                status = MachineStatus.RECLAIMING;
                usage = draw;
                spent += draw;
                if (spent >= cost) {
                    spent = 0;
                    finish(holder.value());
                }
                setChanged();
            }
        }
        setLit(status == MachineStatus.RECLAIMING);
        outputs.pushFluid(level, pos, getFacing(), sideConfig, waterOutput, ArcforgeConfig.RECLAIMER_OUTPUT_RATE.getAsInt(), null);
        autoEject(level, itemOutput);
    }

    private boolean fits(CarbonReclaimingRecipe recipe) {
        ItemStack result = recipe.result().create();
        ItemStack slot = items.getStack(SLOT_OUTPUT);
        boolean item = slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize();
        return item && water.getSpace() >= recipe.waterAmount();
    }

    private void finish(CarbonReclaimingRecipe recipe) {
        ItemStack result = recipe.result().create();
        try (Transaction tx = Transaction.openRoot()) {
            carbonDioxide.extract(0, carbonDioxide.getResource(0), recipe.carbonDioxideAmount(), tx);
            if (recipe.hydrogenAmount() > 0) {
                hydrogen.extract(0, hydrogen.getResource(0), recipe.hydrogenAmount(), tx);
            }
            if (recipe.waterAmount() > 0) {
                water.insert(0, FluidResource.of(Fluids.WATER), recipe.waterAmount(), tx);
            }
            tx.commit();
        }
        ItemStack slot = items.getStack(SLOT_OUTPUT);
        items.setStack(SLOT_OUTPUT, slot.isEmpty() ? result : slot.copyWithCount(slot.getCount() + result.getCount()));
        ArcforgeAdvancements.produced(this, result, null, "carbon_reclaiming");
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getCarbonDioxide() {
        return carbonDioxide;
    }

    public FilteredFluidTank getHydrogen() {
        return hydrogen;
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    // Both gas tanks as one: what input faces fill.
    public InputTankRouter getRouter() {
        return router;
    }

    public int getSpent() {
        return spent;
    }

    public int getCost() {
        return cost;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.OUTPUT ? itemOutput : null;
    }

    // Input faces fill the gas tanks (sorted by the router); output faces drain the Water. Unsided, all three (for Jade).
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAll;
        }
        return switch (mode) {
            case INPUT -> gasInput;
            case OUTPUT -> waterOutput;
            default -> null;
        };
    }

    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInteraction;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ITEM, FLUID -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        carbonDioxide.deserialize(input.childOrEmpty("carbon_dioxide"));
        hydrogen.deserialize(input.childOrEmpty("hydrogen"));
        water.deserialize(input.childOrEmpty("water"));
        spent = input.getIntOr("spent", 0);
        current = input.getString("recipe").map(Identifier::tryParse).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        carbonDioxide.serialize(output.child("carbon_dioxide"));
        hydrogen.serialize(output.child("hydrogen"));
        water.serialize(output.child("water"));
        output.putInt("spent", spent);
        if (current != null) {
            output.putString("recipe", current.toString());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.carbon_reclaimer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CarbonReclaimerMenu(containerId, inventory, worldPosition, items, data);
    }
}
