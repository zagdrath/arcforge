/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.ElectrolyzingRecipe;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IDisplayHelper;

// An Electrolyzer in Jade, while it runs: "Splitting: 42%" and what each operation makes ("→ 200 mB Hydrogen +
// 100 mB Oxygen"). Its tanks and energy show through Jade's own views. This half gathers the data on the server;
// Client draws it.
public enum ElectrolyzerProvider implements StreamServerDataProvider<BlockAccessor, ElectrolyzerProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "electrolyzer");

    public record Data(int percent, FluidStack primary, FluidStack secondary) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::percent,
                FluidStack.OPTIONAL_STREAM_CODEC, Data::primary,
                FluidStack.OPTIONAL_STREAM_CODEC, Data::secondary,
                Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ElectrolyzerBlockEntity electrolyzer) || electrolyzer.getStatus() != MachineStatus.SPLITTING
                || electrolyzer.getCost() <= 0 || electrolyzer.getShownRecipe() == null) {
            return null;
        }
        ElectrolyzingRecipe recipe = electrolyzer.getShownRecipe();
        return new Data((int) (100L * electrolyzer.getSpent() / electrolyzer.getCost()), recipe.primary().create(),
                recipe.secondary().map(template -> template.create()).orElse(FluidStack.EMPTY));
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
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
            ElectrolyzerProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                tooltip.add(Component.translatable("jade.arcforge.electrolyzer.progress", data.percent()));
                if (data.secondary().isEmpty()) {
                    tooltip.add(Component.translatable("jade.arcforge.chemical_reactor.recipe", amount(data.primary())));
                } else {
                    tooltip.add(Component.translatable("jade.arcforge.electrolyzer", amount(data.primary()), amount(data.secondary())));
                }
            });
        }

        private static Component amount(FluidStack fluid) {
            return Component.translatable("jade.arcforge.chemical_reactor.fluid",
                    IDisplayHelper.get().humanReadableNumber(fluid.getAmount(), "", false), fluid.getHoverName());
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
