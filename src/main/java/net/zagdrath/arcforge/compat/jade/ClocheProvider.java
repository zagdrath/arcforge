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
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.HydroponicCellBlockEntity;
import net.zagdrath.arcforge.farming.ClochePlants;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The Glass Cloche, Grow Chamber and Hydroponic Cell in Jade (their tank and FE show through Jade's own views): what's
// growing and how far ("Wheat Seeds: 45%"), how fast ("x3.75"), the fertilizer left ("Fertilized: 4 harvests"), the
// Hydroponic Cell's Carbon Dioxide boost, and the status. The server builds the lines; Client draws them.
public enum ClocheProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "cloche");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ClocheBlockEntity farm)) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        ItemStack seed = farm.getItems().getStack(ClocheBlockEntity.SLOT_SEED);
        ClochePlants.Plant plant = farm.plant();
        if (plant != null) {
            lines.add(Component.translatable("jade.arcforge.cloche.growing", seed.getHoverName(), (int) Math.floor(farm.growth() * 100)));
        }
        if (farm.getRate() > 0) {
            lines.add(Component.translatable("jade.arcforge.cloche.speed", String.format(Locale.ROOT, "%.2f", farm.getRate()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (farm.getFertilizer() > 0) {
            lines.add(Component.translatable(farm.isEnriched() ? "jade.arcforge.cloche.fertilized_enriched" : "jade.arcforge.cloche.fertilized",
                    farm.getFertilizer()).withStyle(ChatFormatting.GREEN));
        }
        if (farm instanceof HydroponicCellBlockEntity cell && cell.isBoosted()) {
            lines.add(Component.translatable("jade.arcforge.cloche.co2").withStyle(ChatFormatting.AQUA));
        }
        lines.add(farm.getStatus().getDescription());
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
            ClocheProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
