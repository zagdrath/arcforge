/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.multiblock.MetalPressingArrayCasingBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.PressingLane;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.MetalPressingArrayMenu;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Metal Pressing Array (see CubeMultiblockBlockEntity). Three pressing lanes work in parallel, each
// with its own die, twice as fast as a Metal Press and for 60% less FE an operation. Input faces only
// feed a lane whose die presses the item; the die slots are for players only.
public class MetalPressingArrayBlockEntity extends CubeMultiblockBlockEntity {
    public static final int LANES = 3;
    public static final int MACHINE_SLOTS = LANES * 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);

    private final PressingLane[] lanes = new PressingLane[LANES];
    private final ContainerData data;
    // Lanes that pressed last tick, for the GUI.
    private int pressing;

    public MetalPressingArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.METAL_PRESSING_ARRAY.get(), pos, state, MACHINE_SLOTS, MetalPressingArrayBlockEntity::isItemValid, UPGRADES,
                ArcforgeConfig.PRESSING_ARRAY_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.PRESSING_ARRAY_MAX_INPUT.getAsInt(),
                new int[] { inputSlot(0), inputSlot(1), inputSlot(2) }, MetalPressingArrayBlockEntity::isProductSlot);
        for (int lane = 0; lane < LANES; lane++) {
            lanes[lane] = new PressingLane(dieSlot(lane), inputSlot(lane), outputSlot(lane));
        }
        this.data = new WideIntContainerData(MetalPressingArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= MetalPressingArrayMenu.DATA_PROGRESS_FIRST && index < MetalPressingArrayMenu.DATA_PROGRESS_FIRST + LANES) {
                    return lanes[index - MetalPressingArrayMenu.DATA_PROGRESS_FIRST].getProgress();
                }
                if (index >= MetalPressingArrayMenu.DATA_TOTAL_FIRST && index < MetalPressingArrayMenu.DATA_TOTAL_FIRST + LANES) {
                    return lanes[index - MetalPressingArrayMenu.DATA_TOTAL_FIRST].getTotal();
                }
                return switch (index) {
                    case MetalPressingArrayMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case MetalPressingArrayMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case MetalPressingArrayMenu.DATA_USAGE -> usage;
                    case MetalPressingArrayMenu.DATA_PRESSING -> pressing;
                    case MetalPressingArrayMenu.DATA_POWER_SAVING -> powerSaving();
                    case MetalPressingArrayMenu.DATA_STATUS -> status.ordinal();
                    case MetalPressingArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case MetalPressingArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static int dieSlot(int lane) {
        return lane * 3;
    }

    public static int inputSlot(int lane) {
        return lane * 3 + 1;
    }

    public static int outputSlot(int lane) {
        return lane * 3 + 2;
    }

    private static boolean isProductSlot(int slot) {
        return slot < MACHINE_SLOTS && slot % 3 == 2;
    }

    // Die slots take dies; input slots anything some die presses (the lane's die decides what it makes).
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        if (slot >= MACHINE_SLOTS) {
            return false;
        }
        return switch (slot % 3) {
            case 0 -> resource.toStack(1).is(ModItemTags.DIES);
            case 1 -> MachineRecipes.isPressingInput(level, resource.toStack(1));
            default -> false;
        };
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // Automation only feeds a lane whose die presses the item.
    @Override
    protected boolean acceptsLaneInput(int slot, ItemResource resource) {
        return MachineRecipes.isPressingInput(level, items.getStack(slot - 1), resource.toStack(1));
    }

    // --- Running ---

    // FE/t per working lane: Speed draws it faster, Energy cuts it.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.PRESSING_ARRAY_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // FE saved per operation compared with an un-upgraded Metal Press, in percent (60 without upgrades).
    private int powerSaving() {
        double press = ArcforgeConfig.PRESS_ENERGY_PER_TICK.getAsInt();
        double array = ArcforgeConfig.PRESSING_ARRAY_ENERGY_PER_TICK.getAsInt() * ArcforgeConfig.PRESSING_ARRAY_TIME_MULTIPLIER.getAsDouble()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY));
        return (int) Math.round(100.0 * (1.0 - array / press));
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return MetalPressingArrayCasingBlock.STRUCTURE;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isFormed()) {
            return;
        }
        usage = 0;
        pressing = 0;
        if (!redstoneMode.canRun(isPowered(level))) {
            status = MachineStatus.DISABLED;
        } else {
            PressingLane.Settings settings = new PressingLane.Settings(
                    ArcforgeConfig.PRESSING_ARRAY_TIME_MULTIPLIER.getAsDouble() / speedMultiplier(),
                    energyPerTick());
            boolean full = false;
            boolean noPower = false;
            int noDie = 0;
            for (PressingLane lane : lanes) {
                switch (lane.tick(level, items, energy, settings)) {
                    case WORKING -> {
                        pressing++;
                        usage += settings.energyPerTick();
                    }
                    case OUTPUT_FULL -> full = true;
                    case NO_POWER -> noPower = true;
                    case NO_DIE -> noDie++;
                    case IDLE -> { }
                }
            }
            status = pressing > 0 ? MachineStatus.PRESSING : full ? MachineStatus.OUTPUT_FULL : noPower ? MachineStatus.NO_POWER
                    : noDie == LANES ? MachineStatus.NO_DIE : MachineStatus.IDLE;
            if (pressing > 0) {
                setChanged();
            }
        }
        setLit(pressing > 0);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            pushOutputs(level);
        }
    }

    public PressingLane getLane(int lane) {
        return lanes[lane];
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int lane = 0;
        for (ValueInput entry : input.childrenListOrEmpty("lanes")) {
            if (lane < LANES) {
                lanes[lane++].deserialize(entry, items);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.ValueOutputList list = output.childrenList("lanes");
        for (PressingLane lane : lanes) {
            lane.serialize(list.addChild());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.metal_pressing_array");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MetalPressingArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
