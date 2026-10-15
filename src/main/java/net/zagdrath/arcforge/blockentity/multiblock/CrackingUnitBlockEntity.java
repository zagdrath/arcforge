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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.RefiningMachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.refining.RefiningCore;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.RefiningMenu;
import net.zagdrath.arcforge.multiblock.CrackingUnitLayout;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.PlantKind;
import net.zagdrath.arcforge.recipe.CrackingMode;
import net.zagdrath.arcforge.recipe.RefiningProcess;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Cracking Unit (see OilfieldStructures.PlantBox), run by its controller: it cracks Heavy Oil with heat in the mode
// set in its GUI (CrackingMode, saved with it): over a catalyst into Naphtha, Diesel, Refinery Gas and Light Oil, or with
// Hydrogen into mostly Diesel and Kerosene (arcforge:cracking recipes; see RefiningCore for how it runs). Through its
// ports: Heavy Oil, Hydrogen and catalyst in (Input; Hydrogen also through Hydrogen ports), heat in (Heat), the liquids
// out of Output ports or each out of its own (Naphtha, Diesel, Kerosene, Light Oil) and the gas out of Refinery Gas
// ports, pushed every few ticks. Speed upgrades make it faster. Its regenerator's sight port glows as hot as the unit is
// (see CrackingUnitRenderer).
public class CrackingUnitBlockEntity extends PlantControllerBlockEntity {
    public static final int SLOT_CATALYST = 0;
    public static final int MACHINE_SLOTS = 1;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.HYDROGEN, SideMode.OUTPUT, SideMode.NAPHTHA,
            SideMode.DIESEL, SideMode.KEROSENE, SideMode.LIGHT_OIL, SideMode.REFINERY_GAS, SideMode.HEAT);
    // The glow is synced to clients when it moves by this much; it starts at GLOW_FROM °C and is full GLOW_SPAN hotter.
    private static final float GLOW_STEP = 0.05F, GLOW_FROM = 150.0F, GLOW_SPAN = 450.0F;

    private final RefiningCore core;
    private final ResourceHandler<ItemResource> catalystInput;
    // What Input ports take: Heavy Oil and Hydrogen.
    private final ResourceHandler<FluidResource> inputs;
    private final ContainerData data;
    private float glow;
    private float syncedGlow = -1.0F;

    public CrackingUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CRACKING_UNIT.get(), PlantKind.CRACKING_UNIT, pos, state, MACHINE_SLOTS,
                (level, slot, resource) -> slot == SLOT_CATALYST && RefiningCore.isCatalyst(level, RefiningProcess.CRACKING, resource), UPGRADES,
                SIDE_MODES);
        this.core = new RefiningCore(RefiningProcess.CRACKING, new RefiningCore.Layout(SLOT_CATALYST, -1, 0, 0,
                ArcforgeConfig.CRACKING_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.CRACKING_MAX_TEMPERATURE.getAsInt(),
                ArcforgeConfig.CRACKING_FEED_CAPACITY.getAsInt(), ArcforgeConfig.CRACKING_PRODUCT_CAPACITY.getAsInt(),
                ArcforgeConfig.CRACKING_SECOND_FEED_CAPACITY.getAsInt()), items, () -> level, this::setChanged);
        this.catalystInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_CATALYST, slot -> false);
        ResourceHandler<FluidResource> hydrogen = core.secondFeedInput();
        this.inputs = hydrogen != null ? new CombinedResourceHandler<>(core.feedInput(), hydrogen) : core.feedInput();
        this.data = RefiningMachineBlockEntity.menuData(core, new RefiningMachineBlockEntity.MachineBlockEntityAccess() {
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
        });
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return clientItems(MACHINE_SLOTS, UPGRADES);
    }

    @Override
    protected List<float[]> solids(int length) {
        return CrackingUnitLayout.solids();
    }

    public RefiningCore getCore() {
        return core;
    }

    public CrackingMode getMode() {
        return core.mode();
    }

    // From the GUI's Mode tab (or a test).
    public void setMode(CrackingMode mode) {
        core.setMode(mode);
    }

    public void serverTick(ServerLevel level) {
        if (!tickStructure(level)) {
            return;
        }
        status = core.tick(level, redstoneMode.canRun(powered), upgrades(UpgradeType.SPEED), 0);
        boolean working = status == RefiningProcess.CRACKING.runningStatus();
        if (working && core.progress() == 0) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, core.mainProduct(), RefiningProcess.CRACKING.getSerializedName());
        }
        setRunning(working);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this, true);
        }
        int temperature = core.heat() != null ? core.heat().getTemperature() : 0;
        glow = Math.max(0.0F, Math.min(1.0F, (temperature - GLOW_FROM) / GLOW_SPAN));
        if (Math.abs(glow - syncedGlow) >= GLOW_STEP || glow == 0.0F && syncedGlow != 0.0F) {
            syncedGlow = glow;
            sync();
        }
    }

    // Client side: how brightly the regenerator's sight port glows (0 to 1).
    public float getGlow() {
        return Math.max(0.0F, syncedGlow);
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.INPUT ? catalystInput : null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return core.all();
        }
        return switch (mode) {
            case INPUT -> inputs;
            case HYDROGEN -> core.secondFeedInput();
            case OUTPUT -> core.liquidOutputs();
            case NAPHTHA -> core.outputHolding(ModFluids.NAPHTHA.get());
            case DIESEL -> core.outputHolding(ModFluids.DIESEL.get());
            case KEROSENE -> core.outputHolding(ModFluids.KEROSENE.get());
            case LIGHT_OIL -> core.outputHolding(ModFluids.LIGHT_OIL.get());
            case REFINERY_GAS -> core.outputHolding(ModFluids.REFINERY_GAS.get());
            default -> null;
        };
    }

    @Override
    public @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return core.heatInput();
        }
        return faceMode(pos, side) == SideMode.HEAT ? core.heatInput() : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT
                    : mode == SideMode.OUTPUT || mode == SideMode.NAPHTHA || mode == SideMode.DIESEL || mode == SideMode.KEROSENE
                            || mode == SideMode.LIGHT_OIL ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            // Hydrogen is a gas: it comes in through Hydrogen (and Input) ports on gas conduits.
            case GAS -> mode == SideMode.REFINERY_GAS ? ConnectionMode.OUTPUT
                    : mode == SideMode.INPUT || mode == SideMode.HYDROGEN ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        core.load(input);
        syncedGlow = input.getFloatOr("glow", 0.0F);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        core.save(output);
        output.putFloat("glow", Math.max(0.0F, syncedGlow));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.cracking_unit");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RefiningMenu(RefiningProcess.CRACKING, containerId, inventory, worldPosition, items, data);
    }
}
