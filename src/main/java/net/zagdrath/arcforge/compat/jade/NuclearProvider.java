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
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.CentrifugeCascadeBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.multiblock.CentrifugeCascadeStructure;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The nuclear tier in Jade. The Gas Centrifuge Cascade (on its controller, a floor casing or a centrifuge): its
// centrifuges, the separative work it did last tick against its capacity, its target and tails, its three tanks with
// their assays, and its status. A Pressurized Cylinder holding Uranium Hexafluoride: the gas's assay, and whether the
// cylinder is lined with PVC. The server builds the lines; Client draws them.
public enum NuclearProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "nuclear");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof PressurizedCylinderBlockEntity cylinder) {
            return cylinder(cylinder);
        }
        CentrifugeCascadeBlockEntity cascade = accessor.getBlockEntity() instanceof CentrifugeCascadeBlockEntity own ? own
                : CentrifugeCascadeStructure.findController(accessor.getLevel(), accessor.getPosition());
        if (cascade == null) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        if (!cascade.isFormed()) {
            lines.add(cascade.getStatus().getDescription());
            return lines;
        }
        lines.add(Component.translatable("jade.arcforge.centrifuge_cascade.work", cascade.getCentrifuges(),
                String.format(Locale.ROOT, "%.2f", cascade.getLastSwu()), String.format(Locale.ROOT, "%.2f", cascade.swuPerTick())));
        lines.add(Component.translatable("jade.arcforge.centrifuge_cascade.assays", Enrichment.percent(cascade.getTarget()),
                Enrichment.percent(cascade.getTailsAssay())).withStyle(ChatFormatting.GREEN));
        tank(lines, "feed", cascade.getFeed());
        tank(lines, "product", cascade.getProduct());
        tank(lines, "tails", cascade.getTails());
        lines.add(cascade.getStatus().getDescription());
        return lines;
    }

    private static void tank(List<Component> lines, String key, FilteredFluidTank tank) {
        if (tank.getAmount() <= 0) {
            return;
        }
        lines.add(Component.translatable("jade.arcforge.centrifuge_cascade." + key, String.format(Locale.ROOT, "%,d", tank.getAmount()),
                Enrichment.percent(Enrichment.assay(tank.getResource(0)))).withStyle(ChatFormatting.GRAY));
    }

    private static @Nullable List<Component> cylinder(PressurizedCylinderBlockEntity cylinder) {
        List<Component> lines = new ArrayList<>();
        FluidStack gas = cylinder.getGas();
        int assay = Enrichment.assay(gas);
        if (assay > 0) {
            lines.add(Component.translatable("jade.arcforge.u235_assay", Enrichment.percent(assay)).withStyle(ChatFormatting.GREEN));
        }
        if (cylinder.isLined()) {
            lines.add(Component.translatable("tooltip.arcforge.pressurized_cylinder.lined").withStyle(ChatFormatting.DARK_AQUA));
        }
        return lines.isEmpty() ? null : lines;
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
            NuclearProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
