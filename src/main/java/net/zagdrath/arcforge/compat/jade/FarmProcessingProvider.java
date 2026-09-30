/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.farming.MillstoneBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MillBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.OilPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SeedExtractorBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Farm processing in Jade (their items, fluids, FE and heat show through Jade's own views):
//  - Millstone: what it's grinding and the turns ("Grinding Wheat: 1 / 2 turns"), or "Empty".
//  - Mill: lanes working ("Milling: 2 / 3 lanes").
//  - Oil Press and Seed Extractor: progress ("45%").
//  - Grain Dryer: its temperature against the 60°C it needs.
//  - Fermenter: the Dried Hops still boosting ("Dried Hops: 3 operations left").
// Then the machine's status. The server builds the lines; Client draws them.
public enum FarmProcessingProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "farm_processing");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        List<Component> lines = new ArrayList<>();
        switch (accessor.getBlockEntity()) {
            case MillstoneBlockEntity millstone -> {
                ItemStack input = millstone.getItems().getStack(MillstoneBlockEntity.SLOT_INPUT);
                int needed = millstone.turnsNeeded();
                lines.add(input.isEmpty() || needed == 0 ? Component.translatable("jade.arcforge.millstone.empty")
                        : Component.translatable("jade.arcforge.millstone.grinding", input.getHoverName(), millstone.getTurns(), needed));
                return lines;
            }
            case MillBlockEntity mill -> {
                int working = 0;
                for (int lane = 0; lane < MillBlockEntity.LANES; lane++) {
                    working += mill.getLane(lane).getProgress() > 0 ? 1 : 0;
                }
                lines.add(Component.translatable("jade.arcforge.mill.lanes", working, MillBlockEntity.LANES));
            }
            case OilPressBlockEntity press -> {
                if (press.getStatus() == MachineStatus.PRESSING && press.getTotal() > 0) {
                    lines.add(Component.translatable("gui.arcforge.percent", 100 * press.getProgress() / press.getTotal()));
                }
            }
            case SeedExtractorBlockEntity extractor -> {
                if (extractor.getStatus() == MachineStatus.EXTRACTING && extractor.getLane().getTotal() > 0) {
                    lines.add(Component.translatable("gui.arcforge.percent", 100 * extractor.getLane().getProgress() / extractor.getLane().getTotal()));
                }
            }
            case GrainDryerBlockEntity dryer -> {
                int celsius = dryer.getHeat().getTemperature();
                int needed = GrainDryerBlockEntity.minTemperature();
                lines.add(Component.translatable("jade.arcforge.grain_dryer.temperature", celsius, needed)
                        .withStyle(celsius > needed ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            }
            case FermenterBlockEntity fermenter -> {
                if (fermenter.getAdditiveLeft() > 0) {
                    lines.add(Component.translatable("jade.arcforge.fermenter.additive", fermenter.getAdditiveLeft()).withStyle(ChatFormatting.GOLD));
                }
                return lines.isEmpty() ? null : lines;
            }
            case null, default -> {
                return null;
            }
        }
        lines.add(((MachineBlockEntity) accessor.getBlockEntity()).getStatus().getDescription());
        return lines;
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
            FarmProcessingProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
