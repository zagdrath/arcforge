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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.VulcanizerMenu;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.VulcanizingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.transfer.item.PairedItemInput;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Cures Raw Rubber with Sulfur into Rubber (arcforge:vulcanizing recipes) with heat and no FE. It only works at
// vulcanizer.minTemperature (140°C) or hotter: below that it pauses, keeping its progress. Two input slots, each holding
// one kind of item (PairedItemInput sorts what hoppers and conduits put in), and an output. Heat flows in from hotter
// blocks (a Firebox, a Fuel Burner, a hot Heat Cell) through heat faces. Speed upgrades make it faster (drawing heat just
// as much faster), Heat upgrades cut the heat per item.
public class VulcanizerBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_INPUT_B = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int MACHINE_SLOTS = 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT);

    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int heatUsage;

    public VulcanizerBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: top input, bottom output, back heat.
        super(ModBlockEntityTypes.VULCANIZER.get(), pos, state, MACHINE_SLOTS, VulcanizerBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(ArcforgeConfig.VULCANIZER_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.VULCANIZER_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        this.itemInput = new PairedItemInput(items, SLOT_INPUT, SLOT_INPUT_B);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new PairedItemInput(items, SLOT_INPUT, SLOT_INPUT_B, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(VulcanizerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case VulcanizerMenu.DATA_HEAT -> heat.getStored();
                    case VulcanizerMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case VulcanizerMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case VulcanizerMenu.DATA_MIN_TEMPERATURE -> minTemperature();
                    case VulcanizerMenu.DATA_PROGRESS -> progress;
                    case VulcanizerMenu.DATA_TOTAL -> total;
                    case VulcanizerMenu.DATA_HEAT_USAGE -> heatUsage;
                    case VulcanizerMenu.DATA_STATUS -> status.ordinal();
                    case VulcanizerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case VulcanizerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return (slot == SLOT_INPUT || slot == SLOT_INPUT_B) && MachineRecipes.isVulcanizerInput(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public static int minTemperature() {
        return ArcforgeConfig.VULCANIZER_MIN_TEMPERATURE.getAsInt();
    }

    // HU/t while curing: Speed draws it faster, Heat cuts it the way Energy cuts FE.
    public int heatPerTick(VulcanizingRecipe recipe) {
        return (int) Math.ceil(recipe.huPerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.HEAT)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        heatUsage = 0;
        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else {
            status = work(level);
        }
        setLit(status == MachineStatus.VULCANIZING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack a = items.getStack(SLOT_INPUT);
        ItemStack b = items.getStack(SLOT_INPUT_B);
        RecipeHolder<VulcanizingRecipe> holder = MachineRecipes.vulcanizing(level, a, b).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            return MachineStatus.IDLE;
        }
        VulcanizingRecipe recipe = holder.value();
        total = UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
        ItemStack result = recipe.result().create();
        if (!fits(items.getStack(SLOT_OUTPUT), result)) {
            return MachineStatus.OUTPUT_FULL;
        }
        int hu = heatPerTick(recipe);
        // Too cool, or too little heat left for this tick: wait for more, keeping the progress.
        if (heat.getTemperature() < minTemperature() || heat.getStored() < hu) {
            return MachineStatus.TOO_COLD;
        }
        heat.remove(hu);
        heatUsage = hu;
        progress++;
        if (progress >= total) {
            int[] slots = recipe.slotsFor(a, b);
            if (slots != null) {
                int first = slots[0] == 0 ? SLOT_INPUT : SLOT_INPUT_B;
                int second = slots[1] == 0 ? SLOT_INPUT : SLOT_INPUT_B;
                ItemStack firstStack = items.getStack(first);
                items.setStack(first, firstStack.copyWithCount(firstStack.getCount() - recipe.input().count()));
                ItemStack secondStack = items.getStack(second);
                items.setStack(second, secondStack.copyWithCount(secondStack.getCount() - recipe.secondInput().count()));
                ItemStack current = items.getStack(SLOT_OUTPUT);
                items.setStack(SLOT_OUTPUT, current.isEmpty() ? result : current.copyWithCount(current.getCount() + result.getCount()));
                ArcforgeAdvancements.produced(this, result, null, "vulcanizing");
            }
            progress = 0;
        }
        setChanged();
        return MachineStatus.VULCANIZING;
    }

    private static boolean fits(ItemStack slot, ItemStack result) {
        if (slot.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize();
    }

    public HeatBuffer getHeat() {
        return heat;
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
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY, FLUID, GAS -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.vulcanizer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VulcanizerMenu(containerId, inventory, worldPosition, items, data);
    }
}
