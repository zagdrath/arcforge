/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.VesselStructure;

// A Control Rod Channel: a channel for a Control Rod, driven from the vessel's head. Each belongs to one of four rod
// banks (BANK, A to D, shown by the colour band on its top), which move together; the Wrench in Configure mode moves it
// to the next bank. A vertical run of them is one long rod: a bank goes in from the top of the core.
public class ControlRodChannelBlock extends VesselInteriorBlock implements MultiblockPart {
    public static final IntegerProperty BANK = IntegerProperty.create("bank", 0, 3);

    public ControlRodChannelBlock(Supplier<VesselStructure> structure, BlockBehaviour.Properties properties) {
        super(structure, properties);
        registerDefaultState(defaultBlockState().setValue(BANK, 0));
    }

    public static String bankName(int bank) {
        return String.valueOf((char) ('A' + bank));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BANK);
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return null;
    }

    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        int bank = (state.getValue(BANK) + 1) % 4;
        level.setBlock(pos, state.setValue(BANK, bank), Block.UPDATE_ALL);
        structure().notifyChanged(level, pos);
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.control_rod_channel.bank", bankName(bank)));
        }
        return InteractionResult.SUCCESS;
    }
}
