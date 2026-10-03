/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.GreenhouseStructure;
import net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure;
import net.zagdrath.arcforge.multiblock.ShellStructure;

// Pressure Glass: the windows of the Steam Boiler Array and Steam Turbine Array, the walls and roof of the
// Greenhouse Array, the windows up the sides of the Thermal Evaporator Array, the faces of the Firebox and Battery Arrays,
// and a decorative glass on its own. Neighbouring panes merge into one window. When a
// structure forms around it, FORMED is set and clicking it opens the machine's GUI; glass never does IO.
public class PressureGlassBlock extends TransparentBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public PressureGlassBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    public static boolean isFormed(BlockState state) {
        return state.getBlock() instanceof PressureGlassBlock && state.getValue(FORMED);
    }

    // The steam arrays and the Gas Turbine Array can use glass.
    private static List<ShellStructure> structures() {
        return List.of(SteamBoilerArrayCasingBlock.STRUCTURE, SteamTurbineArrayCasingBlock.STRUCTURE, GasTurbineArrayCasingBlock.STRUCTURE);
    }

    // The master of the formed array the pane at pos is a window of, or null. Glass isn't a MultiblockPart (it never
    // does IO), so security and Jade find the array through this.
    public static @Nullable ShellMultiblockBlockEntity findMaster(BlockGetter level, BlockPos pos) {
        if (!isFormed(level.getBlockState(pos))) {
            return null;
        }
        for (ShellStructure structure : structures()) {
            ShellMultiblockBlockEntity master = structure.findMaster(level, pos);
            if (master != null) {
                return master;
            }
        }
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            structures().forEach(structure -> ShellCasingBlock.rebuild(serverLevel, pos, placer, structure));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
            GreenhouseStructure.notifyChanged(level, pos);
            ThermalEvaporatorStructure.notifyChanged(level, pos);
            net.zagdrath.arcforge.multiblock.FireboxArrayStructure.notifyChanged(level, pos);
            net.zagdrath.arcforge.multiblock.BatteryArrayStructure.notifyChanged(level, pos);
        }
    }

    // Something appeared beside a formed pane: if it is in the hollow core, the structure breaks.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(FORMED) && !neighbourState.isAir()) {
            ticks.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        for (ShellStructure structure : structures()) {
            if (state.getValue(FORMED)) {
                ShellCasingBlock.checkCore(level, pos, structure);
            } else {
                ShellCasingBlock.rebuild(level, pos, null, structure);
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this) && state.getValue(FORMED)) {
            structures().forEach(structure -> structure.onRemoved(level, pos));
        }
        if (!level.getBlockState(pos).is(this)) {
            GreenhouseStructure.notifyChanged(level, pos);
            ThermalEvaporatorStructure.notifyChanged(level, pos);
            net.zagdrath.arcforge.multiblock.FireboxArrayStructure.notifyChanged(level, pos);
            net.zagdrath.arcforge.multiblock.BatteryArrayStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!state.getValue(FORMED)) {
            return InteractionResult.PASS;
        }
        InteractionResult greenhouse = GreenhouseStructure.useOnPart(level, pos, player);
        if (greenhouse != InteractionResult.PASS) {
            return greenhouse;
        }
        InteractionResult evaporator = ThermalEvaporatorStructure.useOnPart(level, pos, player);
        if (evaporator != InteractionResult.PASS) {
            return evaporator;
        }
        InteractionResult firebox = net.zagdrath.arcforge.multiblock.FireboxArrayStructure.useOnPart(level, pos, player);
        if (firebox != InteractionResult.PASS) {
            return firebox;
        }
        InteractionResult battery = net.zagdrath.arcforge.multiblock.BatteryArrayStructure.useOnPart(level, pos, player);
        if (battery != InteractionResult.PASS) {
            return battery;
        }
        for (ShellStructure structure : structures()) {
            InteractionResult result = ShellCasingBlock.openMenu(level, pos, player, structure);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    }
}
