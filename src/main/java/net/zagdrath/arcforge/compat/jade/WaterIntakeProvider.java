/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.WaterIntakeCasingBlock;
import net.zagdrath.arcforge.blockentity.multiblock.WaterIntakeBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The Industrial Water Intake in Jade, from any casing: the water body it stands in ("Water body: 1,234 / 400 sources",
// "River or ocean: ×1.25") and what it pumps ("Pumping 8,000 mB/t"), or why it doesn't.
public enum WaterIntakeProvider implements StreamServerDataProvider<BlockAccessor, WaterIntakeProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "water_intake");

    public record Data(int waterBlocks, boolean openWater, int pumped) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::waterBlocks, ByteBufCodecs.BOOL, Data::openWater, ByteBufCodecs.VAR_INT, Data::pumped, Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        WaterIntakeBlockEntity intake = WaterIntakeCasingBlock.STRUCTURE.findController(accessor.getLevel(), accessor.getPosition());
        return intake != null && intake.isFormed() ? new Data(intake.getWaterBlocks(), intake.isOpenWater(), intake.getPumped()) : null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            WaterIntakeProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                int needed = ArcforgeConfig.WATER_INTAKE_MIN_WATER.getAsInt();
                tooltip.add(Component.translatable("jade.arcforge.water_intake.body", ArcforgeGui.grouped(data.waterBlocks()), ArcforgeGui.grouped(needed))
                        .withStyle(data.waterBlocks() >= needed ? ChatFormatting.GRAY : ChatFormatting.GOLD));
                if (data.openWater()) {
                    tooltip.add(Component.translatable("jade.arcforge.water_intake.open_water",
                            String.format(java.util.Locale.ROOT, "%.2f", ArcforgeConfig.WATER_INTAKE_OPEN_WATER_MULTIPLIER.getAsDouble())
                                    .replaceAll("0+$", "").replaceAll("\\.$", "")).withColor(0xFF6FA8FF));
                }
                if (data.pumped() > 0) {
                    tooltip.add(Component.translatable("jade.arcforge.water_intake.pumping", ArcforgeGui.grouped(data.pumped())));
                }
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
