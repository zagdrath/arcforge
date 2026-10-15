/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock;
import net.zagdrath.arcforge.block.multiblock.OilfieldPart;
import net.zagdrath.arcforge.registry.ModBlocks;

// The oil and refinery family's fixed-size multiblocks: the oilfield's two, and the refinery's Cracking Unit and Diesel
// Generator (PlantBox). Their controllers' block entities run them and decide whether they're formed; the other parts
// find the controller by searching around themselves.
//
// The Oil Derrick (Rig): a 3x3 rig six blocks tall over the hole it drills. Its floor (the bottom layer) is a ring of
// eight Derrick Casings round the bore, which stays open, with the Oil Derrick Controller in the middle of one side,
// facing out; four Derrick Frame legs stand on its corners for four layers, and a full 3x3 of Derrick Frames crowns it.
// The space over the bore stays open for the drill string.
//
// The Pumpjack (Unit): built on a Well Head (what a Derrick leaves when it strikes oil). A skid of Pumpjack Casings 3
// wide and 5 long round the Well Head, which is the middle of one end, with the Pumpjack Controller in the middle of
// the other end, facing away from the well; and a Samson post of three Pumpjack Casings standing on the middle of the
// skid.
//
// The Cracking Unit and Diesel Generator (PlantBox): a solid box of their casings, 3 wide, with the controller in the
// middle of the bottom of one end, facing out of it: the Cracking Unit 3 long and 5 tall, the Diesel Generator 5 long
// and 2 tall.
public final class OilfieldStructures {
    public static final int RIG_WIDTH = 3, RIG_HEIGHT = 6, LEG_LAYERS = 4;
    public static final int UNIT_WIDTH = 3, UNIT_LENGTH = 5, UNIT_HEIGHT = 4, POST_U = 2;
    public static final int CRACKER_WIDTH = 3, CRACKER_HEIGHT = 5, CRACKER_LENGTH = 3;
    public static final int GENERATOR_WIDTH = 3, GENERATOR_HEIGHT = 2, GENERATOR_LENGTH = 5;

    // --- The Oil Derrick ---

    public record Rig(BlockPos min, Direction front) {
        public BlockPos max() {
            return min.offset(RIG_WIDTH - 1, RIG_HEIGHT - 1, RIG_WIDTH - 1);
        }

        // The open middle of the floor, where the hole goes down.
        public BlockPos bore() {
            return min.offset(1, 0, 1);
        }

        public BlockPos controller() {
            return bore().relative(front);
        }

        // Whether a block of the rig (not the open bore or the space round the drill string) is at pos.
        public boolean contains(BlockPos pos) {
            int x = pos.getX() - min.getX(), y = pos.getY() - min.getY(), z = pos.getZ() - min.getZ();
            if (x < 0 || x >= RIG_WIDTH || y < 0 || y >= RIG_HEIGHT || z < 0 || z >= RIG_WIDTH) {
                return false;
            }
            return isFrameAt(x, y, z) || y == 0 && !(x == 1 && z == 1);
        }

        private static boolean isFrameAt(int x, int y, int z) {
            if (y == RIG_HEIGHT - 1) {
                return true;
            }
            return y >= 1 && y <= LEG_LAYERS && (x == 0 || x == RIG_WIDTH - 1) && (z == 0 || z == RIG_WIDTH - 1);
        }

        public boolean isFrame(BlockPos pos) {
            return contains(pos) && isFrameAt(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
        }

        public List<BlockPos> parts() {
            List<BlockPos> parts = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(min, max())) {
                if (contains(pos)) {
                    parts.add(pos.immutable());
                }
            }
            return parts;
        }

        // The open space the drill string hangs in: the bore and the column over it, under the crown.
        public List<BlockPos> openings() {
            List<BlockPos> open = new ArrayList<>();
            for (int y = 0; y < RIG_HEIGHT - 1; y++) {
                open.add(bore().above(y));
            }
            return open;
        }
    }

    // The valid rig the controller at pos is in, or null: tried facing the way the controller faces first, then the
    // other three. current: the rig it has now (its blocks may still be formed).
    public static @Nullable Rig findRig(Level level, BlockPos controller, @Nullable Rig current) {
        for (Direction front : candidates(level, controller)) {
            BlockPos bore = controller.relative(front.getOpposite());
            Rig rig = new Rig(bore.offset(-1, 0, -1), front);
            if (isValid(level, rig, controller, current)) {
                return rig;
            }
        }
        return null;
    }

    private static boolean isValid(Level level, Rig rig, BlockPos controller, @Nullable Rig current) {
        for (BlockPos pos : rig.parts()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            boolean fits = pos.equals(controller) ? state.is(ModBlocks.OIL_DERRICK_CONTROLLER.get())
                    : rig.isFrame(pos) ? state.is(ModBlocks.DERRICK_FRAME.get()) : state.is(ModBlocks.DERRICK_CASING.get());
            if (!fits || state.getValue(OilfieldPart.FORMED) && (current == null || !current.contains(pos))) {
                return false;
            }
        }
        for (BlockPos pos : rig.openings()) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    // --- The Pumpjack ---

    public record Unit(BlockPos well, Direction back) {
        // The block x across (-1 to 1, to the right seen from the well), y up and u along (0 at the well) from the
        // well.
        public BlockPos at(int x, int y, int u) {
            return well.relative(back, u).relative(back.getClockWise(), x).above(y);
        }

        public BlockPos controller() {
            return at(0, 0, UNIT_LENGTH - 1);
        }

        public BlockPos min() {
            BlockPos a = at(-1, 0, 0), b = at(1, UNIT_HEIGHT - 1, UNIT_LENGTH - 1);
            return new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()));
        }

        public BlockPos max() {
            BlockPos a = at(-1, 0, 0), b = at(1, UNIT_HEIGHT - 1, UNIT_LENGTH - 1);
            return new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()));
        }

        // The skid round the well and the Samson post (not the Well Head itself).
        public List<BlockPos> parts() {
            List<BlockPos> parts = new ArrayList<>();
            for (int u = 0; u < UNIT_LENGTH; u++) {
                for (int x = -1; x <= 1; x++) {
                    if (u != 0 || x != 0) {
                        parts.add(at(x, 0, u));
                    }
                }
            }
            for (int y = 1; y < UNIT_HEIGHT; y++) {
                parts.add(at(0, y, POST_U));
            }
            return parts;
        }

        public boolean contains(BlockPos pos) {
            return parts().contains(pos);
        }
    }

    // The valid pumpjack the controller at pos is in, or null (see findRig).
    public static @Nullable Unit findUnit(Level level, BlockPos controller, @Nullable Unit current) {
        for (Direction back : candidates(level, controller)) {
            Unit unit = new Unit(controller.relative(back.getOpposite(), UNIT_LENGTH - 1), back);
            if (isValid(level, unit, controller, current)) {
                return unit;
            }
        }
        return null;
    }

    private static boolean isValid(Level level, Unit unit, BlockPos controller, @Nullable Unit current) {
        // A gas well takes a Gas Wellhead, not a Pumpjack.
        if (!level.isLoaded(unit.well()) || !level.getBlockState(unit.well()).is(ModBlocks.WELL_HEAD.get())
                || net.zagdrath.arcforge.block.multiblock.WellHeadBlock.isGasWell(level.getBlockState(unit.well()))) {
            return false;
        }
        for (BlockPos pos : unit.parts()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            boolean fits = pos.equals(controller) ? state.is(ModBlocks.PUMPJACK_CONTROLLER.get()) : state.is(ModBlocks.PUMPJACK_CASING.get());
            if (!fits || state.getValue(OilfieldPart.FORMED) && (current == null || !current.contains(pos))) {
                return false;
            }
        }
        return true;
    }

    // --- The Cracking Unit and the Diesel Generator ---

    // A box of casings `width` across (odd), `height` tall and `length` long, its controller in the middle of the
    // bottom of its front end (front), the box running `back` from there.
    public record PlantBox(BlockPos front, Direction back, int width, int height, int length) {
        public static PlantBox of(PlantKind kind, BlockPos front, Direction back) {
            return kind == PlantKind.CRACKING_UNIT ? new PlantBox(front, back, CRACKER_WIDTH, CRACKER_HEIGHT, CRACKER_LENGTH)
                    : new PlantBox(front, back, GENERATOR_WIDTH, GENERATOR_HEIGHT, GENERATOR_LENGTH);
        }

        // The block x across (to the right seen from the front), y up and u back from the front.
        public BlockPos at(int x, int y, int u) {
            return front.relative(back, u).relative(back.getClockWise(), x).above(y);
        }

        public BlockPos controller() {
            return front;
        }

        // Which way the controller faces: out of the front.
        public Direction facing() {
            return back.getOpposite();
        }

        public BlockPos min() {
            BlockPos a = at(-width / 2, 0, 0), b = at(width / 2, height - 1, length - 1);
            return new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()));
        }

        public BlockPos max() {
            BlockPos a = at(-width / 2, 0, 0), b = at(width / 2, height - 1, length - 1);
            return new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()));
        }

        public List<BlockPos> parts() {
            List<BlockPos> parts = new ArrayList<>();
            for (int u = 0; u < length; u++) {
                for (int y = 0; y < height; y++) {
                    for (int x = -width / 2; x <= width / 2; x++) {
                        parts.add(at(x, y, u));
                    }
                }
            }
            return parts;
        }

        public boolean contains(BlockPos pos) {
            BlockPos min = min(), max = max();
            return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    // The valid box of this kind the controller at pos is the front of, or null (see findRig).
    public static @Nullable PlantBox findPlant(Level level, BlockPos controller, PlantKind kind, @Nullable PlantBox current) {
        for (Direction facing : candidates(level, controller)) {
            PlantBox box = PlantBox.of(kind, controller, facing.getOpposite());
            if (isValid(level, box, kind, controller, current)) {
                return box;
            }
        }
        return null;
    }

    private static boolean isValid(Level level, PlantBox box, PlantKind kind, BlockPos controller, @Nullable PlantBox current) {
        Block casing = kind == PlantKind.CRACKING_UNIT ? ModBlocks.CRACKING_UNIT_CASING.get() : ModBlocks.DIESEL_GENERATOR_CASING.get();
        Block control = kind == PlantKind.CRACKING_UNIT ? ModBlocks.CRACKING_UNIT_CONTROLLER.get() : ModBlocks.DIESEL_GENERATOR_CONTROLLER.get();
        for (BlockPos pos : box.parts()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            boolean fits = pos.equals(controller) ? state.is(control) : state.is(casing);
            if (!fits || state.getValue(OilfieldPart.FORMED) && (current == null || !current.contains(pos))) {
                return false;
            }
        }
        return true;
    }

    // The way the controller faces first, then the rest clockwise.
    private static Direction[] candidates(Level level, BlockPos controller) {
        BlockState state = level.getBlockState(controller);
        Direction facing = state.hasProperty(OilfieldControllerBlock.FACING) ? state.getValue(OilfieldControllerBlock.FACING) : Direction.NORTH;
        return new Direction[] { facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise() };
    }

    // --- Forming and breaking (both) ---

    public static void form(ServerLevel level, List<BlockPos> parts, BlockPos controller, Direction facing, BlockPos min, BlockPos max) {
        for (BlockPos pos : parts) {
            BlockState state = level.getBlockState(pos);
            BlockState formed = state.setValue(OilfieldPart.FORMED, true);
            if (pos.equals(controller)) {
                formed = formed.setValue(OilfieldControllerBlock.FACING, facing);
            }
            if (formed != state) {
                level.setBlock(pos, formed, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, min, max);
    }

    // Leaves the block at skip (a controller being broken) alone.
    public static void unform(ServerLevel level, List<BlockPos> parts, @Nullable BlockPos skip, BlockPos min, BlockPos max) {
        for (BlockPos pos : parts) {
            BlockState state = level.getBlockState(pos);
            if (!pos.equals(skip) && state.getBlock() instanceof OilfieldPart && state.getValue(OilfieldPart.FORMED)) {
                BlockState loose = state.setValue(OilfieldPart.FORMED, false);
                if (loose.hasProperty(OilfieldControllerBlock.LIT)) {
                    loose = loose.setValue(OilfieldControllerBlock.LIT, false);
                }
                level.setBlock(pos, loose, Block.UPDATE_ALL);
            }
        }
        MultiblockAutomation.refresh(level, min, max);
    }

    // --- Finding the controller from any part ---

    public static boolean isFormedPart(BlockState state) {
        return state.getBlock() instanceof OilfieldPart && state.getValue(OilfieldPart.FORMED);
    }

    public static @Nullable OilfieldController findController(BlockGetter level, BlockPos pos) {
        if (!isFormedPart(level.getBlockState(pos))) {
            return null;
        }
        OilfieldController[] found = new OilfieldController[1];
        forEachNearbyController(level, pos, controller -> {
            if (found[0] == null && controller.isFormed() && controller.isPart(pos)) {
                found[0] = controller;
            }
        });
        return found[0];
    }

    // A part near pos was placed, broken or wrenched: every controller whose structure could include it rechecks next
    // tick.
    public static void notifyChanged(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            forEachNearbyController(level, pos, OilfieldController::requestCheck);
        }
    }

    // Controllers of any rig or pumpjack pos could be part of: a rig's controller is on its floor, up to two blocks
    // across and five below its crown; a pumpjack's up to five along its skid and three below its post.
    private static void forEachNearbyController(BlockGetter level, BlockPos pos, Consumer<OilfieldController> action) {
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
        for (int dy = -(RIG_HEIGHT - 1); dy <= 0; dy++) {
            for (int dx = -UNIT_LENGTH; dx <= UNIT_LENGTH; dx++) {
                for (int dz = -UNIT_LENGTH; dz <= UNIT_LENGTH; dz++) {
                    candidate.setWithOffset(pos, dx, dy, dz);
                    if (level.getBlockState(candidate).getBlock() instanceof OilfieldControllerBlock
                            && level.getBlockEntity(candidate) instanceof OilfieldController controller) {
                        action.accept(controller);
                    }
                }
            }
        }
    }

    // --- Shared behaviour of the parts ---

    // Right-clicking a part of a formed rig or pumpjack opens it.
    public static InteractionResult useOnPart(Level level, BlockPos pos, Player player) {
        OilfieldController controller = findController(level, pos);
        if (controller == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && controller instanceof net.minecraft.world.MenuProvider menu) {
            player.openMenu(menu, controller.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // Wrench (Configure mode) on a part: nearby controllers recheck, and the player hears whether it's formed.
    public static InteractionResult wrenchPart(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        forEachNearbyController(level, pos, OilfieldController::checkNow);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(findController(level, pos) != null
                    ? "message.arcforge.oilfield.formed" : "message.arcforge.oilfield.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }
}
