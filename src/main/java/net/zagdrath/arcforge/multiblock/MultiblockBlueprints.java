/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.zagdrath.arcforge.block.farming.greenhouse.GreenhouseControllerBlock;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.block.multiblock.BiogasDigesterControllerBlock;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.GasTurbineArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.ThermalEvaporatorControllerBlock;
import net.zagdrath.arcforge.block.multiblock.VesselControllerBlock;
import net.zagdrath.arcforge.registry.ModBlocks;

// An example build of every Arcforge multiblock, for the JEI build viewer and the Engineer's Handbook: which block
// goes where, built bottom layer first. Fronts face south (+Z), the side the viewers show first.
public final class MultiblockBlueprints {
    public record Placement(BlockPos pos, BlockState state) {}

    // id: used for lang keys (multiblock.arcforge.<id> and .rules). size: the box it fills.
    public record Blueprint(String id, List<Placement> placements, BlockPos size) {
        public Component name() {
            return Component.translatable("multiblock.arcforge." + id);
        }

        // How the structure may vary (sizes, where glass may go), one or two lines.
        public Component rules() {
            return Component.translatable("multiblock.arcforge." + id + ".rules");
        }

        public int layers() {
            return size.getY();
        }

        // The blocks needed and how many of each, in the order they first appear.
        public Map<Item, Integer> bill() {
            Map<Item, Integer> counts = new LinkedHashMap<>();
            for (Placement placement : placements) {
                counts.merge(placement.state().getBlock().asItem(), 1, Integer::sum);
            }
            return counts;
        }
    }

    private MultiblockBlueprints() {}

    public static List<Blueprint> all() {
        return List.of(
                cube("arc_crushing_array", ModBlocks.ARC_CRUSHING_ARRAY_CASING.get()),
                cube("induction_furnace_array", ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get()),
                cube("chemical_reactor_array", ModBlocks.CHEMICAL_REACTOR_ARRAY_CASING.get()),
                cube("metal_pressing_array", ModBlocks.METAL_PRESSING_ARRAY_CASING.get()),
                cube("superheater_array", ModBlocks.SUPERHEATER_ARRAY_CASING.get()),
                cube("condenser_array", ModBlocks.CONDENSER_ARRAY_CASING.get()),
                cube("water_intake", ModBlocks.WATER_INTAKE_CASING.get()),
                carbonizer(),
                arcforgeFurnace(),
                steamBoilerArray(),
                steamTurbineArray(),
                nuclearSteamTurbine(),
                gasTurbineArray(),
                distillationArray(),
                refinery(),
                oilDerrick(),
                pumpjack(),
                plant("cracking_unit", PlantKind.CRACKING_UNIT, ModBlocks.CRACKING_UNIT_CASING.get(), ModBlocks.CRACKING_UNIT_CONTROLLER.get(), 0),
                plant("steam_cracker", PlantKind.STEAM_CRACKER, ModBlocks.STEAM_CRACKER_CASING.get(), ModBlocks.STEAM_CRACKER_CONTROLLER.get(), 0),
                plant("diesel_generator", PlantKind.DIESEL_GENERATOR, ModBlocks.DIESEL_GENERATOR_CASING.get(),
                        ModBlocks.DIESEL_GENERATOR_CONTROLLER.get(), 5),
                plant("diesel_generator_v12", PlantKind.DIESEL_GENERATOR, ModBlocks.DIESEL_GENERATOR_CASING.get(),
                        ModBlocks.DIESEL_GENERATOR_CONTROLLER.get(), 7),
                plant("diesel_generator_v16", PlantKind.DIESEL_GENERATOR, ModBlocks.DIESEL_GENERATOR_CASING.get(),
                        ModBlocks.DIESEL_GENERATOR_CONTROLLER.get(), 9),
                solarThermalArray(),
                biogasDigester(),
                greenhouse(),
                thermalEvaporator(),
                fireboxArray(),
                batteryArray(),
                centrifugeCascade(),
                fissionReactor(),
                vessel("steam_generator", new BlockPos(3, 5, 3), ModBlocks.STEAM_GENERATOR_CASING.get(), ModBlocks.STEAM_GENERATOR_CONTROLLER.get(),
                        pos -> ModBlocks.STEAM_GENERATOR_TUBES.get().defaultBlockState()),
                vessel("heavy_water_tower", new BlockPos(3, 5, 3), ModBlocks.HEAVY_WATER_TOWER_CASING.get(),
                        ModBlocks.HEAVY_WATER_TOWER_CONTROLLER.get(), pos -> ModBlocks.EXCHANGE_TRAY.get().defaultBlockState()),
                purexPlant(),
                containment());
    }

    // A solid 3x3x3 cube of one casing.
    private static Blueprint cube(String id, Block casing) {
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    placements.add(new Placement(new BlockPos(x, y, z), casing.defaultBlockState()));
                }
            }
        }
        return new Blueprint(id, placements, new BlockPos(3, 3, 3));
    }

    // Two slices of a 3 tall, 3 deep Carbonizer, all facing south.
    private static Blueprint carbonizer() {
        BlockState block = ModBlocks.CARBONIZER.get().defaultBlockState().setValue(CarbonizerBlock.FACING, Direction.SOUTH);
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 2; x++) {
                    placements.add(new Placement(new BlockPos(x, y, z), block));
                }
            }
        }
        return new Blueprint("carbonizer", placements, new BlockPos(2, 3, 3));
    }

    // A 3x3 cross-shaped column, 6 tall: walls on the corners, bricks on the edges, the port at the
    // front of the bottom layer and a brick hearth behind it; the layers above are hollow.
    private static Blueprint arcforgeFurnace() {
        BlockState bricks = ModBlocks.ARCFORGE_FURNACE_BRICKS.get().defaultBlockState();
        BlockState wall = ModBlocks.ARCFORGE_FURNACE_BRICK_WALL.get().defaultBlockState();
        BlockState port = ModBlocks.ARCFORGE_FURNACE_PORT.get().defaultBlockState().setValue(ArcforgeFurnacePortBlock.FACING, Direction.SOUTH);
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < 6; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    boolean corner = x != 1 && z != 1;
                    boolean center = x == 1 && z == 1;
                    BlockState state;
                    if (corner) {
                        state = wall;
                    } else if (center) {
                        if (y > 0) {
                            continue;
                        }
                        state = bricks;
                    } else if (y == 0 && x == 1 && z == 2) {
                        state = port;
                    } else {
                        state = bricks;
                    }
                    placements.add(new Placement(new BlockPos(x, y, z), state));
                }
            }
        }
        return new Blueprint("arcforge_furnace", placements, new BlockPos(3, 6, 3));
    }

    // A hollow 3x3 tube along an axis, with Pressure Glass at the given positions (never on a corner).
    private static List<Placement> shell(BlockPos size, Direction.Axis axis, Block casing, List<BlockPos> glass) {
        List<Placement> placements = new ArrayList<>();
        int length = axis == Direction.Axis.Y ? size.getY() : axis == Direction.Axis.X ? size.getX() : size.getZ();
        for (int y = 0; y < size.getY(); y++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    int along = axis == Direction.Axis.Y ? y : axis == Direction.Axis.X ? x : z;
                    int a = axis == Direction.Axis.X ? y : x;
                    int b = axis == Direction.Axis.Z ? y : z;
                    boolean core = a == 1 && b == 1 && along > 0 && along < length - 1;
                    if (core) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    placements.add(new Placement(pos, glass.contains(pos) ? ModBlocks.PRESSURE_GLASS.get().defaultBlockState() : casing.defaultBlockState()));
                }
            }
        }
        return placements;
    }

    // 2x2, 4 tall: three layers of casings with the controller at the front of the bottom one (facing south,
    // so its axis is north-south), and four collectors on top.
    private static Blueprint solarThermalArray() {
        BlockState casing = ModBlocks.SOLAR_THERMAL_ARRAY_CASING.get().defaultBlockState();
        BlockState collector = ModBlocks.SOLAR_COLLECTOR.get().defaultBlockState();
        BlockState controller = ModBlocks.SOLAR_THERMAL_ARRAY_CONTROLLER.get().defaultBlockState()
                .setValue(SolarThermalArrayControllerBlock.FACING, Direction.SOUTH);
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < SolarThermalStructure.HEIGHT; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = 0; x < 2; x++) {
                    BlockState state = y == SolarThermalStructure.HEIGHT - 1 ? collector : y == 0 && x == 0 && z == 1 ? controller : casing;
                    placements.add(new Placement(new BlockPos(x, y, z), state));
                }
            }
        }
        return new Blueprint("solar_thermal_array", placements, new BlockPos(2, SolarThermalStructure.HEIGHT, 2));
    }

    // A solid 3x3x3 of Biogas Digester Casings with the controller in the middle of the front (facing south).
    private static Blueprint biogasDigester() {
        BlockState casing = ModBlocks.DIGESTER_CASING.get().defaultBlockState();
        BlockState controller = ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get().defaultBlockState()
                .setValue(BiogasDigesterControllerBlock.FACING, Direction.SOUTH);
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    placements.add(new Placement(new BlockPos(x, y, z), x == 1 && y == 1 && z == 2 ? controller : casing));
                }
            }
        }
        return new Blueprint("biogas_digester", placements, new BlockPos(3, 3, 3));
    }

    // The Thermal Evaporator Array: a 3x3x9 tower of casings round a hollow core, the controller in the middle of the
    // bottom layer's front, and a Pressure Glass window up the middle of the front (any side's middle may be glass or
    // casing).
    private static Blueprint thermalEvaporator() {
        int width = ThermalEvaporatorStructure.WIDTH, height = ThermalEvaporatorStructure.HEIGHT;
        BlockState casing = ModBlocks.THERMAL_EVAPORATOR_CASING.get().defaultBlockState();
        BlockState glass = ModBlocks.PRESSURE_GLASS.get().defaultBlockState();
        BlockState controller = ModBlocks.THERMAL_EVAPORATOR_CONTROLLER.get().defaultBlockState()
                .setValue(ThermalEvaporatorControllerBlock.FACING, Direction.SOUTH);
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < width; z++) {
                for (int x = 0; x < width; x++) {
                    boolean core = x == 1 && z == 1 && y > 0 && y < height - 1;
                    if (core) {
                        continue;
                    }
                    BlockState state = y == 0 && x == 1 && z == width - 1 ? controller
                            : y > 0 && y < height - 1 && x == 1 && z == width - 1 ? glass : casing;
                    placements.add(new Placement(new BlockPos(x, y, z), state));
                }
            }
        }
        return new Blueprint("thermal_evaporator", placements, new BlockPos(width, height, width));
    }

    // The smallest Greenhouse Array, 5x5 and 4 tall (up to 11x11 and 8 tall work): frame edges, glass walls and roof,
    // the controller in the middle of the front wall, a 3x3 of Planting Beds in the floor and a Grow Lamp under the
    // roof.
    private static Blueprint greenhouse() {
        BlockState frame = ModBlocks.GREENHOUSE_FRAME.get().defaultBlockState();
        BlockState glass = ModBlocks.PRESSURE_GLASS.get().defaultBlockState();
        BlockState bed = ModBlocks.PLANTING_BED.get().defaultBlockState();
        BlockState lamp = ModBlocks.GROW_LAMP.get().defaultBlockState();
        BlockState controller = ModBlocks.GREENHOUSE_CONTROLLER.get().defaultBlockState()
                .setValue(GreenhouseControllerBlock.FACING, Direction.SOUTH);
        int size = GreenhouseStructure.MIN_WIDTH, height = GreenhouseStructure.MIN_HEIGHT;
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int boundaries = (x == 0 || x == size - 1 ? 1 : 0) + (y == 0 || y == height - 1 ? 1 : 0) + (z == 0 || z == size - 1 ? 1 : 0);
                    BlockState state;
                    if (boundaries >= 2) {
                        state = frame;
                    } else if (boundaries == 1) {
                        state = y == 0 ? bed : x == size / 2 && y == 1 && z == size - 1 ? controller : glass;
                    } else {
                        state = x == size / 2 && z == size / 2 && y == height - 2 ? lamp : null;
                    }
                    if (state != null) {
                        placements.add(new Placement(new BlockPos(x, y, z), state));
                    }
                }
            }
        }
        return new Blueprint("greenhouse", placements, new BlockPos(size, height, size));
    }

    // 2x2, 8 tall (4 and 6 work too): casings top and bottom, the controller at the front of the second
    // layer, tray level casings everywhere else.
    private static Blueprint distillationArray() {
        BlockState casing = ModBlocks.DISTILLATION_ARRAY_CASING.get().defaultBlockState();
        BlockState tray = ModBlocks.TRAY_LEVEL_CASING.get().defaultBlockState();
        BlockState controller = ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get().defaultBlockState()
                .setValue(DistillationArrayControllerBlock.FACING, Direction.SOUTH);
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < 8; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = 0; x < 2; x++) {
                    BlockState state = y == 0 || y == 7 ? casing : y == 1 && x == 0 && z == 1 ? controller : tray;
                    placements.add(new Placement(new BlockPos(x, y, z), state));
                }
            }
        }
        return new Blueprint("distillation_array", placements, new BlockPos(2, 8, 2));
    }

    // A crude oil refinery: 3x3 and 10 tall (any height from 4 to 16 works, 2x2 too), all casings, the controller at
    // the front of the second layer.
    private static Blueprint refinery() {
        BlockState casing = ModBlocks.DISTILLATION_ARRAY_CASING.get().defaultBlockState();
        BlockState controller = ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get().defaultBlockState()
                .setValue(DistillationArrayControllerBlock.FACING, Direction.SOUTH);
        int height = 10;
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    placements.add(new Placement(new BlockPos(x, y, z), y == 1 && x == 1 && z == 2 ? controller : casing));
                }
            }
        }
        return new Blueprint("distillation_array_3x3", placements, new BlockPos(3, height, 3));
    }

    // The Oil Derrick: a floor of Derrick Casings round the open bore, the controller in the middle of the front
    // (facing south), Derrick Frame legs on the corners and a 3x3 crown of them.
    private static Blueprint oilDerrick() {
        BlockState casing = ModBlocks.DERRICK_CASING.get().defaultBlockState();
        BlockState frame = ModBlocks.DERRICK_FRAME.get().defaultBlockState();
        BlockState controller = ModBlocks.OIL_DERRICK_CONTROLLER.get().defaultBlockState()
                .setValue(net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock.FACING, Direction.SOUTH);
        OilfieldStructures.Rig rig = new OilfieldStructures.Rig(BlockPos.ZERO, Direction.SOUTH);
        List<Placement> placements = new ArrayList<>();
        for (BlockPos pos : rig.parts()) {
            placements.add(new Placement(pos, pos.equals(rig.controller()) ? controller : rig.isFrame(pos) ? frame : casing));
        }
        return new Blueprint("oil_derrick", placements, new BlockPos(OilfieldStructures.RIG_WIDTH, OilfieldStructures.RIG_HEIGHT, OilfieldStructures.RIG_WIDTH));
    }

    // The Pumpjack round its Well Head (at the north end), the controller at the south end facing south.
    private static Blueprint pumpjack() {
        BlockState casing = ModBlocks.PUMPJACK_CASING.get().defaultBlockState();
        BlockState controller = ModBlocks.PUMPJACK_CONTROLLER.get().defaultBlockState()
                .setValue(net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock.FACING, Direction.SOUTH);
        OilfieldStructures.Unit unit = new OilfieldStructures.Unit(new BlockPos(1, 0, 0), Direction.SOUTH);
        List<Placement> placements = new ArrayList<>();
        placements.add(new Placement(unit.well(), ModBlocks.WELL_HEAD.get().defaultBlockState()));
        for (BlockPos pos : unit.parts()) {
            placements.add(new Placement(pos, pos.equals(unit.controller()) ? controller : casing));
        }
        return new Blueprint("pumpjack", placements,
                new BlockPos(OilfieldStructures.UNIT_WIDTH, OilfieldStructures.UNIT_HEIGHT, OilfieldStructures.UNIT_LENGTH));
    }

    // A box plant (the Cracking Unit, Steam Cracker or Diesel Generator, this long): casings, the controller in the
    // middle of the bottom row at the south end, facing south.
    private static Blueprint plant(String id, PlantKind kind, Block casing, Block controller, int length) {
        BlockState controllerState = controller.defaultBlockState()
                .setValue(net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock.FACING, Direction.SOUTH);
        OilfieldStructures.PlantBox probe = OilfieldStructures.PlantBox.of(kind, BlockPos.ZERO, Direction.NORTH, length);
        OilfieldStructures.PlantBox box = OilfieldStructures.PlantBox.of(kind, new BlockPos(probe.width() / 2, 0, probe.length() - 1), Direction.NORTH,
                length);
        List<Placement> placements = new ArrayList<>();
        for (BlockPos pos : box.parts()) {
            placements.add(new Placement(pos, pos.equals(box.controller()) ? controllerState : casing.defaultBlockState()));
        }
        return new Blueprint(id, placements, new BlockPos(box.width(), box.height(), box.length()));
    }

    // 3x3 (up to 7x7 work), 4 tall (3 to 16 work), with a window up the front.
    private static Blueprint steamBoilerArray() {
        BlockPos size = new BlockPos(4, 4, 5);
        List<BlockPos> glass = List.of(new BlockPos(1, 1, 4), new BlockPos(2, 1, 4), new BlockPos(1, 2, 4), new BlockPos(2, 2, 4));
        List<Placement> placements = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, size.offset(-1, -1, -1))) {
            BlockPos at = pos.immutable();
            if (boundaries(at, size) > 0) {
                placements.add(new Placement(at, glass.contains(at) ? ModBlocks.PRESSURE_GLASS.get().defaultBlockState()
                        : ModBlocks.STEAM_BOILER_ARRAY_CASING.get().defaultBlockState()));
            }
        }
        return new Blueprint("steam_boiler_array", sorted(placements), size);
    }

    // On how many axes pos lies on the boundary of a box of this size from the origin.
    private static int boundaries(BlockPos pos, BlockPos size) {
        return (pos.getX() == 0 || pos.getX() == size.getX() - 1 ? 1 : 0) + (pos.getY() == 0 || pos.getY() == size.getY() - 1 ? 1 : 0)
                + (pos.getZ() == 0 || pos.getZ() == size.getZ() - 1 ? 1 : 0);
    }

    // Bottom layer first, then by row, as the other blueprints are built.
    private static List<Placement> sorted(List<Placement> placements) {
        List<Placement> sorted = new ArrayList<>(placements);
        sorted.sort(java.util.Comparator.<Placement>comparingInt(p -> p.pos().getY()).thenComparingInt(p -> p.pos().getZ())
                .thenComparingInt(p -> p.pos().getX()));
        return sorted;
    }

    // A Firebox Array 4 wide, 3 tall and 5 deep (3 to 7 by 3 to 7 across and 3 to 7 tall work): casings on the edges,
    // the controller in the middle of the front wall facing south, and a window in the east wall.
    private static Blueprint fireboxArray() {
        BlockPos size = new BlockPos(4, 3, 5);
        BlockState casing = ModBlocks.FIREBOX_ARRAY_CASING.get().defaultBlockState();
        BlockState glass = ModBlocks.PRESSURE_GLASS.get().defaultBlockState();
        BlockState controller = ModBlocks.FIREBOX_ARRAY_CONTROLLER.get().defaultBlockState()
                .setValue(net.zagdrath.arcforge.block.multiblock.FireboxArrayControllerBlock.FACING, Direction.SOUTH);
        List<Placement> placements = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, size.offset(-1, -1, -1))) {
            BlockPos at = pos.immutable();
            int boundaries = boundaries(at, size);
            if (boundaries == 0) {
                continue;
            }
            BlockState state = at.equals(new BlockPos(2, 1, 4)) ? controller
                    : boundaries == 1 && at.getX() == 3 && at.getY() == 1 ? glass : casing;
            placements.add(new Placement(at, state));
        }
        return new Blueprint("firebox_array", sorted(placements), size);
    }

    // A Battery Array 4 wide, 3 tall and 4 deep (3 to 5 each way work): casings on the edges, the controller in the
    // front face facing south, a window in the east face, and inside three Wrought Lithium Cells and a Wrought Power
    // Regulator (any mix works).
    private static Blueprint batteryArray() {
        BlockPos size = new BlockPos(4, 3, 4);
        BlockState casing = ModBlocks.BATTERY_ARRAY_CASING.get().defaultBlockState();
        BlockState glass = ModBlocks.PRESSURE_GLASS.get().defaultBlockState();
        BlockState controller = ModBlocks.BATTERY_ARRAY_CONTROLLER.get().defaultBlockState()
                .setValue(net.zagdrath.arcforge.block.multiblock.BatteryArrayControllerBlock.FACING, Direction.SOUTH);
        BlockState cell = ModBlocks.lithiumCell(net.zagdrath.arcforge.conduit.ConduitTier.WROUGHT).get().defaultBlockState();
        BlockState regulator = ModBlocks.powerRegulator(net.zagdrath.arcforge.conduit.ConduitTier.WROUGHT).get().defaultBlockState();
        List<Placement> placements = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, size.offset(-1, -1, -1))) {
            BlockPos at = pos.immutable();
            int boundaries = boundaries(at, size);
            BlockState state;
            if (boundaries == 0) {
                state = at.equals(new BlockPos(2, 1, 2)) ? regulator : cell;
            } else if (at.equals(new BlockPos(1, 1, 3))) {
                state = controller;
            } else {
                state = boundaries == 1 && at.getX() == 3 ? glass : casing;
            }
            placements.add(new Placement(at, state));
        }
        return new Blueprint("battery_array", sorted(placements), size);
    }

    // The smallest hall that reads as one: a 5x4 floor (3x3 to 7x7 work), the controller in the front edge, and two
    // rows of centrifuges behind it with an aisle between.
    private static Blueprint centrifugeCascade() {
        BlockPos size = new BlockPos(5, 3, 4);
        BlockState casing = ModBlocks.CENTRIFUGE_CASCADE_CASING.get().defaultBlockState();
        BlockState controller = ModBlocks.CENTRIFUGE_CASCADE_CONTROLLER.get().defaultBlockState()
                .setValue(net.zagdrath.arcforge.block.multiblock.CentrifugeCascadeControllerBlock.FACING, Direction.SOUTH);
        BlockState lower = ModBlocks.GAS_CENTRIFUGE.get().defaultBlockState();
        BlockState upper = lower.setValue(net.zagdrath.arcforge.block.multiblock.GasCentrifugeBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        List<Placement> placements = new ArrayList<>();
        for (int z = 0; z < size.getZ(); z++) {
            for (int x = 0; x < size.getX(); x++) {
                BlockPos at = new BlockPos(x, 0, z);
                placements.add(new Placement(at, at.equals(new BlockPos(2, 0, 3)) ? controller : casing));
                // Rows at z 0 and 2 (z 1 the aisle), clear of the controller's panel at the front.
                if (z == 0 || z == 2) {
                    placements.add(new Placement(new BlockPos(x, 1, z), lower));
                    placements.add(new Placement(new BlockPos(x, 2, z), upper));
                }
            }
        }
        return new Blueprint("centrifuge_cascade", sorted(placements), size);
    }

    // 3x3 (up to 7x7 work), 5 long along X (3 to 27 work), with a window along the front.
    private static Blueprint steamTurbineArray() {
        BlockPos size = new BlockPos(5, 3, 3);
        List<BlockPos> glass = List.of(new BlockPos(1, 1, 2), new BlockPos(2, 1, 2), new BlockPos(3, 1, 2));
        return new Blueprint("steam_turbine_array", shell(size, Direction.Axis.X, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get(), glass), size);
    }

    // The shortest Nuclear Steam Turbine: 7x7, 21 long along X (up to 45 works), every block of its outside a casing.
    private static Blueprint nuclearSteamTurbine() {
        BlockPos size = new BlockPos(net.zagdrath.arcforge.steam.NuclearTurbineLayout.MIN_LENGTH, 7, 7);
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < size.getY(); y++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    if (x == 0 || x == size.getX() - 1 || y == 0 || y == size.getY() - 1 || z == 0 || z == size.getZ() - 1) {
                        placements.add(new Placement(new BlockPos(x, y, z), ModBlocks.NUCLEAR_TURBINE_CASING.get().defaultBlockState()));
                    }
                }
            }
        }
        return new Blueprint("nuclear_steam_turbine", placements, size);
    }

    // 3x3, 5 long along X (5 to 9 work), with windows along the front and top, the intake at the west end and
    // the exhaust at the east.
    private static Blueprint gasTurbineArray() {
        BlockPos size = new BlockPos(5, 3, 3);
        List<BlockPos> glass = List.of(new BlockPos(1, 1, 2), new BlockPos(2, 1, 2), new BlockPos(3, 1, 2),
                new BlockPos(1, 2, 1), new BlockPos(2, 2, 1), new BlockPos(3, 2, 1));
        List<Placement> placements = new ArrayList<>();
        for (Placement placement : shell(size, Direction.Axis.X, ModBlocks.GAS_TURBINE_ARRAY_CASING.get(), glass)) {
            GasTurbineArrayCasingBlock.End end = placement.pos().equals(new BlockPos(0, 1, 1)) ? GasTurbineArrayCasingBlock.End.INTAKE
                    : placement.pos().equals(new BlockPos(4, 1, 1)) ? GasTurbineArrayCasingBlock.End.EXHAUST : null;
            placements.add(end == null ? placement : new Placement(placement.pos(), placement.state().setValue(GasTurbineArrayCasingBlock.END, end)));
        }
        return new Blueprint("gas_turbine_array", placements, size);
    }

    // A walled vessel (VesselStructure): casing walls round its inside, the controller in the middle of the front's
    // lowest wall row above the floor, facing south (the front), and inside(pos) in every inside block.
    private static Blueprint vessel(String id, BlockPos size, Block casing, Block controller,
            Function<BlockPos, @Nullable BlockState> inside) {
        return vessel(id, size, casing, controller, List.of(), null, inside);
    }

    private static Blueprint vessel(String id, BlockPos size, Block casing, Block controller, List<BlockPos> glass,
            @Nullable Block glassBlock, Function<BlockPos, @Nullable BlockState> inside) {
        BlockPos at = new BlockPos(size.getX() / 2, 1, size.getZ() - 1);
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < size.getY(); y++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean wall = x == 0 || y == 0 || z == 0 || x == size.getX() - 1 || y == size.getY() - 1 || z == size.getZ() - 1;
                    BlockState state;
                    if (!wall) {
                        state = inside.apply(pos);
                    } else if (pos.equals(at)) {
                        state = controller.defaultBlockState().setValue(VesselControllerBlock.FACING, Direction.SOUTH);
                    } else if (glassBlock != null && glass.contains(pos)) {
                        state = glassBlock.defaultBlockState();
                    } else {
                        state = casing.defaultBlockState();
                    }
                    if (state != null) {
                        placements.add(new Placement(pos, state));
                    }
                }
            }
        }
        return new Blueprint(id, sorted(placements), size);
    }

    // A small 5x5x5 core (up to 7x7 and 9 tall work): a 3x3 lattice of Fuel Channels round a column of Control Rod
    // Channels, Reactor Glass either side of the controller.
    private static Blueprint fissionReactor() {
        BlockPos size = new BlockPos(5, 5, 5);
        return vessel("fission_reactor", size, ModBlocks.REACTOR_CASING.get(), ModBlocks.REACTOR_CONTROLLER.get(),
                List.of(new BlockPos(1, 2, 4), new BlockPos(3, 2, 4)), ModBlocks.REACTOR_GLASS.get(),
                pos -> pos.getX() == 2 && pos.getZ() == 2 ? ModBlocks.CONTROL_ROD_CHANNEL.get().defaultBlockState()
                        : ModBlocks.FUEL_CHANNEL.get().defaultBlockState());
    }

    // A small hot cell 5x4x4 (up to 9 across and 6 tall work): Hot Cell Casing, a Hot Cell Window either side of the
    // controller (its master-slave manipulators reach through over them), empty inside.
    private static Blueprint purexPlant() {
        return vessel("purex_plant", new BlockPos(5, 4, 4), ModBlocks.HOT_CELL_CASING.get(), ModBlocks.PUREX_CONTROLLER.get(),
                List.of(new BlockPos(1, 2, 3), new BlockPos(3, 2, 3)), ModBlocks.HOT_CELL_WINDOW.get(), pos -> null);
    }

    // A containment building, cut down to show how it's built (a real one is round a reactor and its steam generators):
    // walls two thick, Containment Concrete outside and the Steel Liner on its inner face, floor and roof the same; an
    // Airlock through the front's liner (the concrete open in front of it) and a Penetration through the left wall (two
    // in a row, as thick as the wall). Inside, 3x3x3 of air.
    private static Blueprint containment() {
        BlockPos size = new BlockPos(7, 7, 7);
        List<Placement> placements = new ArrayList<>();
        for (int y = 0; y < size.getY(); y++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    int depth = Math.min(Math.min(Math.min(x, size.getX() - 1 - x), Math.min(z, size.getZ() - 1 - z)), Math.min(y, size.getY() - 1 - y));
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = null;
                    if (x == 3 && z >= 5 && (y == 2 || y == 3)) {
                        // The doorway: the airlock in the liner, the concrete open in front of it.
                        if (z == 5) {
                            state = ModBlocks.AIRLOCK.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
                                    .setValue(DoorBlock.HALF, y == 2 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
                        }
                    } else if (z == 3 && y == 3 && x <= 1) {
                        state = ModBlocks.PENETRATION.get().defaultBlockState().setValue(net.zagdrath.arcforge.block.nuclear.PenetrationBlock.FACING,
                                Direction.WEST);
                    } else if (depth == 0) {
                        state = ModBlocks.CONTAINMENT_CONCRETE.get().defaultBlockState();
                    } else if (depth == 1) {
                        state = ModBlocks.STEEL_LINER.get().defaultBlockState();
                    }
                    if (state != null) {
                        placements.add(new Placement(pos, state));
                    }
                }
            }
        }
        return new Blueprint("containment", sorted(placements), size);
    }
}
