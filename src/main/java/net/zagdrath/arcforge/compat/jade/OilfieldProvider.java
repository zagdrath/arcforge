/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.WellHeadBlock;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.OilDerrickBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.PumpjackBlockEntity;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.oil.OilDeposits;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Crude oil in Jade. On any block of an Oil Derrick: how deep its hole is against the oil's depth, the Steel Pipes
// still to come and its status. On a Pumpjack: the deposit under it, how much is left (or that it's down to a trickle),
// how fast it pumps and its status. On a Well Head: the deposit under it. On any block of a Distillation Array: its
// size, feed rate, temperature against what its feed needs, and its status (its tanks show in Jade's fluid view).
// MultiblockNameProvider names them. The server builds the lines; Client draws them.
public enum OilfieldProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "oilfield");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        if (accessor.getBlock() instanceof WellHeadBlock) {
            deposit(lines, level, accessor);
            return lines;
        }
        Object controller = accessor.getBlockEntity();
        if (!(controller instanceof OilDerrickBlockEntity || controller instanceof PumpjackBlockEntity || controller instanceof DistillationArrayBlockEntity)) {
            controller = OilfieldStructures.findController(level, accessor.getPosition());
            if (controller == null) {
                controller = DistillationStructure.findController(level, accessor.getPosition());
            }
        }
        if (controller instanceof OilDerrickBlockEntity derrick) {
            if (derrick.isFormed()) {
                lines.add(derrick.getTarget() > 0 ? Component.translatable("jade.arcforge.oil_derrick.depth", derrick.getDepth(), derrick.getTarget())
                        : Component.translatable("jade.arcforge.oil_derrick.no_oil").withStyle(ChatFormatting.GRAY));
                if (derrick.getTarget() > 0) {
                    lines.add(Component.translatable("jade.arcforge.oil_derrick.pipes", derrick.pipesNeeded()).withStyle(ChatFormatting.GRAY));
                }
            }
            lines.add(derrick.getStatus().getDescription());
        } else if (controller instanceof PumpjackBlockEntity pumpjack) {
            if (pumpjack.isFormed() && pumpjack.getUnit() != null) {
                deposit(lines, level, pumpjack.getUnit().well());
                lines.add(Component.translatable("jade.arcforge.pumpjack.rate", String.format(Locale.ROOT, "%.1f", pumpjack.getRate()))
                        .withStyle(ChatFormatting.GOLD));
            }
            lines.add(pumpjack.getStatus().getDescription());
        } else if (controller instanceof DistillationArrayBlockEntity column) {
            if (column.isFormed()) {
                lines.add(Component.translatable("jade.arcforge.distillation_array.size", column.getWidth(), column.getHeight(), column.feedRate()));
                lines.add(Component.translatable("jade.arcforge.distillation_array.temperature", column.getHeat().getTemperature())
                        .withStyle(ChatFormatting.GOLD));
            }
            lines.add(column.getStatus().getDescription());
        } else {
            return null;
        }
        return lines;
    }

    private static void deposit(List<Component> lines, ServerLevel level, BlockAccessor accessor) {
        deposit(lines, level, accessor.getPosition());
    }

    private static void deposit(List<Component> lines, ServerLevel level, net.minecraft.core.BlockPos pos) {
        OilDeposits.Deposit deposit = OilDeposits.at(level, pos);
        if (deposit == null) {
            lines.add(Component.translatable("jade.arcforge.oil.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        long left = OilDeposits.get(level.getServer()).remaining(deposit);
        lines.add(left > 0
                ? Component.translatable("jade.arcforge.oil.deposit", deposit.kind().getDescription(),
                        String.format(Locale.ROOT, "%,d", OilDeposits.roughBuckets(left)))
                : Component.translatable("jade.arcforge.oil.drained", deposit.kind().getDescription()));
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, List<Component>> streamCodec() {
        return CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    // Jade needs the tooltip half as its own provider, under the same uid.
    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            OilfieldProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
