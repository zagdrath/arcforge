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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
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
import net.zagdrath.arcforge.menu.machine.GasifierMenu;
import net.zagdrath.arcforge.recipe.GasifyingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Gasifies solid carbon (arcforge:gasifying recipes: biomass, Bio-Coal, coal, charcoal, Coal Coke) with Steam into Syngas,
// with heat and no FE. It only works at gasifier.minTemperature (800°C) or hotter: below that it pauses, keeping its
// progress. Each operation takes the recipe's items from the input slot and its steam (steamPerOperation, 500 mB) from the
// steam tank, uses its heat (heatPerOperation, 16,000 HU) spread over its time, and puts the Syngas in its gas tank and,
// now and then (ashChance), a Wood Ash in its ash slot (a full ash slot loses it). Steam (#c:steam) comes in through input
// faces with the fuel, the ash goes out of output faces, the Syngas out of Gas Output faces, and heat flows in through
// heat faces from hotter blocks. Speed upgrades make it faster (drawing heat just as much faster), Heat upgrades cut the
// heat per operation.
public class GasifierBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_ASH = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.GAS_OUTPUT, SideMode.HEAT);
    private static final TagKey<Fluid> STEAM = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "steam"));

    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank steam;
    private final FilteredFluidTank syngas;
    private final ResourceHandler<FluidResource> steamInput;
    private final ResourceHandler<FluidResource> syngasOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int heatUsage;
    // HU not yet paid for this operation's ticks (its heat isn't a whole number per tick).
    private double heatOwed;

    public GasifierBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: top input (fuel and steam), bottom output (ash), right Syngas out, back heat.
        super(ModBlockEntityTypes.GASIFIER.get(), pos, state, MACHINE_SLOTS, GasifierBlockEntity::isItemValid,
                UPGRADES, new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.GAS_OUTPUT, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(ArcforgeConfig.GASIFIER_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.GASIFIER_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        this.steam = new FilteredFluidTank(ArcforgeConfig.GASIFIER_STEAM_CAPACITY.getAsInt(), GasifierBlockEntity::isSteam, this::setChanged);
        this.syngas = new FilteredFluidTank(ArcforgeConfig.GASIFIER_SYNGAS_CAPACITY.getAsInt(), resource -> !resource.isEmpty(), this::setChanged);
        this.steamInput = new AutomationResourceHandler<>(steam, index -> true, index -> false);
        this.syngasOutput = new AutomationResourceHandler<>(syngas, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(steamInput, syngasOutput);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_ASH);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_ASH);
        this.data = new WideIntContainerData(GasifierMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case GasifierMenu.DATA_HEAT -> heat.getStored();
                    case GasifierMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case GasifierMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case GasifierMenu.DATA_MIN_TEMPERATURE -> minTemperature();
                    case GasifierMenu.DATA_PROGRESS -> progress;
                    case GasifierMenu.DATA_TOTAL -> total;
                    case GasifierMenu.DATA_HEAT_USAGE -> heatUsage;
                    case GasifierMenu.DATA_STATUS -> status.ordinal();
                    case GasifierMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case GasifierMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case GasifierMenu.DATA_STEAM -> steam.getAmount();
                    case GasifierMenu.DATA_STEAM_CAPACITY -> steam.getCapacity();
                    case GasifierMenu.DATA_SYNGAS -> syngas.getAmount();
                    case GasifierMenu.DATA_SYNGAS_CAPACITY -> syngas.getCapacity();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isGasifierInput(level, resource.toStack(1));
    }

    public static boolean isSteam(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid().defaultFluidState().is(STEAM);
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public static int minTemperature() {
        return ArcforgeConfig.GASIFIER_MIN_TEMPERATURE.getAsInt();
    }

    // HU per operation after Heat upgrades (they cut it the way Energy upgrades cut FE).
    public int heatPerOperation(GasifyingRecipe recipe) {
        return (int) Math.ceil(recipe.heatPerOperation() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.HEAT)));
    }

    public int ticksFor(GasifyingRecipe recipe) {
        return UpgradeType.time(recipe.ticks(), upgrades(UpgradeType.SPEED));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        heatUsage = 0;
        if (!canRun(level)) {
            status = stoppedStatus();
        } else {
            status = work(level);
        }
        setLit(status == MachineStatus.GASIFYING);
        // The Syngas's only way out, so this is always on.
        int budget = ArcforgeConfig.GASIFIER_OUTPUT_RATE.getAsInt();
        for (Direction direction : Direction.values()) {
            if (budget <= 0 || syngas.getAmount() <= 0) {
                break;
            }
            if (sideConfig.get(getFacing(), direction) == SideMode.GAS_OUTPUT) {
                budget -= outputs.pushFluid(level, pos, direction, syngasOutput, budget);
            }
        }
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        RecipeHolder<GasifyingRecipe> holder = input.isEmpty() ? null : MachineRecipes.gasifying(level, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            heatOwed = 0;
            return MachineStatus.IDLE;
        }
        GasifyingRecipe recipe = holder.value();
        total = ticksFor(recipe);
        if (input.getCount() < recipe.count()) {
            return MachineStatus.MISSING_ITEMS;
        }
        if (steam.getAmount() < recipe.steamAmount()) {
            return MachineStatus.NO_STEAM;
        }
        FluidStack made = recipe.result().create();
        if (!fitsGas(made)) {
            return MachineStatus.OUTPUT_FULL;
        }
        // This tick's share of the operation's heat (whole HU, the rest carried to the next tick).
        double perTick = heatPerOperation(recipe) / (double) Math.max(1, total);
        int hu = (int) Math.ceil(perTick + heatOwed - 1.0E-9);
        // Too cool, or too little heat for this tick: wait for more, keeping the progress.
        if (heat.getTemperature() < minTemperature() || heat.getStored() < hu) {
            return MachineStatus.TOO_COLD;
        }
        heat.remove(hu);
        heatOwed += perTick - hu;
        heatUsage = hu;
        progress++;
        if (progress >= total) {
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - recipe.count()));
            int steamUsed = 0;
            try (Transaction tx = Transaction.openRoot()) {
                if (recipe.steamAmount() > 0) {
                    steamUsed = steam.extract(0, steam.getResource(0), recipe.steamAmount(), tx);
                }
                syngas.insert(0, FluidResource.of(made), made.getAmount(), tx);
                tx.commit();
            }
            ItemStack ash = ItemStack.EMPTY;
            if (level.getRandom().nextDouble() < recipe.ashChanceOrDefault()) {
                ash = addAsh(recipe.ash().map(template -> template.create()).orElseGet(() -> new ItemStack(ModItems.WOOD_ASH.get())));
            }
            controlState.completed(ash.isEmpty() ? List.of() : List.of(ash), List.of(made.copy()), recipe.count(), steamUsed);
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, made.getFluid(), "gasifying");
            progress = 0;
            heatOwed = 0;
        }
        setChanged();
        return MachineStatus.GASIFYING;
    }

    private boolean fitsGas(FluidStack made) {
        try (Transaction tx = Transaction.openRoot()) {
            return syngas.insert(0, FluidResource.of(made), made.getAmount(), tx) == made.getAmount();
        }
    }

    // Into the ash slot, up to a stack; what doesn't fit is lost. Returns what went in.
    private ItemStack addAsh(ItemStack ash) {
        ItemStack slot = items.getStack(SLOT_ASH);
        if (slot.isEmpty()) {
            items.setStack(SLOT_ASH, ash.copy());
            return ash;
        } else if (ItemStack.isSameItemSameComponents(slot, ash)) {
            int count = Math.min(slot.getMaxStackSize(), slot.getCount() + ash.getCount());
            items.setStack(SLOT_ASH, slot.copyWithCount(count));
            return ash.copyWithCount(count - slot.getCount());
        }
        return ItemStack.EMPTY;
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getSteam() {
        return steam;
    }

    public FilteredFluidTank getSyngas() {
        return syngas;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public int getHeatUsage() {
        return heatUsage;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

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

    // Input faces fill the steam tank; Gas Output faces drain the Syngas.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAutomation;
        }
        return switch (mode) {
            case INPUT -> steamInput;
            case GAS_OUTPUT -> syngasOutput;
            default -> null;
        };
    }

    // Gas Cartridges fill from the Syngas, and empty Steam into its tank.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidAutomation;
    }

    // Heat goes in on heat faces; nothing can draw it back out.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatInput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            // Steam rides Pressurized Conduits in; Syngas leaves through them.
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.GAS_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY, FLUID -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        steam.deserialize(input.childOrEmpty("steam"));
        syngas.deserialize(input.childOrEmpty("syngas"));
        progress = input.getIntOr("progress", 0);
        heatOwed = input.getDoubleOr("heat_owed", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        steam.serialize(output.child("steam"));
        syngas.serialize(output.child("syngas"));
        output.putInt("progress", progress);
        output.putDouble("heat_owed", heatOwed);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.gasifier");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GasifierMenu(containerId, inventory, worldPosition, items, data);
    }
}
