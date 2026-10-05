/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control.spec;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GlassClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GrowChamberBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.HydroponicCellBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockBreakerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockPlacerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.TreeCutterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.machine.control.MachineControlSpec;
import net.zagdrath.arcforge.machine.control.MachineControls;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The machine control API (docs/API.md) for: Automation machines and the automated farms.
public final class AutomationMachineSpecs {
    // The config's own ceiling for vacuum.maxRange; ranges past the configured maximum are refused.
    private static final int VACUUM_RANGE_LIMIT = 16;

    private AutomationMachineSpecs() {}

    public static void register() {
        MachineControls.register(ModBlockEntityTypes.ARC_QUARRY.get(), MachineControlSpec.builder(ArcQuarryBlockEntity.class)
                .energy(EnergyRole.CONSUMER, ArcQuarryBlockEntity::getEnergy, ArcQuarryBlockEntity::getUsage)
                .slot(ArcQuarryBlockEntity.SLOT_REPLACE, SlotRole.INPUT)
                .slots(ArcQuarryBlockEntity.FIRST_BUFFER, ArcQuarryBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .items(ArcQuarryBlockEntity::getItems, be -> be.getItemHandler(null))
                // The whole job: the scan's share of the area, then the targets mined so far.
                .progress(AutomationMachineSpecs::quarryProgress)
                .remaining(be -> be.getQuarryState() == ArcQuarryBlockEntity.State.MINING
                        ? OptionalInt.of(be.getCooldown() + Math.max(0, be.getTargetCount() - be.getTargetIndex() - 1) * be.ticksPerBlock())
                        : OptionalInt.empty())
                // The Start/Stop button: true starts (or resumes) it, false stops it. A scan without mining counts as running.
                .booleanOption("running", AutomationMachineSpecs::quarryRunning, (be, running) -> {
                    if (running != quarryRunning(be)) {
                        be.toggleRunning();
                    }
                })
                .build());

        MachineControls.register(ModBlockEntityTypes.ASSEMBLER.get(), MachineControlSpec.builder(AssemblerBlockEntity.class)
                .energy(EnergyRole.CONSUMER, AssemblerBlockEntity::getEnergy, AssemblerBlockEntity::getUsage)
                .slots(AssemblerBlockEntity.FIRST_BUFFER, AssemblerBlockEntity.FIRST_BUFFER + AssemblerBlockEntity.BUFFER_SLOTS, SlotRole.INPUT)
                .slot(AssemblerBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .slots(AssemblerBlockEntity.FIRST_REMAINDER, AssemblerBlockEntity.FIRST_REMAINDER + AssemblerBlockEntity.REMAINDER_SLOTS, SlotRole.OUTPUT)
                .items(AssemblerBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(AssemblerBlockEntity::getProgress, AssemblerBlockEntity::craftTicks)
                // The pattern's result, as the GUI previews it (leftovers aren't known until the craft).
                .outputs(be -> be.getProgress() > 0 ? List.of(be.getPreview().getItem(0)) : List.of())
                .build());

        MachineControls.register(ModBlockEntityTypes.BLOCK_BREAKER.get(), MachineControlSpec.builder(BlockBreakerBlockEntity.class)
                .energy(EnergyRole.CONSUMER, BlockBreakerBlockEntity::getEnergy, BlockBreakerBlockEntity::getUsage)
                .slots(0, BlockBreakerBlockEntity.SLOTS, SlotRole.OUTPUT)
                .items(BlockBreakerBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(BlockBreakerBlockEntity::getProgress, BlockBreakerBlockEntity::getTotal)
                .build());

        MachineControls.register(ModBlockEntityTypes.BLOCK_PLACER.get(), MachineControlSpec.builder(BlockPlacerBlockEntity.class)
                .energy(EnergyRole.CONSUMER, BlockPlacerBlockEntity::getEnergy, BlockPlacerBlockEntity::getUsage)
                .slots(0, BlockPlacerBlockEntity.SLOTS, SlotRole.INPUT)
                .items(BlockPlacerBlockEntity::getItems, be -> be.getItemHandler(null))
                .build());

        MachineControls.register(ModBlockEntityTypes.TREE_CUTTER.get(), MachineControlSpec.builder(TreeCutterBlockEntity.class)
                .energy(EnergyRole.CONSUMER, TreeCutterBlockEntity::getEnergy, TreeCutterBlockEntity::getUsage)
                .slots(0, TreeCutterBlockEntity.SAPLING_SLOTS, SlotRole.INPUT)
                .slot(TreeCutterBlockEntity.SLOT_FERTILIZER, SlotRole.INPUT)
                .slots(TreeCutterBlockEntity.FIRST_OUTPUT, TreeCutterBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .items(TreeCutterBlockEntity::getItems, be -> be.getItemHandler(null))
                .build());

        MachineControls.register(ModBlockEntityTypes.VACUUM_COLLECTOR.get(), MachineControlSpec.builder(VacuumCollectorBlockEntity.class)
                .energy(EnergyRole.CONSUMER, VacuumCollectorBlockEntity::getEnergy, VacuumCollectorBlockEntity::getUsage)
                .slot(VacuumCollectorBlockEntity.SLOT_FILTER, SlotRole.OTHER)
                .slots(VacuumCollectorBlockEntity.FIRST_BUFFER, VacuumCollectorBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .items(VacuumCollectorBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.OUTPUT, VacuumCollectorBlockEntity::getXpTank)
                .fluidAutomation(be -> be.getFluidHandler(null))
                // As the GUI's buttons: 1 to the configured maximum (read when set, as the config can change).
                .intOption("range", 1, VACUUM_RANGE_LIMIT, VacuumCollectorBlockEntity::getRange, (be, range) -> {
                    if (range <= VacuumCollectorBlockEntity.maxRange()) {
                        be.setRange(range);
                    }
                })
                .build());

        // The Glass Cloche has no energy.
        MachineControls.register(ModBlockEntityTypes.GLASS_CLOCHE.get(), cloche(GlassClocheBlockEntity.class).build());
        MachineControls.register(ModBlockEntityTypes.GROW_CHAMBER.get(), cloche(GrowChamberBlockEntity.class)
                .energy(EnergyRole.CONSUMER, be -> be.getEnergy().getAmountAsLong(), be -> be.getEnergy().getCapacityAsLong(), GrowChamberBlockEntity::getUsage)
                .build());
        MachineControls.register(ModBlockEntityTypes.HYDROPONIC_CELL.get(), cloche(HydroponicCellBlockEntity.class)
                .energy(EnergyRole.CONSUMER, be -> be.getEnergy().getAmountAsLong(), be -> be.getEnergy().getCapacityAsLong(), HydroponicCellBlockEntity::getUsage)
                .tank(SlotRole.INPUT, HydroponicCellBlockEntity::getCo2Tank)
                .build());
    }

    private static boolean quarryRunning(ArcQuarryBlockEntity be) {
        return be.getQuarryState() == ArcQuarryBlockEntity.State.MINING || be.getQuarryState() == ArcQuarryBlockEntity.State.SCANNING;
    }

    private static OptionalDouble quarryProgress(ArcQuarryBlockEntity be) {
        return switch (be.getQuarryState()) {
            case SCANNING -> OptionalDouble.of(be.scanPercent() / 100.0);
            case MINING -> be.getTargetCount() > 0 ? OptionalDouble.of(Math.min(1.0, (double) be.getTargetIndex() / be.getTargetCount())) : OptionalDouble.empty();
            default -> OptionalDouble.empty();
        };
    }

    // What the three farms share: the seed and soil stay, the fertilizer is used up, the harvest comes out; the water (or
    // Nutrient Solution) tank first. Growth isn't one per tick, so remaining is worked out from last tick's rate.
    private static <T extends ClocheBlockEntity> MachineControlSpec.Builder<T> cloche(Class<T> type) {
        return MachineControlSpec.builder(type)
                .slot(ClocheBlockEntity.SLOT_SEED, SlotRole.CATALYST)
                .slot(ClocheBlockEntity.SLOT_SOIL, SlotRole.CATALYST)
                .slot(ClocheBlockEntity.SLOT_FERTILIZER, SlotRole.INPUT)
                .slots(ClocheBlockEntity.SLOT_OUTPUT_FIRST, ClocheBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .items(T::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, T::getTank)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .progress(be -> be.getTotal() > 0 && be.getProgress() > 0 ? OptionalDouble.of(be.growth()) : OptionalDouble.empty())
                .remaining(be -> be.getTotal() > 0 && be.getProgress() > 0 && be.getRate() > 0
                        ? OptionalInt.of((int) Math.ceil((be.getTotal() - be.getProgress()) / be.getRate()))
                        : OptionalInt.empty());
    }
}
