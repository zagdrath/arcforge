/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control.spec;

import java.util.function.Supplier;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.blockentity.machine.RefiningMachineBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CrackingUnitBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.DieselGeneratorBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity;
import net.zagdrath.arcforge.machine.control.MachineControlSpec;
import net.zagdrath.arcforge.machine.control.MachineControls;
import net.zagdrath.arcforge.machine.refining.RefiningCore;
import net.zagdrath.arcforge.recipe.RefiningProcess;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The machine control API (docs/API.md) for: the refinery's downstream units (the Catalytic Reformer, the Coker, the
// Lubricant Blender and the Cracking Unit) and the Diesel Generator. The Flare Stack is no machine (no status, no
// settings), like a tank.
public final class RefineryMachineSpecs {
    private RefineryMachineSpecs() {}

    public static void register() {
        refining(ModBlockEntityTypes.CATALYTIC_REFORMER, RefiningProcess.REFORMING);
        refining(ModBlockEntityTypes.COKER, RefiningProcess.COKING);
        refining(ModBlockEntityTypes.LUBRICANT_BLENDER, RefiningProcess.BLENDING);
        refining(ModBlockEntityTypes.HYDROTREATER, RefiningProcess.HYDROTREATING);
        refining(ModBlockEntityTypes.SULFUR_RECOVERY_UNIT, RefiningProcess.SULFUR_RECOVERY);
        refining(ModBlockEntityTypes.STEAM_METHANE_REFORMER, RefiningProcess.STEAM_REFORMING);

        // Progress: ticks into the batch. The catalyst slot, the feed, the Hydrogen tank and the four product tanks.
        MachineControlSpec.Builder<CrackingUnitBlockEntity> cracking = MachineControlSpec.builder(CrackingUnitBlockEntity.class)
                .slot(CrackingUnitBlockEntity.SLOT_CATALYST, SlotRole.CATALYST)
                .items(CrackingUnitBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, be -> be.getCore().feed())
                .tank(SlotRole.INPUT, be -> be.getCore().secondFeed());
        for (int i = 0; i < RefiningProcess.CRACKING.tanks(); i++) {
            int index = i;
            cracking.tank(SlotRole.OUTPUT, be -> be.getCore().product(index));
        }
        MachineControls.register(ModBlockEntityTypes.CRACKING_UNIT.get(), cracking
                .heat(HeatRole.CONSUMER, be -> be.getCore().heat(), be -> be.getCore().heatUsage(), be -> be.getCore().heatInput())
                .progress(be -> be.getCore().progress(), be -> be.getCore().total())
                .build());

        // The Steam Cracker: the feed and steam, the four product tanks; progress, ticks into the batch.
        MachineControlSpec.Builder<SteamCrackerBlockEntity> steamCracking = MachineControlSpec.builder(SteamCrackerBlockEntity.class)
                .items(SteamCrackerBlockEntity::getItems, null)
                .tank(SlotRole.INPUT, be -> be.getCore().feed())
                .tank(SlotRole.INPUT, be -> be.getCore().secondFeed());
        for (int i = 0; i < RefiningProcess.STEAM_CRACKING.tanks(); i++) {
            int index = i;
            steamCracking.tank(SlotRole.OUTPUT, be -> be.getCore().product(index));
        }
        MachineControls.register(ModBlockEntityTypes.STEAM_CRACKER.get(), steamCracking
                .heat(HeatRole.CONSUMER, be -> be.getCore().heat(), be -> be.getCore().heatUsage(), be -> be.getCore().heatInput())
                .progress(be -> be.getCore().progress(), be -> be.getCore().total())
                .build());

        // No progress: it burns its fuel as it goes. Its output is the FE/t it makes.
        MachineControls.register(ModBlockEntityTypes.DIESEL_GENERATOR.get(), MachineControlSpec.builder(DieselGeneratorBlockEntity.class)
                .energy(EnergyRole.GENERATOR, DieselGeneratorBlockEntity::getEnergy, DieselGeneratorBlockEntity::getMade)
                .items(DieselGeneratorBlockEntity::getItems, null)
                .tank(SlotRole.FUEL, DieselGeneratorBlockEntity::getFuel)
                .build());
    }

    // A single-block refining machine: FE (the Blender) or heat (the Reformer and the Coker), the feed, a tank for each
    // product, and the Coker's coke slot (the others have only upgrade slots, so no item automation). Progress: ticks into
    // the batch.
    private static void refining(Supplier<BlockEntityType<RefiningMachineBlockEntity>> type, RefiningProcess process) {
        RefiningCore.Layout layout = RefiningMachineBlockEntity.layout(process);
        MachineControlSpec.Builder<RefiningMachineBlockEntity> spec = MachineControlSpec.builder(RefiningMachineBlockEntity.class);
        if (process.powered()) {
            spec.energy(EnergyRole.CONSUMER, be -> be.getCore().energy(), be -> be.getCore().usage());
        }
        if (layout.itemSlot() >= 0) {
            spec.slot(layout.itemSlot(), SlotRole.OUTPUT);
        }
        spec.items(RefiningMachineBlockEntity::getItems, process.makesItem() ? be -> be.getItemHandler(null) : null)
                .tank(SlotRole.INPUT, be -> be.getCore().feed());
        if (process.secondFeed() != null) {
            spec.tank(SlotRole.INPUT, be -> be.getCore().secondFeed());
        }
        for (int i = 0; i < process.tanks(); i++) {
            int index = i;
            spec.tank(SlotRole.OUTPUT, be -> be.getCore().product(index));
        }
        if (process.heated()) {
            spec.heat(HeatRole.CONSUMER, be -> be.getCore().heat(), be -> be.getCore().heatUsage(), be -> be.getCore().heatInput());
        }
        MachineControls.register(type.get(), spec
                .fluidAutomation(be -> be.getFluidHandler(null))
                .progress(be -> be.getCore().progress(), be -> be.getCore().total())
                .build());
    }
}
