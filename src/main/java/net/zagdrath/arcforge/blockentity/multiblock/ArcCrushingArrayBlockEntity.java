/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.block.multiblock.ArcCrushingArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.CrushingLane;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.ArcCrushingArrayMenu;
import net.zagdrath.arcforge.multiblock.ArcCrushingArrayStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Every Arc Crushing Array casing has one of these; only the centre's runs. Three crushing lanes work
// in parallel, twice as fast as an Arc Crusher and for 60% less FE an operation, and ore recipes give
// double the main output. Side configuration applies to the faces of the whole cube. Each casing drops
// only its own items, so the machine's contents drop when the centre is broken.
public class ArcCrushingArrayBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int LANES = 3;
    public static final int MACHINE_SLOTS = LANES * 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final CrushingLane[] lanes = new CrushingLane[LANES];
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;
    private int usage;

    public ArcCrushingArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ARC_CRUSHING_ARRAY.get(), pos, state, MACHINE_SLOTS, ArcCrushingArrayBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.ARRAY_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.ARRAY_MAX_INPUT.getAsInt(),
                this::setChanged);
        for (int lane = 0; lane < LANES; lane++) {
            lanes[lane] = new CrushingLane(inputSlot(lane), outputSlot(lane), bonusSlot(lane));
        }
        this.itemInput = new BalancedInput(items);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, ArcCrushingArrayBlockEntity::isProductSlot);
        this.itemAutomation = new BalancedInput(items) {
            @Override
            public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                return isProductSlot(index) ? items.extract(index, resource, amount, transaction) : 0;
            }

            @Override
            public int extract(ItemResource resource, int amount, TransactionContext transaction) {
                return itemOutput.extract(resource, amount, transaction);
            }
        };
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

    // Items put in through input faces go to the lane holding the fewest.
    private static class BalancedInput extends AutomationResourceHandler<ItemResource> {
        private final MachineItemHandler items;

        BalancedInput(MachineItemHandler items) {
            super(items, slot -> slot < MACHINE_SLOTS && slot % 3 == 0, slot -> false);
            this.items = items;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            Integer[] order = { 0, 1, 2 };
            java.util.Arrays.sort(order, java.util.Comparator.comparingInt(lane -> items.getStack(inputSlot(lane)).getCount()));
            int inserted = 0;
            for (int lane : order) {
                if (inserted >= amount) {
                    break;
                }
                inserted += insert(inputSlot(lane), resource, amount - inserted, transaction);
            }
            return inserted;
        }
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
            MultiblockAutomation.pushOutputs(level, this);
        }
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            if (level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    public CrushingLane getLane(int lane) {
        return lanes[lane];
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // The facing turned or the side configuration changed: every face of the cube may be different now.
    public void onStructureChanged() {
        if (level != null) {
            MultiblockAutomation.refresh(level, getMinCorner(), getMaxCorner());
        }
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        onStructureChanged();
    }

    // --- Structure (MultiblockController) ---

    @Override
    public boolean isFormed() {
        return getBlockState().getValue(ArcCrushingArrayCasingBlock.PART) == ArcCrushingArrayCasingBlock.Part.CENTER;
    }

    @Override
    public Direction getStructureFacing() {
        return getBlockState().getValue(ArcCrushingArrayCasingBlock.FACING);
    }

    @Override
    public BlockPos getMinCorner() {
        return worldPosition.offset(-1, -1, -1);
    }

    @Override
    public BlockPos getMaxCorner() {
        return worldPosition.offset(1, 1, 1);
    }

    @Override
    public boolean isPart(BlockPos pos) {
        return isInside(pos);
    }

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return null;
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID, THERMAL -> ConnectionMode.NONE;
        };
    }

    // Conduits next to any casing ask it; it answers for the structure face it lies on.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        ArcCrushingArrayBlockEntity controller = level != null ? ArcCrushingArrayStructure.findController(level, worldPosition) : null;
        return controller != null ? controller.conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
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
        energy.serialize(output.child("energy"));
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
