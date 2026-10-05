/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.api.machine.MachineCapabilities;
import net.zagdrath.arcforge.api.machine.MachineControl;
import net.zagdrath.arcforge.api.machine.StructureSize;
import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.resource.MachineEnergy;
import net.zagdrath.arcforge.api.machine.resource.MachineFluids;
import net.zagdrath.arcforge.api.machine.resource.MachineHeat;
import net.zagdrath.arcforge.api.machine.resource.MachineItems;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.api.machine.settings.MachinePort;
import net.zagdrath.arcforge.api.machine.settings.MachineSettings;
import net.zagdrath.arcforge.api.machine.settings.MachineSide;
import net.zagdrath.arcforge.api.machine.settings.RedstoneMode;
import net.zagdrath.arcforge.api.machine.settings.SettingResult;
import net.zagdrath.arcforge.api.machine.settings.SideModes;
import net.zagdrath.arcforge.api.machine.status.CompletedOperation;
import net.zagdrath.arcforge.api.machine.status.MachineListener;
import net.zagdrath.arcforge.api.machine.status.MachineStatistics;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;
import net.zagdrath.arcforge.block.multiblock.BiogasDigesterControllerBlock;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayControllerBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockBreakerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FiberizerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// The machine control API (docs/API.md), looked up only through its capability as another mod would: which blocks
// provide it (every single-block machine, found from the block registry; every block of each formed multiblock; an
// unformed controller), and that its status, progress, energy, heat, slots, tanks, settings, events and statistics
// agree with the machines' own state.
public final class MachineControlGameTests {
    private MachineControlGameTests() {}

    // --- Helpers ---

    // The machine control at a test-relative position, or null.
    static @Nullable MachineControl control(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getCapability(MachineCapabilities.MACHINE_CONTROL, helper.absolutePos(relative), null);
    }

    private static MachineControl require(GameTestHelper helper, BlockPos relative, String what) {
        MachineControl control = control(helper, relative);
        helper.assertTrue(control != null, what + " provides no machine control");
        return control;
    }

    private static Identifier typeId(BlockEntity blockEntity) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
    }

    // An Arc Crusher at pos with 8 Speed upgrades (13-tick operations) and plenty of FE.
    private static ArcCrusherBlockEntity fastCrusher(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ARC_CRUSHER.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(pos, ArcCrusherBlockEntity.class);
        CrushingGameTests.install(crusher.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
        CrushingGameTests.charge(crusher.getEnergy(), 100_000);
        return crusher;
    }

    // What the crusher's output and bonus slots hold, in items.
    private static int produced(ArcCrusherBlockEntity crusher) {
        return crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT).getCount() + crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_BONUS).getCount();
    }

    // Records every event it gets.
    private static final class Recorder implements MachineListener {
        final List<String> statuses = new ArrayList<>();
        final List<CompletedOperation> operations = new ArrayList<>();
        final List<Component> faults = new ArrayList<>();

        @Override
        public void onStatusChanged(MachineControl machine, MachineStatus previous, MachineStatus current) {
            statuses.add(previous + "->" + current);
        }

        @Override
        public void onOperationCompleted(MachineControl machine, CompletedOperation operation) {
            operations.add(operation);
        }

        @Override
        public void onFault(MachineControl machine, Component reason) {
            faults.add(reason);
        }
    }

    // --- Coverage: single blocks ---

    // Every Arcforge block whose block entity is a single-block machine (found from the registry, so a new machine
    // without a spec fails here) provides a machine control that describes it: its type, a formed single block at its
    // own position, and an energy or heat view exactly when the machine exposes FE or heat.
    static void singleBlockCoverage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(pos);
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        int machines = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!Arcforge.MODID.equals(id.getNamespace()) || !(block instanceof EntityBlock entityBlock)) {
                continue;
            }
            // Only machines are placed: a probe block entity says what the block is.
            BlockEntity probe = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            if (!(probe instanceof MachineBlockEntity) || probe instanceof MultiblockController) {
                continue;
            }
            machines++;
            helper.setBlock(pos, block);
            BlockEntity blockEntity = level.getBlockEntity(absolute);
            if (!(blockEntity instanceof MachineBlockEntity machine)) {
                problems.add(id + ": no machine block entity once placed");
                helper.setBlock(pos, Blocks.AIR);
                continue;
            }
            MachineControl control = control(helper, pos);
            if (control == null) {
                problems.add(id + ": no machine control");
                helper.setBlock(pos, Blocks.AIR);
                continue;
            }
            if (control(helper, pos) != control) {
                problems.add(id + ": a second lookup gave another instance");
            }
            if (!control.machineType().equals(typeId(machine))) {
                problems.add(id + ": type " + control.machineType() + ", expected " + typeId(machine));
            }
            if (control.isMultiblock() || !control.isFormed() || control.structureSize().isPresent()) {
                problems.add(id + ": multiblock " + control.isMultiblock() + ", formed " + control.isFormed() + ", size " + control.structureSize());
            }
            if (!control.position().equals(absolute) || !control.isValid()) {
                problems.add(id + ": position " + control.position() + ", valid " + control.isValid());
            }
            if (control.tier().isPresent() || control.status() == MachineStatus.NOT_FORMED || control.displayName().getString().isEmpty()) {
                problems.add(id + ": tier " + control.tier() + ", status " + control.status() + ", name '" + control.displayName().getString() + "'");
            }
            MachineSettings settings = control.settings();
            MachineStatistics statistics = control.statistics();
            if (settings == null || statistics == null) {
                problems.add(id + ": no settings or statistics");
            } else {
                if (!settings.isEnabled() || !settings.hasSideConfiguration() || !settings.ports().isEmpty()
                        || settings.setPort(absolute, Direction.UP, SideModes.INPUT) != SettingResult.UNSUPPORTED) {
                    problems.add(id + ": enabled " + settings.isEnabled() + ", sides " + settings.hasSideConfiguration() + ", ports " + settings.ports());
                }
                if (statistics.operationsCompleted() != 0 || statistics.itemsProduced() != 0) {
                    problems.add(id + ": a new machine has statistics");
                }
            }
            // Every single-block machine has slots (its upgrade slots at least), all of them visible.
            if (control.items().map(MachineItems::slotCount).orElse(-1) != machine.getItems().size()) {
                problems.add(id + ": items " + control.items().map(MachineItems::slotCount) + ", expected " + machine.getItems().size() + " slots");
            }
            EnergyHandler energy = level.getCapability(Capabilities.Energy.BLOCK, absolute, null);
            if (control.energy().isPresent() != (energy != null)) {
                problems.add(id + ": energy view " + control.energy().isPresent() + ", FE capability " + (energy != null));
            } else if (energy != null) {
                MachineEnergy view = control.energy().get();
                if (view.stored() != energy.getAmountAsLong() || view.capacity() != energy.getCapacityAsLong()) {
                    problems.add(id + ": energy " + view.stored() + "/" + view.capacity() + ", capability " + energy.getAmountAsLong() + "/" + energy.getCapacityAsLong());
                }
            }
            boolean hasHeat = level.getCapability(ModCapabilities.HEAT, absolute, null) != null;
            if (control.heat().isPresent() != hasHeat) {
                problems.add(id + ": heat view " + control.heat().isPresent() + ", heat capability " + hasHeat);
            }
            if (level.getCapability(Capabilities.Fluid.BLOCK, absolute, null) != null && control.fluids().isEmpty()) {
                problems.add(id + ": exposes fluids but has no fluid view");
            }
            control.fluids().ifPresent(fluids -> {
                if (fluids.tankCount() == 0) {
                    problems.add(id + ": a fluid view with no tanks");
                }
            });
            helper.setBlock(pos, Blocks.AIR);
            if (control.isValid()) {
                problems.add(id + ": still valid once broken");
            }
        }
        helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems));
        helper.assertTrue(machines >= 35, "Only " + machines + " single-block machines found");
        helper.succeed();
    }

    // Storage blocks, conduits, the Meter, the Chargepad, the Chunk Loader, the Quantum Tunnel, plain blocks and loose
    // casings of cube and shell arrays aren't machines.
    static void nonMachinesHaveNone(GameTestHelper helper) {
        List<BlockState> others = List.of(ModBlocks.STEEL_BLOCK.get().defaultBlockState(), ModBlocks.RESERVOIR.get().defaultBlockState(),
                ModBlocks.energyCell(ConduitTier.WROUGHT).get().defaultBlockState(), ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get().defaultBlockState(),
                ModBlocks.conduit(ConduitType.ITEM, ConduitTier.WROUGHT).get().defaultBlockState(), ModBlocks.ENERGY_METER.get().defaultBlockState(),
                ModBlocks.CHARGEPAD.get().defaultBlockState(), ModBlocks.CHUNK_LOADER.get().defaultBlockState(), ModBlocks.QUANTUM_TUNNEL.get().defaultBlockState(),
                ModBlocks.COMPOST_BIN.get().defaultBlockState(), ModBlocks.PRESSURE_GLASS.get().defaultBlockState(),
                ModBlocks.ARC_CRUSHING_ARRAY_CASING.get().defaultBlockState(), ModBlocks.SUPERHEATER_ARRAY_CASING.get().defaultBlockState(),
                ModBlocks.STEAM_TURBINE_ARRAY_CASING.get().defaultBlockState(), ModBlocks.GAS_TURBINE_ARRAY_CASING.get().defaultBlockState(),
                ModBlocks.DIGESTER_CASING.get().defaultBlockState(), ModBlocks.FIREBOX_ARRAY_CASING.get().defaultBlockState());
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < others.size(); i++) {
            BlockPos pos = new BlockPos(i % 6 * 2, 1, i / 6 * 2);
            helper.setBlock(pos, others.get(i));
            placed.add(pos);
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (int i = 0; i < placed.size(); i++) {
                        helper.assertTrue(control(helper, placed.get(i)) == null, others.get(i).getBlock() + " provides a machine control");
                    }
                    // Air and vanilla blocks have nothing either.
                    helper.assertTrue(control(helper, new BlockPos(1, 4, 1)) == null, "Air provides a machine control");
                })
                .thenSucceed();
    }

    // --- Coverage: multiblocks ---

    // The formed multiblock whose controller (or master) is at controller: every Arcforge block in its bounding box gives
    // the same machine control, which says it's a formed multiblock at the controller with the structure's size.
    private static MachineControl checkFormed(GameTestHelper helper, BlockPos controller, String name) {
        MachineControl control = require(helper, controller, name + "'s controller");
        ServerLevel level = helper.getLevel();
        helper.assertTrue(control.isMultiblock() && control.isFormed(), name + ": multiblock " + control.isMultiblock() + ", formed " + control.isFormed());
        helper.assertTrue(control.position().equals(helper.absolutePos(controller)),
                name + " is at " + helper.relativePos(control.position()) + ", expected its controller at " + controller);
        BlockEntity blockEntity = level.getBlockEntity(control.position());
        helper.assertTrue(blockEntity instanceof MultiblockController, name + "'s position holds " + blockEntity);
        MultiblockController structure = (MultiblockController) blockEntity;
        helper.assertTrue(structure.isFormed(), name + " isn't formed");
        helper.assertTrue(control.machineType().equals(typeId(blockEntity)), name + " is " + control.machineType() + ", expected " + typeId(blockEntity));
        helper.assertTrue(control.status() != MachineStatus.NOT_FORMED, name + " reports " + control.status());
        BlockPos min = structure.getMinCorner();
        BlockPos max = structure.getMaxCorner();
        int dx = max.getX() - min.getX() + 1, dy = max.getY() - min.getY() + 1, dz = max.getZ() - min.getZ() + 1;
        StructureSize size = control.structureSize().orElse(null);
        helper.assertTrue(size != null, name + " has no structure size");
        helper.assertTrue(size.height() == dy && (size.width() == dx && size.depth() == dz || size.width() == dz && size.depth() == dx),
                name + " is " + size + ", its box " + dx + "x" + dy + "x" + dz);
        int checked = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !Arcforge.MODID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
                continue;
            }
            MachineControl at = level.getCapability(MachineCapabilities.MACHINE_CONTROL, pos, null);
            helper.assertTrue(at == control, name + ": " + state.getBlock() + " at " + helper.relativePos(pos) + " gives "
                    + (at == null ? "nothing" : "another machine at " + helper.relativePos(at.position())));
            checked++;
        }
        helper.assertTrue(checked > 1, name + ": only " + checked + " blocks checked");
        return control;
    }

    // The five cube arrays, 3x3x3 each (the centre is the machine), and a Superheater grown into a 5x3x4 box (its corner is).
    static void cubeArraysCoverage(GameTestHelper helper) {
        MultiblockTestHelpers.buildCube(helper, new BlockPos(0, 1, 0), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, new BlockPos(4, 1, 0), ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, new BlockPos(8, 1, 0), ModBlocks.METAL_PRESSING_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, new BlockPos(0, 1, 4), ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, new BlockPos(4, 1, 4), ModBlocks.CONDENSER_ARRAY_CASING.get());
        BlockPos boxMin = new BlockPos(0, 1, 8);
        for (BlockPos pos : BlockPos.betweenClosed(boxMin, boxMin.offset(4, 2, 3))) {
            helper.setBlock(pos.immutable(), ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        }
        MachineControl[] crushing = new MachineControl[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    checkFormed(helper, new BlockPos(1, 2, 1), "Arc Crushing Array");
                    checkFormed(helper, new BlockPos(5, 2, 1), "Induction Furnace Array");
                    checkFormed(helper, new BlockPos(9, 2, 1), "Metal Pressing Array");
                    checkFormed(helper, new BlockPos(1, 2, 5), "Superheater Array");
                    checkFormed(helper, new BlockPos(5, 2, 5), "Condenser Array");
                    checkFormed(helper, boxMin, "Superheater box");
                    crushing[0] = control(helper, new BlockPos(1, 2, 1));
                    // Breaking a casing leaves loose casings, which belong to nothing.
                    helper.setBlock(new BlockPos(0, 1, 0), Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(!crushing[0].isValid() && crushing[0].status() == MachineStatus.NOT_FORMED,
                            "A broken array's machine control is still valid (" + crushing[0].status() + ")");
                    helper.assertTrue(control(helper, new BlockPos(1, 2, 1)) == null, "A broken Arc Crushing Array's centre provides a machine control");
                    helper.assertTrue(control(helper, new BlockPos(2, 3, 2)) == null, "A broken Arc Crushing Array's casing provides a machine control");
                })
                .thenSucceed();
    }

    // The three shell arrays (the minimum corner is the master), with a window in the boiler.
    static void shellArraysCoverage(GameTestHelper helper) {
        SteamGameTests.buildShell(helper, new BlockPos(0, 1, 0), Direction.Axis.Y, 4, ModBlocks.STEAM_BOILER_ARRAY_CASING.get(), new BlockPos(1, 2, 0));
        SteamGameTests.buildShell(helper, new BlockPos(0, 1, 4), Direction.Axis.X, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        SteamGameTests.buildShell(helper, new BlockPos(4, 1, 0), Direction.Axis.X, 5, ModBlocks.GAS_TURBINE_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    checkFormed(helper, new BlockPos(0, 1, 0), "Steam Boiler Array");
                    checkFormed(helper, new BlockPos(0, 1, 4), "Steam Turbine Array");
                    checkFormed(helper, new BlockPos(4, 1, 0), "Gas Turbine Array");
                })
                .thenSucceed();
    }

    // A 3x3x3 Biogas Digester with its controller in the middle of the south side.
    private static BlockPos buildDigester(GameTestHelper helper, BlockPos min) {
        BlockPos controller = min.offset(1, 1, 2);
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(2, 2, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.DIGESTER_CASING.get());
        }
        helper.setBlock(controller, ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get().defaultBlockState().setValue(BiogasDigesterControllerBlock.FACING, Direction.SOUTH));
        return controller;
    }

    // A 2x2 Distillation column 4 tall, with its controller at min + (0, 1, 0) facing north.
    private static BlockPos buildColumn(GameTestHelper helper, BlockPos min) {
        for (int y = 0; y < 4; y++) {
            for (BlockPos pos : BlockPos.betweenClosed(min.above(y), min.offset(1, y, 1))) {
                helper.setBlock(pos.immutable(), y == 0 || y == 3 ? ModBlocks.DISTILLATION_ARRAY_CASING.get() : ModBlocks.TRAY_LEVEL_CASING.get());
            }
        }
        BlockPos controller = min.above();
        helper.setBlock(controller, ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get().defaultBlockState().setValue(DistillationArrayControllerBlock.FACING, Direction.NORTH));
        return controller;
    }

    // The Biogas Digester, the Firebox Array (with a window), the Battery Array (with a window and its cell inside) and
    // the Thermal Evaporator (with windows); and the role rules for each.
    static void controllerArraysCoverage(GameTestHelper helper) {
        BlockPos digester = buildDigester(helper, new BlockPos(0, 1, 0));
        BlockPos fireboxMin = new BlockPos(4, 1, 0);
        BlockPos firebox = BigArrayGameTests.buildFirebox(helper, fireboxMin, 3, 3, 3, Set.of(fireboxMin.offset(2, 1, 1)));
        BlockPos batteryMin = new BlockPos(8, 1, 0);
        BlockPos battery = LithiumGameTests.buildBattery(helper, batteryMin, 3, 3, 3, Set.of(batteryMin.offset(2, 1, 1)), ConduitTier.WROUGHT, 0, ConduitTier.WROUGHT);
        BlockPos evaporator = ThermalEvaporatorGameTests.buildTower(helper, new BlockPos(0, 1, 5), true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    checkFormed(helper, digester, "Biogas Digester");
                    checkFormed(helper, firebox, "Firebox Array");
                    checkFormed(helper, battery, "Battery Array");
                    helper.assertTrue(control(helper, batteryMin.offset(1, 1, 1)) == control(helper, battery), "The Battery Array's cell isn't part of it");
                    checkFormed(helper, evaporator, "Thermal Evaporator");
                    checkRoles(helper, "Multiblock", digester, firebox, battery, evaporator);
                })
                .thenSucceed();
    }

    // The Solar Thermal Array (its collectors on top), the Distillation Array and the Greenhouse (its glass, beds and lamp);
    // and the role rules for each.
    static void towerArraysCoverage(GameTestHelper helper) {
        BlockPos solar = SolarGameTests.buildTower(helper, new BlockPos(0, 1, 0), Direction.NORTH).getBlockPos();
        BlockPos column = buildColumn(helper, new BlockPos(3, 1, 0));
        BlockPos greenhouseOrigin = new BlockPos(6, 1, 0);
        BlockPos greenhouse = GreenhouseGameTests.build(helper, greenhouseOrigin);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    checkFormed(helper, helper.relativePos(solar), "Solar Thermal Array");
                    checkFormed(helper, column, "Distillation Array");
                    MachineControl house = checkFormed(helper, greenhouse, "Greenhouse");
                    // The lamp inside, under the roof's middle.
                    helper.assertTrue(control(helper, greenhouseOrigin.offset(2, 2, 2)) == house, "The Greenhouse's lamp isn't part of it");
                    checkRoles(helper, "Multiblock", helper.relativePos(solar), column, greenhouse);
                })
                .thenSucceed();
    }

    // The Arcforge Furnace (its port is the controller) and a three-slice Carbonizer (its master is the left, bottom,
    // front block); and the role rules for each.
    static void furnaceArraysCoverage(GameTestHelper helper) {
        MultiblockGameTests.buildFurnace(helper);
        BlockState carbonizer = ModBlocks.CARBONIZER.get().defaultBlockState().setValue(CarbonizerBlock.FACING, Direction.NORTH);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(5, 1, 0), new BlockPos(7, 2, 1))) {
            helper.setBlock(pos.immutable(), carbonizer);
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    checkFormed(helper, new BlockPos(1, 1, 0), "Arcforge Furnace");
                    checkFormed(helper, new BlockPos(5, 1, 0), "Carbonizer");
                    checkRoles(helper, "Furnace", new BlockPos(1, 1, 0), new BlockPos(5, 1, 0));
                })
                .thenSucceed();
    }

    // A multiblock with a controller block answers from its controller while unformed: NOT_FORMED, no size, no progress.
    static void unformedControllers(GameTestHelper helper) {
        List<Block> controllers = List.of(ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get(), ModBlocks.FIREBOX_ARRAY_CONTROLLER.get(),
                ModBlocks.BATTERY_ARRAY_CONTROLLER.get(), ModBlocks.THERMAL_EVAPORATOR_CONTROLLER.get(), ModBlocks.SOLAR_THERMAL_ARRAY_CONTROLLER.get(),
                ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get(), ModBlocks.GREENHOUSE_CONTROLLER.get());
        for (int i = 0; i < controllers.size(); i++) {
            helper.setBlock(new BlockPos(i % 4 * 3, 1, i / 4 * 3), controllers.get(i));
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (int i = 0; i < controllers.size(); i++) {
                        BlockPos pos = new BlockPos(i % 4 * 3, 1, i / 4 * 3);
                        String name = controllers.get(i).getName().getString();
                        MachineControl control = require(helper, pos, name);
                        helper.assertTrue(control.isMultiblock() && !control.isFormed(), name + ": multiblock " + control.isMultiblock() + ", formed " + control.isFormed());
                        helper.assertTrue(control.status() == MachineStatus.NOT_FORMED, name + " reports " + control.status());
                        helper.assertTrue(control.structureSize().isEmpty() && control.progress().isEmpty(), name + " has a size or progress");
                        helper.assertTrue(control.position().equals(helper.absolutePos(pos)) && control.isValid(), name + " isn't at its block");
                        helper.assertTrue(control.settings().ports().isEmpty(), name + " has ports");
                        helper.assertTrue(control.settings().setPort(helper.absolutePos(pos), Direction.UP, SideModes.NONE) == SettingResult.REJECTED,
                                name + " took a port while unformed");
                    }
                })
                .thenSucceed();
    }

    // --- Status, progress, energy and heat ---

    // A working Arc Crusher reports RUNNING with its own progress, energy and FE/t; without FE it reports NO_POWER and
    // without an input IDLE.
    static void crusherStatus(GameTestHelper helper) {
        BlockPos running = new BlockPos(0, 1, 0), unpowered = new BlockPos(2, 1, 0), idle = new BlockPos(4, 1, 0);
        for (BlockPos pos : new BlockPos[] { running, unpowered, idle }) {
            helper.setBlock(pos, ModBlocks.ARC_CRUSHER.get());
        }
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(running, ArcCrusherBlockEntity.class);
        CrushingGameTests.charge(crusher.getEnergy(), 100_000);
        CrushingGameTests.charge(helper.getBlockEntity(idle, ArcCrusherBlockEntity.class).getEnergy(), 100_000);
        crusher.getItems().setStack(ArcCrusherBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON, 4));
        helper.getBlockEntity(unpowered, ArcCrusherBlockEntity.class).getItems().setStack(ArcCrusherBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON, 4));
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    MachineControl control = require(helper, running, "Arc Crusher");
                    helper.assertTrue(control.status() == MachineStatus.RUNNING, "Working crusher reports " + control.status() + " (" + crusher.getStatus() + ")");
                    helper.assertTrue(control.statusReason().getString().equals(crusher.getStatus().getDescription().getString()),
                            "Reason '" + control.statusReason().getString() + "', its own '" + crusher.getStatus().getDescription().getString() + "'");
                    int done = crusher.getLane().getProgress(), total = crusher.getLane().getTotal();
                    helper.assertTrue(done > 0 && total > 0, "Lane at " + done + "/" + total);
                    helper.assertTrue(control.progress().isPresent() && Math.abs(control.progress().getAsDouble() - (double) done / total) < 1e-9,
                            "Progress " + control.progress() + ", lane " + done + "/" + total);
                    helper.assertTrue(control.ticksRemaining().orElse(-1) == total - done, "Remaining " + control.ticksRemaining() + ", lane " + done + "/" + total);
                    helper.assertTrue(control.currentOutputs().stream().anyMatch(stack -> stack.is(ModItems.IRON_DUST.get())),
                            "Current outputs " + control.currentOutputs());
                    MachineEnergy energy = control.energy().orElse(null);
                    helper.assertTrue(energy != null && energy.role() == EnergyRole.CONSUMER, "Energy " + energy);
                    helper.assertTrue(energy.stored() == crusher.getEnergy().getAmountAsLong() && energy.capacity() == crusher.getEnergy().getCapacityAsLong(),
                            "Energy " + energy.stored() + "/" + energy.capacity() + ", its own " + crusher.getEnergy().getAmountAsLong() + "/" + crusher.getEnergy().getCapacityAsLong());
                    helper.assertTrue(energy.perTick() > 0 && energy.perTick() == crusher.getUsage(), "FE/t " + energy.perTick() + ", its own " + crusher.getUsage());
                    helper.assertTrue(control.heat().isEmpty() && control.fluids().isEmpty(), "An Arc Crusher has heat or fluids");

                    MachineControl noPower = require(helper, unpowered, "Unpowered Arc Crusher");
                    helper.assertTrue(noPower.status() == MachineStatus.NO_POWER, "Unpowered crusher reports " + noPower.status());
                    helper.assertTrue(noPower.energy().map(MachineEnergy::perTick).orElse(-1L) == 0, "Unpowered crusher uses FE");
                    MachineControl idleControl = require(helper, idle, "Idle Arc Crusher");
                    helper.assertTrue(idleControl.status() == MachineStatus.IDLE, "Empty crusher reports " + idleControl.status());
                    helper.assertTrue(idleControl.progress().isEmpty() && idleControl.ticksRemaining().isEmpty() && idleControl.currentOutputs().isEmpty(),
                            "Empty crusher has progress " + idleControl.progress() + " or outputs " + idleControl.currentOutputs());
                })
                .thenSucceed();
    }

    // A burning Combustion Plant is a generator reporting the FE it makes; a burning Firebox reports its heat buffer, and a
    // Fiberizer takes heat in.
    static void generatorAndHeatStatus(GameTestHelper helper) {
        BlockPos plantPos = new BlockPos(0, 1, 0), fireboxPos = new BlockPos(3, 1, 0), fiberizerPos = new BlockPos(6, 1, 0);
        helper.setBlock(plantPos, ModBlocks.COMBUSTION_PLANT.get());
        helper.setBlock(fiberizerPos, ModBlocks.FIBERIZER.get());
        helper.setBlock(fireboxPos, ModBlocks.FIREBOX.get());
        MachineControl plant = require(helper, plantPos, "Combustion Plant");
        MachineControl firebox = require(helper, fireboxPos, "Firebox");
        helper.assertTrue(plant.items().orElseThrow().role(BurnerBlockEntity.SLOT_FUEL) == SlotRole.FUEL, "The fuel slot isn't FUEL");
        helper.assertTrue(plant.items().orElseThrow().insert(new ItemStack(Items.COAL, 2), false).isEmpty(), "The plant refused coal");
        helper.assertTrue(firebox.items().orElseThrow().insert(BurnerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 2), false).isEmpty(), "The Firebox refused coal");
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    CombustionPlantBlockEntity own = helper.getBlockEntity(plantPos, CombustionPlantBlockEntity.class);
                    helper.assertTrue(plant.status() == MachineStatus.RUNNING, "Burning plant reports " + plant.status() + " (" + own.getStatus() + ")");
                    MachineEnergy energy = plant.energy().orElse(null);
                    helper.assertTrue(energy != null && energy.role() == EnergyRole.GENERATOR, "Plant energy " + energy);
                    helper.assertTrue(energy.perTick() > 0 && energy.perTick() == own.getOutputPerTick(), "Plant FE/t " + energy.perTick() + ", its own " + own.getOutputPerTick());
                    helper.assertTrue(energy.stored() == own.getEnergy().getAmountAsLong() && energy.stored() > 0, "Plant FE " + energy.stored());
                    helper.assertTrue(plant.progress().isPresent(), "A burning plant has no progress");

                    FireboxBlockEntity ownFirebox = helper.getBlockEntity(fireboxPos, FireboxBlockEntity.class);
                    HeatBuffer buffer = ownFirebox.getHeat();
                    helper.assertTrue(firebox.status() == MachineStatus.RUNNING, "Burning Firebox reports " + firebox.status());
                    helper.assertTrue(firebox.energy().isEmpty(), "A Firebox has FE");
                    MachineHeat heat = firebox.heat().orElse(null);
                    helper.assertTrue(heat != null && heat.role() == HeatRole.PRODUCER, "Firebox heat " + heat);
                    helper.assertTrue(heat.stored() == buffer.getStored() && heat.capacity() == buffer.getCapacity() && heat.stored() > 0,
                            "Heat " + heat.stored() + "/" + heat.capacity() + ", its buffer " + buffer.getStored() + "/" + buffer.getCapacity());
                    helper.assertTrue(heat.temperature() == buffer.getTemperature() && heat.maxTemperature() == buffer.getMaxCelsius(),
                            "Temperature " + heat.temperature() + "/" + heat.maxTemperature() + ", its buffer " + buffer.getTemperature() + "/" + buffer.getMaxCelsius());
                    helper.assertTrue(heat.perTick() > 0 && heat.perTick() == ownFirebox.getOutputPerTick(), "HU/t " + heat.perTick() + ", its own " + ownFirebox.getOutputPerTick());
                    // A producer gives heat; nothing can put heat in.
                    helper.assertTrue(heat.insert(100, true) == 0, "Heat went into a Firebox");
                    int stored = buffer.getStored();
                    int taken = heat.extract(50, true);
                    helper.assertTrue(taken > 0 && buffer.getStored() == stored, "Simulated extract took " + taken + ", buffer " + stored + " -> " + buffer.getStored());

                    // A consumer takes heat in and gives none out.
                    FiberizerBlockEntity fiberizer = helper.getBlockEntity(fiberizerPos, FiberizerBlockEntity.class);
                    MachineHeat consumer = require(helper, fiberizerPos, "Fiberizer").heat().orElse(null);
                    helper.assertTrue(consumer != null && consumer.role() == HeatRole.CONSUMER, "Fiberizer heat " + consumer);
                    int before = fiberizer.getHeat().getStored();
                    helper.assertTrue(consumer.insert(100, true) == 100 && fiberizer.getHeat().getStored() == before, "Simulated heat changed the buffer");
                    helper.assertTrue(consumer.insert(100, false) == 100 && fiberizer.getHeat().getStored() == before + 100,
                            "Heat in: " + before + " -> " + fiberizer.getHeat().getStored());
                    helper.assertTrue(consumer.stored() == fiberizer.getHeat().getStored(), "Fiberizer heat reads " + consumer.stored());
                    helper.assertTrue(consumer.extract(50, false) == 0, "Heat came out of a Fiberizer");
                })
                .thenSucceed();
    }

    // --- Slot and tank roles ---

    // Inputs take what the machine's filter allows and give nothing back; outputs give and never take; upgrade slots are
    // read only; simulating changes nothing.
    static void slotRoles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        ArcCrusherBlockEntity crusher = fastCrusher(helper, pos);
        MachineItems items = require(helper, pos, "Arc Crusher").items().orElseThrow();
        int firstUpgrade = crusher.getItems().getFirstUpgradeSlot();
        helper.assertTrue(items.role(ArcCrusherBlockEntity.SLOT_INPUT) == SlotRole.INPUT, "Input is " + items.role(ArcCrusherBlockEntity.SLOT_INPUT));
        helper.assertTrue(items.role(ArcCrusherBlockEntity.SLOT_OUTPUT) == SlotRole.OUTPUT && items.role(ArcCrusherBlockEntity.SLOT_BONUS) == SlotRole.OUTPUT,
                "Outputs are " + items.role(ArcCrusherBlockEntity.SLOT_OUTPUT) + ", " + items.role(ArcCrusherBlockEntity.SLOT_BONUS));
        for (int slot = firstUpgrade; slot < items.slotCount(); slot++) {
            helper.assertTrue(items.role(slot) == SlotRole.UPGRADE, "Slot " + slot + " is " + items.role(slot));
        }
        helper.assertTrue(items.role(-1) == SlotRole.OTHER && items.role(items.slotCount()) == SlotRole.OTHER, "Slots out of range have a role");
        helper.assertTrue(items.stack(firstUpgrade).is(ModItems.SPEED_UPGRADE.get()) && items.stack(firstUpgrade).getCount() == 8, "Upgrade slot reads " + items.stack(firstUpgrade));
        // Empty slots: a stack of 64, or what the slot is limited to (8 of an upgrade).
        helper.assertTrue(items.capacity(ArcCrusherBlockEntity.SLOT_INPUT) == 64, "Input capacity " + items.capacity(ArcCrusherBlockEntity.SLOT_INPUT));
        helper.assertTrue(items.capacity(firstUpgrade + 1) == 8, "Upgrade slot capacity " + items.capacity(firstUpgrade + 1));
        helper.assertTrue(items.capacity(items.slotCount()) == 0, "A slot out of range has a capacity");

        // Simulated: nothing moves.
        helper.assertTrue(items.insert(ArcCrusherBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON, 2), true).isEmpty(), "Simulated insert refused");
        helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_INPUT).isEmpty(), "Simulated insert changed the input");
        // Not into an output or upgrade slot, nor anything the crusher can't crush.
        helper.assertTrue(items.insert(ArcCrusherBlockEntity.SLOT_OUTPUT, new ItemStack(Items.RAW_IRON), false).getCount() == 1, "An output slot took raw iron");
        helper.assertTrue(items.insert(firstUpgrade + 1, new ItemStack(ModItems.ENERGY_UPGRADE.get()), false).getCount() == 1, "An upgrade slot took an upgrade");
        helper.assertTrue(!crusher.getItems().isValid(ArcCrusherBlockEntity.SLOT_INPUT, ItemResource.of(Items.TOTEM_OF_UNDYING)), "Totems are crushable");
        helper.assertTrue(items.insert(ArcCrusherBlockEntity.SLOT_INPUT, new ItemStack(Items.TOTEM_OF_UNDYING), false).getCount() == 1, "The input took a totem");
        helper.assertTrue(items.insert(new ItemStack(Items.TOTEM_OF_UNDYING), false).getCount() == 1, "A fill without a slot took a totem");
        helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT).isEmpty() && crusher.getItems().getStack(firstUpgrade + 1).isEmpty(),
                "A refused insert changed a slot");
        // Into the input: taken, and can't be taken back.
        helper.assertTrue(items.insert(ArcCrusherBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON, 2), false).isEmpty(), "The input refused raw iron");
        helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_INPUT).getCount() == 2, "Input holds " + crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_INPUT));
        helper.assertTrue(items.extract(ArcCrusherBlockEntity.SLOT_INPUT, 64, false).isEmpty(), "Raw iron came back out of the input");
        helper.assertTrue(items.extract(firstUpgrade, 64, false).isEmpty() && crusher.getItems().getStack(firstUpgrade).getCount() == 8, "An upgrade came out");
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    int made = crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT).getCount();
                    helper.assertTrue(made >= 2, "Output holds " + crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT));
                    ItemStack simulated = items.extract(ArcCrusherBlockEntity.SLOT_OUTPUT, 1, true);
                    helper.assertTrue(simulated.is(ModItems.IRON_DUST.get()) && simulated.getCount() == 1, "Simulated extract gave " + simulated);
                    helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT).getCount() == made, "Simulated extract took dust");
                    ItemStack taken = items.extract(ArcCrusherBlockEntity.SLOT_OUTPUT, 64, false);
                    helper.assertTrue(taken.is(ModItems.IRON_DUST.get()) && taken.getCount() == made, "Extract gave " + taken + ", expected " + made);
                    helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT).isEmpty(), "Dust left after extracting it all");
                })
                .thenSucceed();
    }

    // The Electrolyzer's water tank takes only what it splits and gives nothing back; its gas tanks give and never take.
    static void tankRoles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.ELECTROLYZER.get());
        ElectrolyzerBlockEntity electrolyzer = helper.getBlockEntity(pos, ElectrolyzerBlockEntity.class);
        MachineControl control = require(helper, pos, "Electrolyzer");
        MachineFluids fluids = control.fluids().orElseThrow();
        helper.assertTrue(fluids.tankCount() == 4, "Electrolyzer has " + fluids.tankCount() + " tanks");
        helper.assertTrue(fluids.role(0) == SlotRole.INPUT && fluids.role(1) == SlotRole.OUTPUT && fluids.role(2) == SlotRole.OUTPUT
                && fluids.role(3) == SlotRole.OUTPUT && fluids.role(4) == SlotRole.OTHER, "Tank roles " + fluids.role(0) + " " + fluids.role(1));
        helper.assertTrue(fluids.capacity(0) == electrolyzer.getWater().getCapacity(), "Water capacity " + fluids.capacity(0));
        // Simulated: nothing moves.
        helper.assertTrue(fluids.insert(0, new FluidStack(Fluids.WATER, 1_000), true) == 1_000, "Simulated water refused");
        helper.assertTrue(electrolyzer.getWater().getAmount() == 0, "Simulated fill changed the tank");
        // Not into an output tank, nor a fluid it doesn't split.
        helper.assertTrue(fluids.insert(1, new FluidStack(ModFluids.HYDROGEN.get(), 500), false) == 0, "The hydrogen tank took hydrogen");
        helper.assertTrue(fluids.insert(1, new FluidStack(Fluids.WATER, 500), false) == 0, "The hydrogen tank took water");
        helper.assertTrue(fluids.insert(0, new FluidStack(Fluids.LAVA, 500), false) == 0, "The water tank took lava");
        helper.assertTrue(fluids.insert(new FluidStack(Fluids.LAVA, 500), false) == 0, "A fill without a tank took lava");
        // Water goes in, and doesn't come back out.
        helper.assertTrue(fluids.insert(0, new FluidStack(Fluids.WATER, 1_000), false) == 1_000, "The water tank refused water");
        helper.assertTrue(fluids.fluid(0).is(Fluids.WATER) && fluids.fluid(0).getAmount() == 1_000 && electrolyzer.getWater().getAmount() == 1_000,
                "Water tank reads " + fluids.fluid(0));
        helper.assertTrue(fluids.extract(0, 1_000, false).isEmpty() && electrolyzer.getWater().getAmount() == 1_000, "Water came back out");
        helper.assertTrue(fluids.insert(new FluidStack(Fluids.WATER, 500), false) == 500, "A fill without a tank refused water");
        // Hydrogen in its tank comes out.
        electrolyzer.getHydrogen().set(0, FluidResource.of(ModFluids.HYDROGEN.get()), 400);
        FluidStack simulated = fluids.extract(1, 100, true);
        helper.assertTrue(simulated.getAmount() == 100 && electrolyzer.getHydrogen().getAmount() == 400, "Simulated drain gave " + simulated);
        FluidStack drained = fluids.extract(1, 1_000, false);
        helper.assertTrue(drained.is(ModFluids.HYDROGEN.get()) && drained.getAmount() == 400 && electrolyzer.getHydrogen().getAmount() == 0, "Drained " + drained);
        helper.succeed();
    }

    // --- Settings ---

    // The redstone mode and side modes follow the GUI's rules: only modes it offers, the same value is UNCHANGED.
    // Auto-eject only where there are outputs. Ports only on multiblocks.
    static void redstoneAndSides(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0), plantPos = new BlockPos(2, 1, 0), breakerPos = new BlockPos(4, 1, 0);
        helper.setBlock(pos, ModBlocks.ARC_CRUSHER.get());
        helper.setBlock(plantPos, ModBlocks.THERMOELECTRIC_PLANT.get());
        helper.setBlock(breakerPos, ModBlocks.BLOCK_BREAKER.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(pos, ArcCrusherBlockEntity.class);
        MachineSettings settings = require(helper, pos, "Arc Crusher").settings();

        helper.assertTrue(settings.redstoneMode().orElse(null) == RedstoneMode.IGNORE, "Redstone mode " + settings.redstoneMode());
        helper.assertTrue(settings.allowedRedstoneModes().equals(List.of(RedstoneMode.IGNORE, RedstoneMode.HIGH, RedstoneMode.LOW)),
                "Allowed modes " + settings.allowedRedstoneModes());
        helper.assertTrue(settings.setRedstoneMode(RedstoneMode.HIGH) == SettingResult.APPLIED, "HIGH wasn't applied");
        helper.assertTrue(crusher.getRedstoneMode() == net.zagdrath.arcforge.machine.config.RedstoneMode.HIGH, "Crusher's mode is " + crusher.getRedstoneMode());
        helper.assertTrue(settings.redstoneMode().orElse(null) == RedstoneMode.HIGH, "Mode reads " + settings.redstoneMode());
        helper.assertTrue(settings.setRedstoneMode(RedstoneMode.HIGH) == SettingResult.UNCHANGED, "HIGH again wasn't UNCHANGED");
        helper.assertTrue(settings.setRedstoneMode(RedstoneMode.PULSE) == SettingResult.INVALID, "PULSE wasn't INVALID");
        helper.assertTrue(settings.setRedstoneMode(RedstoneMode.THROTTLE) == SettingResult.INVALID, "THROTTLE wasn't INVALID");
        helper.assertTrue(crusher.getRedstoneMode() == net.zagdrath.arcforge.machine.config.RedstoneMode.HIGH, "A refused mode changed it");
        MachineSettings breaker = require(helper, breakerPos, "Block Breaker").settings();
        helper.assertTrue(breaker.allowedRedstoneModes().contains(RedstoneMode.PULSE) && breaker.setRedstoneMode(RedstoneMode.PULSE) == SettingResult.APPLIED,
                "The Block Breaker refused PULSE: allowed " + breaker.allowedRedstoneModes());

        helper.assertTrue(settings.hasSideConfiguration() && settings.allowedSideModes().equals(List.of(SideModes.NONE, SideModes.INPUT, SideModes.OUTPUT, SideModes.ENERGY)),
                "Side modes " + settings.allowedSideModes());
        RelativeSide[] relative = { RelativeSide.TOP, RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.BACK, RelativeSide.FRONT };
        for (MachineSide side : MachineSide.values()) {
            helper.assertTrue(settings.sideMode(side).equals(crusher.getSideMode(relative[side.ordinal()]).getSerializedName()),
                    side + " reads " + settings.sideMode(side) + ", the crusher has " + crusher.getSideMode(relative[side.ordinal()]));
        }
        String front = settings.sideMode(MachineSide.FRONT);
        String next = front.equals(SideModes.OUTPUT) ? SideModes.INPUT : SideModes.OUTPUT;
        helper.assertTrue(settings.setSideMode(MachineSide.FRONT, next) == SettingResult.APPLIED, "Front " + next + " wasn't applied");
        helper.assertTrue(crusher.getSideMode(RelativeSide.FRONT).getSerializedName().equals(next), "Crusher's front is " + crusher.getSideMode(RelativeSide.FRONT));
        helper.assertTrue(settings.setSideMode(MachineSide.FRONT, next) == SettingResult.UNCHANGED, "The same side mode wasn't UNCHANGED");
        helper.assertTrue(settings.setSideMode(MachineSide.FRONT, SideModes.HEAT) == SettingResult.INVALID, "Heat wasn't INVALID on a crusher");
        helper.assertTrue(settings.setSideMode(MachineSide.FRONT, "sideways") == SettingResult.INVALID, "An unknown mode wasn't INVALID");
        helper.assertTrue(crusher.getSideMode(RelativeSide.FRONT).getSerializedName().equals(next), "A refused side mode changed it");
        helper.assertTrue(settings.clearSideModes() == SettingResult.APPLIED, "Clearing wasn't applied");
        for (RelativeSide side : RelativeSide.values()) {
            helper.assertTrue(crusher.getSideMode(side) == SideMode.NONE, side + " is " + crusher.getSideMode(side) + " after clearing");
        }
        helper.assertTrue(settings.clearSideModes() == SettingResult.UNCHANGED, "Clearing again wasn't UNCHANGED");

        helper.assertTrue(settings.supportsAutoEject(), "The crusher has outputs but no auto-eject");
        boolean eject = crusher.isAutoEject();
        helper.assertTrue(settings.isAutoEject() == eject, "Auto-eject reads " + settings.isAutoEject());
        helper.assertTrue(settings.setAutoEject(!eject) == SettingResult.APPLIED && crusher.isAutoEject() == !eject, "Auto-eject wasn't toggled");
        helper.assertTrue(settings.setAutoEject(!eject) == SettingResult.UNCHANGED, "The same auto-eject wasn't UNCHANGED");
        MachineSettings plant = require(helper, plantPos, "Thermoelectric Plant").settings();
        helper.assertTrue(!plant.supportsAutoEject() && plant.setAutoEject(true) == SettingResult.UNSUPPORTED, "A Thermoelectric Plant has auto-eject");
        helper.assertTrue(plant.options().isEmpty() && plant.setOption("vent_hydrogen", "true") == SettingResult.UNSUPPORTED, "A Thermoelectric Plant has options");

        helper.assertTrue(settings.ports().isEmpty() && settings.allowedPortModes().isEmpty(), "A single block has ports");
        helper.assertTrue(settings.setPort(helper.absolutePos(pos), Direction.UP, SideModes.INPUT) == SettingResult.UNSUPPORTED, "A single block took a port");
        helper.assertTrue(settings.upgrades().isEmpty() && settings.acceptedUpgrades().contains("speed"), "Upgrades " + settings.upgrades() + ", accepted " + settings.acceptedUpgrades());
        helper.succeed();
    }

    // Machine-specific options: the Electrolyzer's venting (booleans) and the Vacuum Collector's range (an integer up to
    // the configured maximum).
    static void machineOptions(GameTestHelper helper) {
        BlockPos electrolyzerPos = new BlockPos(0, 1, 0), vacuumPos = new BlockPos(2, 1, 0);
        helper.setBlock(electrolyzerPos, ModBlocks.ELECTROLYZER.get());
        helper.setBlock(vacuumPos, ModBlocks.VACUUM_COLLECTOR.get());
        ElectrolyzerBlockEntity electrolyzer = helper.getBlockEntity(electrolyzerPos, ElectrolyzerBlockEntity.class);
        MachineSettings settings = require(helper, electrolyzerPos, "Electrolyzer").settings();
        helper.assertTrue(settings.options().stream().map(option -> option.id()).toList().equals(List.of("vent_hydrogen", "vent_oxygen")),
                "Electrolyzer options " + settings.options());
        helper.assertTrue(!electrolyzer.isVentingHydrogen(), "Venting from the start");
        helper.assertTrue(settings.setOption("vent_hydrogen", "true") == SettingResult.APPLIED && electrolyzer.isVentingHydrogen(), "Venting hydrogen wasn't applied");
        helper.assertTrue(!electrolyzer.isVentingOxygen(), "Venting hydrogen vents oxygen");
        helper.assertTrue(settings.options().get(0).value().equals("true"), "Option reads " + settings.options().get(0).value());
        helper.assertTrue(settings.setOption("vent_hydrogen", "true") == SettingResult.UNCHANGED, "The same value wasn't UNCHANGED");
        helper.assertTrue(settings.setOption("vent_hydrogen", "maybe") == SettingResult.INVALID, "'maybe' wasn't INVALID");
        helper.assertTrue(settings.setOption("vent_hydrogen", "TRUE") == SettingResult.INVALID, "'TRUE' wasn't INVALID");
        helper.assertTrue(settings.setOption("vent_nitrogen", "true") == SettingResult.UNSUPPORTED, "An unknown option wasn't UNSUPPORTED");
        helper.assertTrue(electrolyzer.isVentingHydrogen(), "A refused value changed it");
        helper.assertTrue(settings.setOption("vent_hydrogen", "false") == SettingResult.APPLIED && !electrolyzer.isVentingHydrogen(), "Venting wasn't turned off");

        VacuumCollectorBlockEntity vacuum = helper.getBlockEntity(vacuumPos, VacuumCollectorBlockEntity.class);
        MachineSettings vacuumSettings = require(helper, vacuumPos, "Vacuum Collector").settings();
        var range = vacuumSettings.options().get(0);
        helper.assertTrue(range.id().equals("range") && range.min() == 1 && range.max() == 16 && range.value().equals(Integer.toString(vacuum.getRange())),
                "Range option " + range);
        int target = vacuum.getRange() == 1 ? 2 : 1;
        helper.assertTrue(vacuumSettings.setOption("range", Integer.toString(target)) == SettingResult.APPLIED && vacuum.getRange() == target,
                "Range " + target + " wasn't applied: " + vacuum.getRange());
        for (String bad : new String[] { "0", "17", "-3", "two", "" }) {
            helper.assertTrue(vacuumSettings.setOption("range", bad) == SettingResult.INVALID, "Range '" + bad + "' wasn't INVALID");
        }
        if (VacuumCollectorBlockEntity.maxRange() < 16) {
            helper.assertTrue(vacuumSettings.setOption("range", Integer.toString(VacuumCollectorBlockEntity.maxRange() + 1)) == SettingResult.REJECTED,
                    "A range past the configured maximum wasn't REJECTED");
        }
        helper.assertTrue(vacuum.getRange() == target, "A refused range changed it to " + vacuum.getRange());
        helper.succeed();
    }

    // A formed array's ports: an outer face of one of its blocks takes a port in a mode it allows; a face into the
    // structure, a block outside it or a mode it doesn't allow are refused. It has no side configuration.
    static void arrayPorts(GameTestHelper helper) {
        MultiblockTestHelpers.buildCube(helper, new BlockPos(0, 1, 0), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    MachineSettings settings = require(helper, new BlockPos(1, 2, 1), "Arc Crushing Array").settings();
                    BlockPos top = helper.absolutePos(new BlockPos(1, 3, 1));
                    helper.assertTrue(settings.ports().isEmpty(), "A new array has ports " + settings.ports());
                    helper.assertTrue(settings.allowedPortModes().contains(SideModes.INPUT) && !settings.allowedPortModes().contains(SideModes.HEAT),
                            "Port modes " + settings.allowedPortModes());
                    helper.assertTrue(settings.setPort(top, Direction.UP, SideModes.INPUT) == SettingResult.APPLIED, "The top port wasn't applied");
                    helper.assertTrue(settings.ports().contains(new MachinePort(top, Direction.UP, SideModes.INPUT)), "Ports " + settings.ports());
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, top, Direction.UP) != null, "The port doesn't take items");
                    helper.assertTrue(settings.setPort(top, Direction.UP, SideModes.INPUT) == SettingResult.UNCHANGED, "The same port wasn't UNCHANGED");
                    helper.assertTrue(settings.setPort(top, Direction.DOWN, SideModes.INPUT) == SettingResult.REJECTED, "A face into the array took a port");
                    helper.assertTrue(settings.setPort(top, Direction.UP, SideModes.HEAT) == SettingResult.INVALID, "A heat port wasn't INVALID");
                    helper.assertTrue(settings.setPort(top, Direction.UP, "sideways") == SettingResult.INVALID, "An unknown port mode wasn't INVALID");
                    helper.assertTrue(settings.setPort(top.above(), Direction.UP, SideModes.INPUT) == SettingResult.REJECTED, "A block outside the array took a port");
                    helper.assertTrue(settings.ports().size() == 1, "Ports " + settings.ports());
                    helper.assertTrue(settings.setPort(top, Direction.UP, SideModes.NONE) == SettingResult.APPLIED && settings.ports().isEmpty(), "Clearing the port failed");
                    helper.assertTrue(!settings.hasSideConfiguration() && settings.setSideMode(MachineSide.TOP, SideModes.INPUT) == SettingResult.UNSUPPORTED,
                            "An array has side configuration");
                    helper.assertTrue(settings.clearSideModes() == SettingResult.UNSUPPORTED, "An array's sides cleared");
                })
                .thenSucceed();
    }

    // Switched off, a machine stops where it is (its own status is Switched off remotely) and reports DISABLED;
    // switched on, it carries on.
    static void enableSwitch(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.ARC_CRUSHER.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(pos, ArcCrusherBlockEntity.class);
        CrushingGameTests.charge(crusher.getEnergy(), 100_000);
        crusher.getItems().setStack(ArcCrusherBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON));
        MachineControl control = require(helper, pos, "Arc Crusher");
        int[] progress = new int[1];
        long[] energy = new long[1];
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    progress[0] = crusher.getLane().getProgress();
                    helper.assertTrue(progress[0] > 0 && control.status() == MachineStatus.RUNNING, "Not running: " + control.status());
                    helper.assertTrue(control.settings().setEnabled(false) == SettingResult.APPLIED, "Switching off wasn't applied");
                    helper.assertTrue(control.settings().setEnabled(false) == SettingResult.UNCHANGED, "Switching off again wasn't UNCHANGED");
                    helper.assertTrue(!control.settings().isEnabled() && !crusher.isControlEnabled(), "Still enabled");
                    progress[0] = crusher.getLane().getProgress();
                    energy[0] = crusher.getEnergy().getAmountAsLong();
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(crusher.getLane().getProgress() == progress[0], "Progress went " + progress[0] + " -> " + crusher.getLane().getProgress() + " while off");
                    helper.assertTrue(crusher.getEnergy().getAmountAsLong() == energy[0], "FE used while off");
                    helper.assertTrue(control.status() == MachineStatus.DISABLED, "Switched off reports " + control.status());
                    helper.assertTrue(crusher.getStatus() == net.zagdrath.arcforge.machine.MachineStatus.SWITCHED_OFF, "The crusher's own status is " + crusher.getStatus());
                    helper.assertTrue(control.statusReason().getString().equals(net.zagdrath.arcforge.machine.MachineStatus.SWITCHED_OFF.getDescription().getString()),
                            "Reason " + control.statusReason().getString());
                    helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_INPUT).is(Items.RAW_IRON), "Switching off lost the input");
                    helper.assertTrue(control.settings().setEnabled(true) == SettingResult.APPLIED, "Switching on wasn't applied");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(crusher.getLane().getProgress() > progress[0], "Didn't carry on once switched on");
                    helper.assertTrue(control.status() == MachineStatus.RUNNING, "Switched on reports " + control.status());
                })
                .thenSucceed();
    }

    // --- Events ---

    // Listeners hear status changes (checked each tick) and each finished operation with what it made; a removed one
    // hears nothing more; one that throws doesn't stop the machine or the others.
    static void listeners(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        ArcCrusherBlockEntity crusher = fastCrusher(helper, pos);
        MachineControl control = require(helper, pos, "Arc Crusher");
        int[] thrown = new int[1];
        MachineListener throwing = new MachineListener() {
            @Override
            public void onStatusChanged(MachineControl machine, MachineStatus previous, MachineStatus current) {
                thrown[0]++;
                throw new IllegalStateException("A test listener throwing on purpose");
            }

            @Override
            public void onOperationCompleted(MachineControl machine, CompletedOperation operation) {
                thrown[0]++;
                throw new IllegalStateException("A test listener throwing on purpose");
            }
        };
        Recorder recorder = new Recorder();
        Recorder removed = new Recorder();
        // The thrower first, so the others are told after it throws.
        control.addListener(throwing);
        control.addListener(recorder);
        control.addListener(removed);
        control.addListener(recorder);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(control.status() == MachineStatus.IDLE, "Empty crusher reports " + control.status());
                    helper.assertTrue(recorder.statuses.isEmpty(), "Status events while idle: " + recorder.statuses);
                    control.removeListener(removed);
                    helper.assertTrue(control.items().orElseThrow().insert(new ItemStack(Items.RAW_IRON), false).isEmpty(), "The crusher refused raw iron");
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(recorder.statuses.contains("IDLE->RUNNING") && recorder.statuses.contains("RUNNING->IDLE"),
                            "Status events " + recorder.statuses);
                    helper.assertTrue(recorder.statuses.stream().filter("IDLE->RUNNING"::equals).count() == 1, "A listener added twice was told twice: " + recorder.statuses);
                    helper.assertTrue(recorder.operations.size() == 1, recorder.operations.size() + " operations heard");
                    CompletedOperation operation = recorder.operations.get(0);
                    int made = operation.itemsProduced().stream().mapToInt(ItemStack::getCount).sum();
                    helper.assertTrue(operation.itemsProduced().stream().allMatch(stack -> stack.is(ModItems.IRON_DUST.get())) && made >= 1 && made == produced(crusher),
                            "Operation made " + operation.itemsProduced() + ", the crusher holds " + produced(crusher));
                    helper.assertTrue(operation.itemsConsumed() == 1 && operation.fluidConsumed() == 0 && operation.fluidProduced().isEmpty(), "Operation " + operation);
                    helper.assertTrue(removed.statuses.isEmpty() && removed.operations.isEmpty(), "A removed listener heard " + removed.statuses + " " + removed.operations);
                    helper.assertTrue(thrown[0] >= 3, "The throwing listener was told " + thrown[0] + " times");
                    helper.assertTrue(control.statistics().operationsCompleted() == 1, "Operations " + control.statistics().operationsCompleted());
                    // The machine kept working after a listener threw.
                    control.removeListener(recorder);
                    control.removeListener(throwing);
                    control.items().orElseThrow().insert(new ItemStack(Items.RAW_IRON), false);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(control.statistics().operationsCompleted() == 2, "Second operation not done: " + control.statistics().operationsCompleted());
                    helper.assertTrue(recorder.operations.size() == 1 && recorder.statuses.size() == 2, "A removed listener heard more: " + recorder.statuses);
                })
                .thenSucceed();
    }

    // --- Statistics ---

    // After three operations the totals are right and it has run for a while; they and the switch are saved with it.
    static void statistics(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0), copyPos = new BlockPos(2, 1, 0);
        ArcCrusherBlockEntity crusher = fastCrusher(helper, pos);
        MachineControl control = require(helper, pos, "Arc Crusher");
        helper.assertTrue(control.items().orElseThrow().insert(ArcCrusherBlockEntity.SLOT_INPUT, new ItemStack(Items.RAW_IRON, 3), false).isEmpty(),
                "The crusher refused raw iron");
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(control.statistics().uptimeTicks() > 0, "No uptime while running"))
                .thenIdle(40)
                .thenExecute(() -> {
                    MachineStatistics statistics = control.statistics();
                    helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_INPUT).isEmpty(), "Not all crushed");
                    helper.assertTrue(statistics.operationsCompleted() == 3, "Operations " + statistics.operationsCompleted());
                    helper.assertTrue(statistics.itemsConsumed() == 3, "Items consumed " + statistics.itemsConsumed());
                    helper.assertTrue(statistics.itemsProduced() == produced(crusher), "Items produced " + statistics.itemsProduced() + ", it holds " + produced(crusher));
                    helper.assertTrue(statistics.fluidProduced() == 0 && statistics.fluidConsumed() == 0, "Fluid counted");
                    // Three 13-tick operations, give or take the ticks around them.
                    helper.assertTrue(statistics.uptimeTicks() >= 36 && statistics.uptimeTicks() <= 42, "Uptime " + statistics.uptimeTicks());
                    helper.assertTrue(statistics.loadedTicks() >= 50 && statistics.loadedTicks() > statistics.uptimeTicks(), "Loaded " + statistics.loadedTicks());
                    helper.assertTrue(statistics.operationsPerMinute() > 0, "Rate " + statistics.operationsPerMinute());
                    helper.assertTrue(control.settings().setEnabled(false) == SettingResult.APPLIED, "Switching off wasn't applied");

                    // Saved and loaded into a new crusher.
                    var registries = helper.getLevel().registryAccess();
                    CompoundTag saved = crusher.saveCustomOnly(registries);
                    helper.setBlock(copyPos, ModBlocks.ARC_CRUSHER.get());
                    ArcCrusherBlockEntity copy = helper.getBlockEntity(copyPos, ArcCrusherBlockEntity.class);
                    MachineControl fresh = require(helper, copyPos, "New Arc Crusher");
                    helper.assertTrue(fresh.statistics().operationsCompleted() == 0 && fresh.settings().isEnabled(), "A new crusher has statistics or is off");
                    copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
                    MachineControl loaded = require(helper, copyPos, "Loaded Arc Crusher");
                    MachineStatistics totals = loaded.statistics();
                    helper.assertTrue(totals.operationsCompleted() == 3 && totals.itemsConsumed() == 3 && totals.itemsProduced() == statistics.itemsProduced(),
                            "Loaded totals " + totals.operationsCompleted() + " ops, " + totals.itemsConsumed() + " in, " + totals.itemsProduced() + " out");
                    helper.assertTrue(totals.uptimeTicks() == statistics.uptimeTicks(), "Loaded uptime " + totals.uptimeTicks() + ", saved " + statistics.uptimeTicks());
                    helper.assertTrue(!loaded.settings().isEnabled() && loaded.status() == MachineStatus.DISABLED, "The switch wasn't saved");
                })
                .thenSucceed();
    }

    // Every block of the Arc Quarry's 3x3x3 footprint resolves to the quarry.
    static void quarryFootprint(GameTestHelper helper) {
        BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(4, 6, 4)).forEach(pos -> helper.setBlock(pos.immutable(), Blocks.AIR));
        BlockPos base = new BlockPos(2, 3, 2);
        BlockPos against = helper.absolutePos(base);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.ARC_QUARRY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(against).add(0, 0.5, 0), Direction.UP, against, false));
        helper.assertTrue(ModItems.ARC_QUARRY.get().place(context).consumesAction(), "The quarry wasn't placed");
        MachineControl quarry = require(helper, base.above(), "Arc Quarry");
        helper.assertTrue(quarry.machineType().equals(Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_quarry")) && !quarry.isMultiblock(),
                "Quarry is " + quarry.machineType());
        int footprint = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(4, 6, 4))) {
            Block block = helper.getBlockState(pos.immutable()).getBlock();
            if (block == ModBlocks.ARC_QUARRY.get() || block == ModBlocks.ARC_QUARRY_BOUNDING.get()) {
                footprint++;
                helper.assertTrue(control(helper, pos.immutable()) == quarry, "Quarry block at " + pos + " gives another machine control");
            }
        }
        helper.assertTrue(footprint == 27, "The quarry's footprint is " + footprint + " blocks");
        helper.succeed();
    }

    // A lane array through the API: inputs go in by slot, it reports its busiest lane and its FE, it stops while switched
    // off (from any of its blocks), and each lane's operation is counted with what it made.
    static void arrayOperations(GameTestHelper helper) {
        MultiblockTestHelpers.buildCube(helper, new BlockPos(0, 1, 0), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        BlockPos centre = new BlockPos(1, 2, 1);
        int[] progress = new int[3];
        MachineControl[] control = new MachineControl[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(centre, ArcCrushingArrayBlockEntity.class);
                    CrushingGameTests.charge(array.getEnergy(), 100_000);
                    // Any block of it will do.
                    control[0] = require(helper, new BlockPos(0, 1, 0), "Arc Crushing Array casing");
                    MachineItems items = control[0].items().orElseThrow();
                    for (int lane = 0; lane < 3; lane++) {
                        helper.assertTrue(items.role(ArcCrushingArrayBlockEntity.inputSlot(lane)) == SlotRole.INPUT, "Lane " + lane + "'s input isn't INPUT");
                        helper.assertTrue(items.role(ArcCrushingArrayBlockEntity.outputSlot(lane)) == SlotRole.OUTPUT, "Lane " + lane + "'s output isn't OUTPUT");
                        helper.assertTrue(items.insert(ArcCrushingArrayBlockEntity.inputSlot(lane), new ItemStack(Items.RAW_IRON), false).isEmpty(),
                                "Lane " + lane + " refused raw iron");
                        helper.assertTrue(items.insert(ArcCrushingArrayBlockEntity.outputSlot(lane), new ItemStack(Items.RAW_IRON), false).getCount() == 1,
                                "Lane " + lane + "'s output took raw iron");
                    }
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(centre, ArcCrushingArrayBlockEntity.class);
                    helper.assertTrue(control[0].status() == MachineStatus.RUNNING, "The array reports " + control[0].status() + " (" + array.getStatus() + ")");
                    double busiest = 0;
                    for (int lane = 0; lane < 3; lane++) {
                        busiest = Math.max(busiest, (double) array.getLane(lane).getProgress() / array.getLane(lane).getTotal());
                    }
                    helper.assertTrue(control[0].progress().isPresent() && Math.abs(control[0].progress().getAsDouble() - busiest) < 1e-9,
                            "Progress " + control[0].progress() + ", busiest lane " + busiest);
                    MachineEnergy energy = control[0].energy().orElseThrow();
                    helper.assertTrue(energy.perTick() > 0 && energy.perTick() == array.getUsage() && energy.stored() == array.getEnergy().getAmountAsLong(),
                            "Array FE/t " + energy.perTick() + " (its own " + array.getUsage() + "), stored " + energy.stored());
                    helper.assertTrue(control[0].settings().setEnabled(false) == SettingResult.APPLIED, "Switching off wasn't applied");
                    for (int lane = 0; lane < 3; lane++) {
                        progress[lane] = array.getLane(lane).getProgress();
                    }
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(centre, ArcCrushingArrayBlockEntity.class);
                    for (int lane = 0; lane < 3; lane++) {
                        helper.assertTrue(array.getLane(lane).getProgress() == progress[lane], "Lane " + lane + " went on while switched off");
                    }
                    helper.assertTrue(control[0].status() == MachineStatus.DISABLED && array.getStatus() == net.zagdrath.arcforge.machine.MachineStatus.SWITCHED_OFF,
                            "Switched off reports " + control[0].status() + " (" + array.getStatus() + ")");
                    helper.assertTrue(require(helper, new BlockPos(2, 3, 2), "Arc Crushing Array corner").settings().setEnabled(true) == SettingResult.APPLIED,
                            "Switching on wasn't applied");
                })
                .thenIdle(110)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(centre, ArcCrushingArrayBlockEntity.class);
                    int made = 0;
                    for (int lane = 0; lane < 3; lane++) {
                        helper.assertTrue(array.getItems().getStack(ArcCrushingArrayBlockEntity.inputSlot(lane)).isEmpty(), "Lane " + lane + " didn't finish");
                        made += array.getItems().getStack(ArcCrushingArrayBlockEntity.outputSlot(lane)).getCount()
                                + array.getItems().getStack(ArcCrushingArrayBlockEntity.bonusSlot(lane)).getCount();
                    }
                    MachineStatistics statistics = control[0].statistics();
                    helper.assertTrue(statistics.operationsCompleted() == 3 && statistics.itemsConsumed() == 3, "Array: " + statistics.operationsCompleted()
                            + " operations, " + statistics.itemsConsumed() + " consumed");
                    helper.assertTrue(statistics.itemsProduced() == made && made >= 6, "Array produced " + statistics.itemsProduced() + ", it holds " + made);
                    helper.assertTrue(control[0].status() == MachineStatus.IDLE, "Finished array reports " + control[0].status());
                })
                .thenSucceed();
    }

    // A cube array grown into a bigger box keeps its switch (and totals): the box's corner takes over from the old centre.
    static void boxTakesOver(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        MultiblockTestHelpers.buildCube(helper, min, ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    MachineControl cube = require(helper, new BlockPos(1, 2, 1), "Superheater cube");
                    helper.assertTrue(cube.position().equals(helper.absolutePos(new BlockPos(1, 2, 1))), "The cube's machine isn't its centre");
                    helper.assertTrue(cube.settings().setEnabled(false) == SettingResult.APPLIED, "Switching off wasn't applied");
                    for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(4, 2, 3))) {
                        if (helper.getBlockState(pos.immutable()).isAir()) {
                            helper.setBlock(pos.immutable(), ModBlocks.SUPERHEATER_ARRAY_CASING.get());
                        }
                    }
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    MachineControl box = checkFormed(helper, min, "Superheater box");
                    helper.assertTrue(!box.settings().isEnabled() && box.status() == MachineStatus.DISABLED, "The box didn't keep the switch: " + box.status());
                    helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 2, 1), MachineBlockEntity.class).isControlEnabled(),
                            "The old centre kept the switch");
                    helper.assertTrue(box.settings().setEnabled(true) == SettingResult.APPLIED, "Switching the box on wasn't applied");
                })
                .thenSucceed();
    }

    // --- Role rules for every machine ---

    // Items offered to every machine: ingredients, fuels, buckets, dies, upgrades and things no machine uses.
    private static List<ItemStack> candidateItems() {
        List<ItemStack> stacks = new ArrayList<>();
        for (net.minecraft.world.item.Item item : List.of(Items.RAW_IRON, Items.IRON_ORE, Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK, Items.WATER_BUCKET,
                Items.LAVA_BUCKET, Items.BUCKET, Items.WHEAT, Items.WHEAT_SEEDS, Items.OAK_SAPLING, Items.OAK_LOG, Items.OAK_PLANKS, Items.BONE_MEAL,
                Items.COBBLESTONE, Items.STONE, Items.GRAVEL, Items.SAND, Items.DIRT, Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.STICK,
                Items.KELP, Items.SUGAR_CANE, Items.GUNPOWDER, Items.NETHERRACK, Items.DIAMOND, Items.SLIME_BALL, Items.POTATO, Items.GLASS_BOTTLE,
                Items.TOTEM_OF_UNDYING, ModItems.SPEED_UPGRADE.get(), ModItems.ENERGY_UPGRADE.get(), ModItems.HEAT_UPGRADE.get(), ModItems.IRON_DUST.get(),
                ModItems.CARBON_DUST.get(), ModItems.PLATE_DIE.get(), ModItems.GEAR_DIE.get(), ModItems.PINE_RESIN.get(), ModItems.DRIED_HOPS.get(),
                ModItems.NPK_FERTILIZER.get(), ModItems.COMPOST.get(), ModItems.CONDUIT_FILTER.get(), ModItems.COAL_COKE.get(), ModItems.SALT.get())) {
            stacks.add(new ItemStack(item, item.getDefaultMaxStackSize() > 1 ? 4 : 1));
        }
        return stacks;
    }

    // Every source fluid: water, lava and Arcforge's liquids and gases.
    private static List<FluidStack> candidateFluids() {
        List<FluidStack> fluids = new ArrayList<>();
        for (net.minecraft.world.level.material.Fluid fluid : BuiltInRegistries.FLUID) {
            String namespace = BuiltInRegistries.FLUID.getKey(fluid).getNamespace();
            if ((namespace.equals(Arcforge.MODID) || fluid == Fluids.WATER || fluid == Fluids.LAVA) && fluid.isSource(fluid.defaultFluidState())) {
                fluids.add(new FluidStack(fluid, 1_000));
            }
        }
        return fluids;
    }

    // Offers every candidate item and fluid to the machine through the API, with and without a slot or tank, and takes
    // from every slot and tank: nothing may go into a slot or tank that isn't an input, fuel or catalyst, and nothing may
    // come out of one that isn't an output. Puts the machine's slots back as they were.
    private static void checkRoles(MachineControl control, net.zagdrath.arcforge.transfer.item.FilteredItemHandler own, String name, List<String> problems) {
        control.items().ifPresent(items -> {
            List<ItemStack> before = new ArrayList<>();
            for (int slot = 0; slot < items.slotCount(); slot++) {
                before.add(items.stack(slot));
            }
            for (ItemStack offer : candidateItems()) {
                ItemStack rest = items.insert(offer.copy(), false);
                for (int slot = 0; slot < items.slotCount(); slot++) {
                    if (!items.role(slot).insertable() && !ItemStack.matches(items.stack(slot), before.get(slot))) {
                        problems.add(name + ": a fill of " + offer + " went into " + items.role(slot) + " slot " + slot + " (" + items.stack(slot) + ")");
                        own.setStack(slot, before.get(slot).copy());
                    }
                }
                if (rest.getCount() > offer.getCount()) {
                    problems.add(name + ": a fill of " + offer + " gave back " + rest);
                }
            }
            for (int slot = 0; slot < items.slotCount(); slot++) {
                SlotRole role = items.role(slot);
                if (!role.insertable()) {
                    for (ItemStack offer : candidateItems()) {
                        if (items.insert(slot, offer.copy(), false).getCount() != offer.getCount() || !ItemStack.matches(items.stack(slot), before.get(slot))) {
                            problems.add(name + ": " + role + " slot " + slot + " took " + offer);
                            own.setStack(slot, before.get(slot).copy());
                        }
                    }
                }
                if (!role.extractable()) {
                    ItemStack held = items.stack(slot);
                    ItemStack taken = items.extract(slot, 64, false);
                    if (!taken.isEmpty()) {
                        problems.add(name + ": " + taken + " came out of " + role + " slot " + slot);
                        own.setStack(slot, held);
                    }
                }
            }
            for (int slot = 0; slot < own.size() && slot < before.size(); slot++) {
                own.setStack(slot, before.get(slot).copy());
            }
        });
        control.fluids().ifPresent(fluids -> {
            List<FluidStack> before = new ArrayList<>();
            for (int tank = 0; tank < fluids.tankCount(); tank++) {
                before.add(fluids.fluid(tank));
            }
            for (FluidStack offer : candidateFluids()) {
                fluids.insert(offer.copy(), false);
                for (int tank = 0; tank < fluids.tankCount(); tank++) {
                    if (!fluids.role(tank).insertable() && !FluidStack.matches(fluids.fluid(tank), before.get(tank))) {
                        problems.add(name + ": a fill of " + offer.getAmount() + " mB of " + BuiltInRegistries.FLUID.getKey(offer.getFluid())
                                + " went into " + fluids.role(tank) + " tank " + tank);
                        before.set(tank, fluids.fluid(tank));
                    }
                }
            }
            for (int tank = 0; tank < fluids.tankCount(); tank++) {
                SlotRole role = fluids.role(tank);
                if (!role.insertable()) {
                    for (FluidStack offer : candidateFluids()) {
                        if (fluids.insert(tank, offer.copy(), false) != 0) {
                            problems.add(name + ": " + role + " tank " + tank + " took " + BuiltInRegistries.FLUID.getKey(offer.getFluid()));
                        }
                    }
                }
                if (!role.extractable() && !fluids.extract(tank, 1_000, false).isEmpty()) {
                    problems.add(name + ": fluid came out of " + role + " tank " + tank);
                }
            }
        });
    }

    // The role rules hold for every single-block machine (found from the registry).
    static void singleBlockRoles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!Arcforge.MODID.equals(id.getNamespace()) || !(block instanceof EntityBlock entityBlock)) {
                continue;
            }
            BlockEntity probe = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            if (!(probe instanceof MachineBlockEntity) || probe instanceof MultiblockController) {
                continue;
            }
            helper.setBlock(pos, block);
            MachineControl control = control(helper, pos);
            if (control != null && level.getBlockEntity(helper.absolutePos(pos)) instanceof MachineBlockEntity machine) {
                checkRoles(control, machine.getItems(), id.toString(), problems);
                // Empty, so nothing drops when it's removed.
                for (int slot = 0; slot < machine.getItems().size(); slot++) {
                    machine.getItems().setStack(slot, ItemStack.EMPTY);
                }
            }
            helper.setBlock(pos, Blocks.AIR);
        }
        helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems));
        helper.succeed();
    }

    // The role rules hold for each formed multiblock at these controllers.
    private static void checkRoles(GameTestHelper helper, String name, BlockPos... controllers) {
        List<String> problems = new ArrayList<>();
        for (BlockPos controller : controllers) {
            MachineControl control = require(helper, controller, name + " at " + controller);
            BlockEntity blockEntity = helper.getLevel().getBlockEntity(control.position());
            net.zagdrath.arcforge.transfer.item.FilteredItemHandler own = blockEntity instanceof MachineBlockEntity machine ? machine.getItems()
                    : blockEntity instanceof net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity furnace ? furnace.getItems()
                    : blockEntity instanceof net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity carbonizer ? carbonizer.getItems() : null;
            helper.assertTrue(own != null, "No item handler for " + control.machineType());
            checkRoles(control, own, control.machineType().toString(), problems);
        }
        helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems));
    }

    // The role rules hold for the cube and shell arrays (the other multiblocks check them in their coverage tests).
    static void multiblockRoles(GameTestHelper helper) {
        MultiblockTestHelpers.buildCube(helper, new BlockPos(0, 1, 0), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, new BlockPos(4, 1, 0), ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, new BlockPos(8, 1, 0), ModBlocks.METAL_PRESSING_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, new BlockPos(0, 1, 4), ModBlocks.SUPERHEATER_ARRAY_CASING.get());
        MultiblockTestHelpers.buildCube(helper, new BlockPos(4, 1, 4), ModBlocks.CONDENSER_ARRAY_CASING.get());
        SteamGameTests.buildShell(helper, new BlockPos(8, 1, 4), Direction.Axis.Y, 4, ModBlocks.STEAM_BOILER_ARRAY_CASING.get());
        SteamGameTests.buildShell(helper, new BlockPos(0, 1, 8), Direction.Axis.X, 3, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        SteamGameTests.buildShell(helper, new BlockPos(4, 1, 8), Direction.Axis.X, 5, ModBlocks.GAS_TURBINE_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> checkRoles(helper, "Array", new BlockPos(1, 2, 1), new BlockPos(5, 2, 1), new BlockPos(9, 2, 1), new BlockPos(1, 2, 5),
                        new BlockPos(5, 2, 5), new BlockPos(8, 1, 4), new BlockPos(0, 1, 8), new BlockPos(4, 1, 8)))
                .thenSucceed();
    }

    // A fault reaches listeners as a status change to FAULT and then onFault with the machine's own reason: a Block
    // Breaker facing bedrock.
    static void faultEvent(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 2);
        // Clear in front (the test area's walls can reach it).
        helper.setBlock(pos.north(), Blocks.AIR);
        helper.setBlock(pos, ModBlocks.BLOCK_BREAKER.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                Direction.NORTH));
        BlockBreakerBlockEntity breaker = helper.getBlockEntity(pos, BlockBreakerBlockEntity.class);
        CrushingGameTests.charge(breaker.getEnergy(), 20_000);
        MachineControl control = require(helper, pos, "Block Breaker");
        Recorder recorder = new Recorder();
        control.addListener(recorder);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(control.status() == MachineStatus.IDLE, "Breaker with nothing in front reports " + control.status() + " (" + breaker.getStatus() + ")");
                    helper.setBlock(pos.north(), Blocks.BEDROCK);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(control.status() == MachineStatus.FAULT, "Breaker facing bedrock reports " + control.status() + " (" + breaker.getStatus() + ")");
                    helper.assertTrue(recorder.statuses.contains("IDLE->FAULT") || recorder.statuses.contains("RUNNING->FAULT"), "Status events " + recorder.statuses);
                    helper.assertTrue(recorder.faults.size() == 1, recorder.faults.size() + " faults heard");
                    helper.assertTrue(recorder.faults.get(0).getString().equals(net.zagdrath.arcforge.machine.MachineStatus.CANNOT_BREAK.getDescription().getString()),
                            "Fault reason '" + recorder.faults.get(0).getString() + "'");
                    helper.assertBlockPresent(Blocks.BEDROCK, pos.north());
                })
                .thenSucceed();
    }
}
