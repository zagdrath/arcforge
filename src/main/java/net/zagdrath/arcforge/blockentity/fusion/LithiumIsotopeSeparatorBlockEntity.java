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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.fusion.Lithium6;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.fusion.LithiumIsotopeSeparatorMenu;
import net.zagdrath.arcforge.recipe.LithiumEnrichingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Lithium Isotope Separator: raises lithium fluoride's lithium-6 a step a pass with FE (arcforge:lithium_enriching;
// see Lithium6). Each pass takes fusion.lithium.separatorFeed of the same enrichment and gives one item a step richer
// (the rest leaves as depleted tails); Enriched Lithium Fluoride until it reaches the minimum Lead-Lithium and Lithium
// Target Rods need, then Lithium-6 Fluoride. Feed it its own output to go on. Speed upgrades make it faster, Energy
// upgrades cut the FE.
public class LithiumIsotopeSeparatorBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;
    // The assay the item in the input slot would come out at (tenths of a percent), for the GUI.
    private int nextAssay;

    public LithiumIsotopeSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.LITHIUM_ISOTOPE_SEPARATOR.get(), pos, state, MACHINE_SLOTS, LithiumIsotopeSeparatorBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY, SideMode.NONE), SIDE_MODES);
        int fe = Math.max(1, ArcforgeConfig.LITHIUM_SEPARATOR_ENERGY.getAsInt());
        this.energy = new ConsumerEnergyHandler(fe * 400, fe * 4, this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(LithiumIsotopeSeparatorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case LithiumIsotopeSeparatorMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case LithiumIsotopeSeparatorMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case LithiumIsotopeSeparatorMenu.DATA_PROGRESS -> progress;
                    case LithiumIsotopeSeparatorMenu.DATA_TOTAL -> total;
                    case LithiumIsotopeSeparatorMenu.DATA_USAGE -> usage;
                    case LithiumIsotopeSeparatorMenu.DATA_STATUS -> status.ordinal();
                    case LithiumIsotopeSeparatorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case LithiumIsotopeSeparatorMenu.DATA_SIDE_CONFIG -> packedSides();
                    case LithiumIsotopeSeparatorMenu.DATA_NEXT_ASSAY -> nextAssay;
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isLithiumFeed(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public int energyPerTick(LithiumEnrichingRecipe recipe) {
        return (int) Math.ceil(recipe.baseEnergyPerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        status = canRun(level) ? work(level) : stoppedStatus();
        setLit(status == MachineStatus.ENRICHING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        RecipeHolder<LithiumEnrichingRecipe> holder = input.isEmpty() ? null : MachineRecipes.lithiumEnriching(level, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            nextAssay = 0;
            return input.isEmpty() ? MachineStatus.IDLE : MachineStatus.OUTPUT_FULL;
        }
        LithiumEnrichingRecipe recipe = holder.value();
        int count = Math.max(recipe.input().count(), ArcforgeConfig.LITHIUM_SEPARATOR_FEED.getAsInt());
        ItemStack result = recipe.product(Lithium6.assay(input));
        nextAssay = Lithium6.assay(result);
        total = UpgradeType.time(Math.max(recipe.time(), ArcforgeConfig.LITHIUM_SEPARATOR_TIME.getAsInt()), upgrades(UpgradeType.SPEED));
        if (input.getCount() < count) {
            return MachineStatus.MISSING_ITEMS;
        }
        ItemStack current = items.getStack(SLOT_OUTPUT);
        if (!current.isEmpty() && (!ItemStack.isSameItemSameComponents(current, result) || current.getCount() + result.getCount() > current.getMaxStackSize())) {
            return MachineStatus.OUTPUT_FULL;
        }
        int fe = energyPerTick(recipe);
        if (!energy.consume(fe)) {
            return MachineStatus.NO_POWER;
        }
        usage = fe;
        progress++;
        if (progress >= total) {
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - count));
            items.setStack(SLOT_OUTPUT, current.isEmpty() ? result.copy() : current.copyWithCount(current.getCount() + result.getCount()));
            controlState.completed(result.copy(), count);
            ArcforgeAdvancements.produced(this, result, null, "lithium_enriching");
            progress = 0;
        }
        setChanged();
        return MachineStatus.ENRICHING;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public int getUsage() {
        return usage;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

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

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL, GAS, FLUID -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.lithium_isotope_separator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LithiumIsotopeSeparatorMenu(containerId, inventory, worldPosition, items, data);
    }
}
