/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.GasTurbineArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayControllerBlock;
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
                cube("metal_pressing_array", ModBlocks.METAL_PRESSING_ARRAY_CASING.get()),
                cube("superheater_array", ModBlocks.SUPERHEATER_ARRAY_CASING.get()),
                cube("condenser_array", ModBlocks.CONDENSER_ARRAY_CASING.get()),
                carbonizer(),
                arcforgeFurnace(),
                steamBoilerArray(),
                steamTurbineArray(),
                gasTurbineArray(),
                distillationArray(),
                solarThermalArray());
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

    // 3x3, 5 tall (3 to 7 work), with a window up the front.
    private static Blueprint steamBoilerArray() {
        BlockPos size = new BlockPos(3, 5, 3);
        List<BlockPos> glass = List.of(new BlockPos(1, 1, 2), new BlockPos(1, 2, 2), new BlockPos(1, 3, 2));
        return new Blueprint("steam_boiler_array", shell(size, Direction.Axis.Y, ModBlocks.STEAM_BOILER_ARRAY_CASING.get(), glass), size);
    }

    // 3x3, 5 long along X (3 to 9 work), with a window along the front.
    private static Blueprint steamTurbineArray() {
        BlockPos size = new BlockPos(5, 3, 3);
        List<BlockPos> glass = List.of(new BlockPos(1, 1, 2), new BlockPos(2, 1, 2), new BlockPos(3, 1, 2));
        return new Blueprint("steam_turbine_array", shell(size, Direction.Axis.X, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get(), glass), size);
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
}
