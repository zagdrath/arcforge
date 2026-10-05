/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.api.gas.GasHandler;
import net.zagdrath.arcforge.api.machine.MachineCapabilities;
import net.zagdrath.arcforge.api.machine.MachineControl;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBoundingBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.multiblock.BatteryArrayStructure;
import net.zagdrath.arcforge.multiblock.FireboxArrayStructure;
import net.zagdrath.arcforge.multiblock.GreenhouseStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure;

// The machine control API (docs/API.md): which block entity types it covers (MachineSpecs registers a spec for each),
// the MACHINE_CONTROL capability on every Arcforge block, and which machine a block belongs to.
public final class MachineControls {
    private static final Map<BlockEntityType<?>, MachineControlSpec<?>> SPECS = new IdentityHashMap<>();

    private MachineControls() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MachineControls::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(MachineControlTracker::onLevelTick);
    }

    public static <T extends BlockEntity> void register(BlockEntityType<? extends T> type, MachineControlSpec<T> spec) {
        if (SPECS.put(type, spec) != null) {
            throw new IllegalStateException("Two machine control specs for " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type));
        }
    }

    public static boolean covers(BlockEntityType<?> type) {
        return SPECS.containsKey(type);
    }

    // Every Arcforge block: machines answer for themselves, and a formed structure's parts, glass and interior for its
    // controller. Blocks that are neither give null.
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        SPECS.clear();
        MachineSpecs.registerAll();
        Block[] blocks = BuiltInRegistries.BLOCK.stream()
                .filter(block -> Arcforge.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace()))
                .toArray(Block[]::new);
        event.registerBlock(MachineCapabilities.MACHINE_CONTROL, (level, pos, state, blockEntity, context) -> find(level, pos, state, blockEntity), blocks);
    }

    public static @Nullable MachineControl find(Level level, BlockPos pos) {
        return find(level, pos, level.getBlockState(pos), level.getBlockEntity(pos));
    }

    private static @Nullable MachineControl find(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        if (level.isClientSide()) {
            return null;
        }
        BlockEntity machine = resolve(level, pos, state, blockEntity);
        return machine == null ? null : of(machine);
    }

    // The block entity of the machine the block at pos belongs to (itself, for a machine), or null.
    public static @Nullable BlockEntity resolve(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        if (blockEntity != null && isPrimary(blockEntity)) {
            return blockEntity;
        }
        if (blockEntity instanceof ArcQuarryBoundingBlockEntity bounding) {
            return level.getBlockEntity(bounding.mainPos());
        }
        if (blockEntity instanceof CarbonizerBlockEntity carbonizer) {
            return carbonizer.getFormedMaster();
        }
        if (state.getBlock() instanceof MultiblockPart part) {
            return formed(part.findController(level, pos), pos);
        }
        // A window of a formed array belongs to the array, like its casings.
        if (state.getBlock() instanceof PressureGlassBlock) {
            BlockEntity master = formed(PressureGlassBlock.findMaster(level, pos), pos);
            if (master != null) {
                return master;
            }
            if (!PressureGlassBlock.isFormed(state)) {
                return null;
            }
            BlockEntity controller = formed(GreenhouseStructure.findController(level, pos), pos);
            if (controller == null) {
                controller = formed(ThermalEvaporatorStructure.findController(level, pos), pos);
            }
            if (controller != null) {
                return controller;
            }
        }
        // Inside a formed array: a Battery Array's cells and regulators, a Greenhouse's beds and lamps.
        BlockEntity inside = formed(BatteryArrayStructure.findController(level, pos), pos);
        if (inside == null) {
            inside = formed(FireboxArrayStructure.findController(level, pos), pos);
        }
        if (inside == null) {
            inside = formed(GreenhouseStructure.findController(level, pos), pos);
        }
        return inside;
    }

    // The controller's block entity if it's formed and its box holds pos.
    private static @Nullable BlockEntity formed(@Nullable MultiblockController controller, BlockPos pos) {
        return controller instanceof BlockEntity blockEntity && controller.isFormed() && controller.isInside(pos) ? blockEntity : null;
    }

    private static boolean isPrimary(BlockEntity blockEntity) {
        MachineControlSpec<?> spec = SPECS.get(blockEntity.getType());
        return spec != null && test(spec, blockEntity);
    }

    private static <T extends BlockEntity> boolean test(MachineControlSpec<T> spec, BlockEntity blockEntity) {
        return spec.type.isInstance(blockEntity) && spec.primary.test(spec.type.cast(blockEntity));
    }

    // The machine control for a machine's own block entity (a cube array's centre, a shell array's master...), made once
    // and kept in its state; null for anything else.
    public static @Nullable MachineControl of(BlockEntity blockEntity) {
        MachineControlSpec<?> spec = SPECS.get(blockEntity.getType());
        if (spec == null || !(blockEntity instanceof ControlStateHolder holder) || !test(spec, blockEntity)) {
            return null;
        }
        MachineControlState state = holder.machineControlState();
        if (state.control() instanceof SpecMachineControl<?> existing && existing.blockEntity() == blockEntity) {
            return existing;
        }
        MachineControl control = create(spec, blockEntity, state);
        state.setControl(control);
        return control;
    }

    // A machine's gas tanks for the gas API (GasCapabilities.BLOCK), from its own block entity; null if it has none.
    public static @Nullable GasHandler gases(BlockEntity machine) {
        return of(machine) instanceof SpecMachineControl<?> control ? control.gases() : null;
    }

    private static <T extends BlockEntity> MachineControl create(MachineControlSpec<T> spec, BlockEntity blockEntity, MachineControlState state) {
        return new SpecMachineControl<>(spec, spec.type.cast(blockEntity), state);
    }
}
