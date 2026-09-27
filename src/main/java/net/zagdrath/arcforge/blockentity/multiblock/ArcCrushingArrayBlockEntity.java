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
import net.zagdrath.arcforge.block.multiblock.ArcCrushingArrayCasingBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.CrushingLane;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.ArcCrushingArrayMenu;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Arc Crushing Array (see CubeMultiblockBlockEntity). Three crushing lanes work in parallel, twice
// as fast as an Arc Crusher and for 60% less FE an operation, and ore recipes give double the main output.
public class ArcCrushingArrayBlockEntity extends CubeMultiblockBlockEntity {
    public static final int LANES = 3;
    public static final int MACHINE_SLOTS = LANES * 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);

    private final CrushingLane[] lanes = new CrushingLane[LANES];
    private final ContainerData data;

    public ArcCrushingArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ARC_CRUSHING_ARRAY.get(), pos, state, MACHINE_SLOTS, ArcCrushingArrayBlockEntity::isItemValid, UPGRADES,
                ArcforgeConfig.ARRAY_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.ARRAY_MAX_INPUT.getAsInt(),
                new int[] { inputSlot(0), inputSlot(1), inputSlot(2) }, ArcCrushingArrayBlockEntity::isProductSlot);
        for (int lane = 0; lane < LANES; lane++) {
            lanes[lane] = new CrushingLane(inputSlot(lane), outputSlot(lane), bonusSlot(lane));
        }
        this.data = new WideIntContainerData(ArcCrushingArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= ArcCrushingArrayMenu.DATA_PROGRESS_FIRST && index < ArcCrushingArrayMenu.DATA_PROGRESS_FIRST + LANES) {
                    return lanes[index - ArcCrushingArrayMenu.DATA_PROGRESS_FIRST].getProgress();
                }
                if (index >= ArcCrushingArrayMenu.DATA_TOTAL_FIRST && index < ArcCrushingArrayMenu.DATA_TOTAL_FIRST + LANES) {
                    return lanes[index - ArcCrushingArrayMenu.DATA_TOTAL_FIRST].getTotal();
                }
                return switch (index) {
                    case ArcCrushingArrayMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ArcCrushingArrayMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case ArcCrushingArrayMenu.DATA_USAGE -> usage;
                    case ArcCrushingArrayMenu.DATA_ORE_YIELD -> ArcforgeConfig.ARRAY_ORE_YIELD.getAsInt();
                    case ArcCrushingArrayMenu.DATA_POWER_SAVING -> powerSaving();
                    case ArcCrushingArrayMenu.DATA_STATUS -> status.ordinal();
                    case ArcCrushingArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ArcCrushingArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static int inputSlot(int lane) {
        return lane * 3;
    }

    public static int outputSlot(int lane) {
        return lane * 3 + 1;
    }

    public static int bonusSlot(int lane) {
        return lane * 3 + 2;
    }

    private static boolean isProductSlot(int slot) {
        return slot < MACHINE_SLOTS && slot % 3 != 0;
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot < MACHINE_SLOTS && slot % 3 == 0 && MachineRecipes.isCrusherInput(level, resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // --- Running ---

    // FE/t per working lane: Speed draws it faster, Energy cuts it.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.ARRAY_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // FE saved per operation compared with an un-upgraded Arc Crusher, in percent (60 without upgrades).
    private int powerSaving() {
        double crusher = ArcforgeConfig.CRUSHER_ENERGY_PER_TICK.getAsInt();
        double array = ArcforgeConfig.ARRAY_ENERGY_PER_TICK.getAsInt() * ArcforgeConfig.ARRAY_TIME_MULTIPLIER.getAsDouble()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY));
        return (int) Math.round(100.0 * (1.0 - array / crusher));
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return ArcCrushingArrayCasingBlock.STRUCTURE;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isFormed()) {
            return;
        }
        usage = 0;
        boolean working = false;
        if (!redstoneMode.canRun(isPowered(level))) {
            status = MachineStatus.DISABLED;
        } else {
            CrushingLane.Settings settings = new CrushingLane.Settings(
                    ArcforgeConfig.ARRAY_TIME_MULTIPLIER.getAsDouble() / speedMultiplier(),
                    energyPerTick(),
                    ArcforgeConfig.ARRAY_ORE_YIELD.getAsInt());
            boolean full = false;
            boolean noPower = false;
            for (CrushingLane lane : lanes) {
                switch (lane.tick(level, items, energy, settings)) {
                    case WORKING -> {
                        working = true;
                        usage += settings.energyPerTick();
                    }
                    case OUTPUT_FULL -> full = true;
                    case NO_POWER -> noPower = true;
                    case IDLE -> { }
                }
            }
            status = working ? MachineStatus.CRUSHING : full ? MachineStatus.OUTPUT_FULL : noPower ? MachineStatus.NO_POWER : MachineStatus.IDLE;
            if (working) {
                setChanged();
            }
        }
        setLit(working);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            pushOutputs(level);
        }
    }

    public CrushingLane getLane(int lane) {
        return lanes[lane];
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int lane = 0;
        for (ValueInput entry : input.childrenListOrEmpty("lanes")) {
            if (lane < LANES) {
                lanes[lane++].deserialize(entry);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.ValueOutputList list = output.childrenList("lanes");
        for (CrushingLane lane : lanes) {
            lane.serialize(list.addChild());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.arc_crushing_array");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ArcCrushingArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
