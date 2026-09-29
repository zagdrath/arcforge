/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;

// The Settings Card's shared pieces: redstone mode, auto-eject, and a formed structure's ports. Ports are kept in
// the structure's own frame (see Frame), so a card pastes onto a copy of the structure built facing another way.
public final class MachineSettings {
    private MachineSettings() {}

    public static Identifier kind(BlockEntity blockEntity) {
        return blockEntity instanceof MultiblockController
                ? BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())
                : BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
    }

    public static Component onOff(boolean on) {
        return Component.translatable(on ? "settings.arcforge.on" : "settings.arcforge.off");
    }

    // --- Redstone and auto-eject ---

    public static void writeRedstone(ValueOutput output, RedstoneMode mode) {
        output.putInt("redstone_mode", mode.ordinal());
    }

    // Returns 1 if the stored mode isn't one this machine offers (skipped), else 0.
    public static int readRedstone(ValueInput input, ConfigurableMachine machine) {
        Optional<Integer> id = input.getInt("redstone_mode");
        if (id.isEmpty()) {
            return 0;
        }
        RedstoneMode mode = RedstoneMode.byId(id.get());
        if (!machine.getAllowedRedstoneModes().contains(mode)) {
            return 1;
        }
        machine.setRedstoneMode(mode);
        return 0;
    }

    public static void describeRedstone(ValueInput input, List<Component> lines) {
        input.getInt("redstone_mode").ifPresent(id ->
                lines.add(Component.translatable("settings.arcforge.redstone", RedstoneMode.byId(id).getDescription())));
    }

    public static void describeAutoEject(ValueInput input, List<Component> lines) {
        input.read("auto_eject", com.mojang.serialization.Codec.BOOL).ifPresent(on -> lines.add(Component.translatable("settings.arcforge.auto_eject", onOff(on))));
    }

    // --- A structure's ports ---

    // A formed structure's frame: forward runs along its long horizontal axis (or its facing, on a square footprint),
    // right is forward turned clockwise, and up is up; the origin is the corner where all three are lowest.
    public record Frame(BlockPos origin, Direction right, Direction forward, int width, int height, int depth) {
        public static Frame of(MultiblockController controller) {
            BlockPos min = controller.getMinCorner();
            BlockPos max = controller.getMaxCorner();
            int sizeX = max.getX() - min.getX() + 1;
            int sizeZ = max.getZ() - min.getZ() + 1;
            Direction forward = sizeX > sizeZ ? Direction.EAST : sizeZ > sizeX ? Direction.SOUTH : controller.getStructureFacing();
            if (!forward.getAxis().isHorizontal()) {
                forward = Direction.SOUTH;
            }
            Direction right = forward.getClockWise();
            // Each horizontal axis belongs to one of forward or right; its low end is the corner.
            int x = right.getAxis() == Direction.Axis.X ? (right.getStepX() > 0 ? min.getX() : max.getX()) : (forward.getStepX() > 0 ? min.getX() : max.getX());
            int z = right.getAxis() == Direction.Axis.Z ? (right.getStepZ() > 0 ? min.getZ() : max.getZ()) : (forward.getStepZ() > 0 ? min.getZ() : max.getZ());
            int width = right.getAxis() == Direction.Axis.X ? sizeX : sizeZ;
            int depth = forward.getAxis() == Direction.Axis.X ? sizeX : sizeZ;
            return new Frame(new BlockPos(x, min.getY(), z), right, forward, width, max.getY() - min.getY() + 1, depth);
        }

        public SettingsCardData.StructureSize size() {
            return new SettingsCardData.StructureSize(width, height, depth);
        }

        public int[] local(BlockPos pos) {
            BlockPos offset = pos.subtract(origin);
            return new int[] { dot(offset, right), offset.getY(), dot(offset, forward) };
        }

        public BlockPos world(int a, int b, int c) {
            return origin.relative(right, a).above(b).relative(forward, c);
        }

        // Faces as 0 right, 1 left, 2 up, 3 down, 4 forward, 5 back.
        public int localFace(Direction face) {
            if (face == right) return 0;
            if (face == right.getOpposite()) return 1;
            if (face == Direction.UP) return 2;
            if (face == Direction.DOWN) return 3;
            return face == forward ? 4 : 5;
        }

        public Direction worldFace(int local) {
            return switch (local) {
                case 0 -> right;
                case 1 -> right.getOpposite();
                case 2 -> Direction.UP;
                case 3 -> Direction.DOWN;
                case 4 -> forward;
                default -> forward.getOpposite();
            };
        }

        private static int dot(BlockPos offset, Direction direction) {
            return offset.getX() * direction.getStepX() + offset.getZ() * direction.getStepZ();
        }
    }

    // Every port as five ints: a, b, c (the block in the frame), the face, and the mode.
    public static void writePorts(ValueOutput output, Level level, MultiblockController controller) {
        Frame frame = Frame.of(controller);
        List<Integer> packed = new ArrayList<>();
        for (MultiblockPorts.Port port : MultiblockPorts.list(level, controller)) {
            int[] local = frame.local(port.pos());
            packed.add(local[0]);
            packed.add(local[1]);
            packed.add(local[2]);
            packed.add(frame.localFace(port.face()));
            packed.add(port.mode().ordinal());
        }
        output.putIntArray("ports", packed.stream().mapToInt(Integer::intValue).toArray());
    }

    // Replaces the structure's ports with the card's; returns how many couldn't go where they were (a block that
    // can't hold one there, or a mode this structure doesn't have).
    public static int readPorts(ValueInput input, Level level, MultiblockController controller) {
        Optional<int[]> stored = input.getIntArray("ports");
        if (stored.isEmpty()) {
            return 0;
        }
        for (MultiblockPorts.Port port : MultiblockPorts.list(level, controller)) {
            MultiblockPorts.set(level, controller, port.pos(), SideMode.NONE, port.face());
        }
        Frame frame = Frame.of(controller);
        int[] packed = stored.get();
        int skipped = 0;
        for (int i = 0; i + 4 < packed.length; i += 5) {
            BlockPos pos = frame.world(packed[i], packed[i + 1], packed[i + 2]);
            Direction face = frame.worldFace(packed[i + 3]);
            SideMode mode = SideMode.byId(packed[i + 4]);
            if (!MultiblockPorts.canHold(level, controller, pos) || !MultiblockPorts.outerFaces(controller, pos).contains(face)
                    || !controller.getAllowedSideModes().contains(mode)) {
                skipped++;
                continue;
            }
            MultiblockPorts.set(level, controller, pos, mode, face);
        }
        return skipped;
    }

    public static void describePorts(ValueInput input, List<Component> lines) {
        input.getIntArray("ports").ifPresent(packed -> lines.add(Component.translatable("settings.arcforge.ports", packed.length / 5)));
    }
}
