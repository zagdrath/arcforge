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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ItemLane;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MillMenu;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.BalancedLaneInput;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Mill: the powered Millstone (arcforge:milling recipes), with three lanes that each mill an item in the recipe's
// time x mill.timeMultiplier ticks, drawing energyPerTick FE while they work. Items put in through input faces go to
// the emptiest lane. Speed upgrades make it faster (drawing FE just as much faster), Energy upgrades cut the FE per
// item.
public class MillBlockEntity extends MachineBlockEntity {
    public static final int LANES = 3;
    public static final int MACHINE_SLOTS = LANES * 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final ItemLane[] lanes = new ItemLane[LANES];
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;
    private int usage;

    public MillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.MILL.get(), pos, state, MACHINE_SLOTS, MillBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.MILL_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.MILL_MAX_INPUT.getAsInt(), this::setChanged);
        for (int lane = 0; lane < LANES; lane++) {
            lanes[lane] = new ItemLane(inputSlot(lane), outputSlot(lane), bonusSlot(lane));
        }
        int[] inputs = { inputSlot(0), inputSlot(1), inputSlot(2) };
        this.itemInput = new BalancedLaneInput(items, inputs);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, MillBlockEntity::isProductSlot);
        this.itemAutomation = new CombinedResourceHandler<>(itemInput, itemOutput);
        this.data = new WideIntContainerData(MillMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= MillMenu.DATA_PROGRESS_FIRST && index < MillMenu.DATA_PROGRESS_FIRST + LANES) {
                    return lanes[index - MillMenu.DATA_PROGRESS_FIRST].getProgress();
                }
                if (index >= MillMenu.DATA_TOTAL_FIRST && index < MillMenu.DATA_TOTAL_FIRST + LANES) {
                    return lanes[index - MillMenu.DATA_TOTAL_FIRST].getTotal();
                }
                return switch (index) {
                    case MillMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case MillMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case MillMenu.DATA_USAGE -> usage;
                    case MillMenu.DATA_STATUS -> status.ordinal();
                    case MillMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case MillMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static int inputSlot(int lane) {
        return lane;
    }

    public static int outputSlot(int lane) {
        return LANES + lane;
    }

    public static int bonusSlot(int lane) {
        return 2 * LANES + lane;
    }

    private static boolean isProductSlot(int slot) {
        return slot >= LANES && slot < MACHINE_SLOTS;
    }

    // Each lane's input slot takes anything with a milling recipe. The client passes a null level and checks against
    // the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot < LANES && MachineRecipes.isMillingInput(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t for each working lane: Speed draws it faster, Energy cuts it.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.MILL_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else {
            ItemLane.Settings settings = new ItemLane.Settings(ArcforgeConfig.MILL_TIME_MULTIPLIER.getAsDouble() / speedMultiplier(), energyPerTick());
            boolean working = false, blocked = false, unpowered = false;
            for (ItemLane lane : lanes) {
                ItemLane.State laneState = lane.tick(level, items, energy, settings,
                        stack -> MachineRecipes.milling(level, stack).map(RecipeHolder::value));
                switch (laneState) {
                    case WORKING -> {
                        working = true;
                        usage += settings.energyPerTick();
                    }
                    case OUTPUT_FULL -> blocked = true;
                    case NO_POWER -> unpowered = true;
                    case IDLE -> {}
                }
            }
            status = working ? MachineStatus.MILLING : unpowered ? MachineStatus.NO_POWER : blocked ? MachineStatus.OUTPUT_FULL : MachineStatus.IDLE;
            if (working) {
                setChanged();
            }
        }
        setLit(status == MachineStatus.MILLING);
        autoEject(level, itemOutput);
    }

    public ItemLane getLane(int lane) {
        return lanes[lane];
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
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
            case FLUID, GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        for (int lane = 0; lane < LANES; lane++) {
            lanes[lane].deserialize(input.childOrEmpty("lane" + lane));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        for (int lane = 0; lane < LANES; lane++) {
            lanes[lane].serialize(output.child("lane" + lane));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.mill");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MillMenu(containerId, inventory, worldPosition, items, data);
    }
}
