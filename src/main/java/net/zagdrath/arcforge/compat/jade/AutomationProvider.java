/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockBreakerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockPlacerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The automation blocks in Jade: what the Assembler crafts ("Crafting: Piston"), the Block Breaker's target and
// progress, what the Block Placer places next, the Vacuum Collector's range and filter, then the status, and
// "Redstone: Pulse" when set. The server builds the lines; Client draws them.
public enum AutomationProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "automation");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MachineBlockEntity machine) || !(accessor.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        switch (machine) {
            case AssemblerBlockEntity assembler -> {
                if (assembler.getRecipe() != null) {
                    ItemStack result = assembler.getPreview().getItem(0);
                    Component name = result.getCount() > 1 ? Component.literal(result.getCount() + " × ").append(result.getHoverName()) : result.getHoverName();
                    lines.add(Component.translatable("jade.arcforge.assembler", name));
                }
                if (assembler.getStatus() == MachineStatus.CRAFTING && assembler.craftTicks() > 0) {
                    lines.add(Component.translatable("gui.arcforge.percent", 100 * assembler.getProgress() / assembler.craftTicks()));
                }
            }
            case BlockBreakerBlockEntity breaker -> {
                BlockState target = level.getBlockState(breaker.getBlockPos().relative(breaker.getFacing()));
                if (!target.isAir()) {
                    lines.add(Component.translatable("jade.arcforge.breaker.target", target.getBlock().getName()));
                }
                if (breaker.getStatus() == MachineStatus.BREAKING && breaker.getTotal() > 0) {
                    lines.add(Component.translatable("jade.arcforge.breaker", 100 * breaker.getProgress() / breaker.getTotal()));
                }
            }
            case BlockPlacerBlockEntity placer -> {
                int next = placer.nextSlot();
                if (next >= 0) {
                    lines.add(Component.translatable("jade.arcforge.placer", placer.getItems().getStack(next).getHoverName()));
                }
            }
            case VacuumCollectorBlockEntity collector -> lines.add(Component.translatable("jade.arcforge.vacuum", collector.getRange(),
                    Component.translatable(collector.isFiltered() ? "jade.arcforge.vacuum.filtered" : "jade.arcforge.vacuum.all")));
            default -> {
                return null;
            }
        }
        lines.add(machine.getStatus().getDescription());
        if (machine.getRedstoneMode() == RedstoneMode.PULSE) {
            lines.add(Component.translatable("jade.arcforge.redstone", Component.translatable("gui.arcforge.redstone.pulse")));
        }
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
            AutomationProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
