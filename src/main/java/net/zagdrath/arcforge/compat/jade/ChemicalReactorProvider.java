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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IDisplayHelper;

// A Chemical Reactor in Jade, while it runs: "Reacting: 42%" and what it makes ("→ 300 mB Iron Slurry",
// "→ Iron Dust"). Its tanks and energy show through Jade's own views. This half gathers the data on the
// server; Client draws it.
public enum ChemicalReactorProvider implements StreamServerDataProvider<BlockAccessor, ChemicalReactorProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "chemical_reactor");

    public record Data(int percent, ItemStack item, FluidStack fluid) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::percent,
                ItemStack.OPTIONAL_STREAM_CODEC, Data::item,
                FluidStack.OPTIONAL_STREAM_CODEC, Data::fluid,
                Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ChemicalReactorBlockEntity reactor) || reactor.getStatus() != MachineStatus.REACTING
                || reactor.getTotal() <= 0) {
            return null;
        }
        return new Data(100 * reactor.getProgress() / reactor.getTotal(), reactor.getMakingItem(), reactor.getMakingFluid());
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
            ChemicalReactorProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                tooltip.add(Component.translatable("jade.arcforge.chemical_reactor.progress", data.percent()));
                if (!data.fluid().isEmpty()) {
                    Component amount = Component.translatable("jade.arcforge.chemical_reactor.fluid",
                            IDisplayHelper.get().humanReadableNumber(data.fluid().getAmount(), "", false), data.fluid().getHoverName());
                    tooltip.add(Component.translatable("jade.arcforge.chemical_reactor.recipe", amount));
                }
                if (!data.item().isEmpty()) {
                    Component item = data.item().getCount() > 1
                            ? Component.literal(data.item().getCount() + " × ").append(data.item().getHoverName())
                            : data.item().getHoverName();
                    tooltip.add(Component.translatable("jade.arcforge.chemical_reactor.recipe", item));
                }
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
