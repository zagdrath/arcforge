/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.logistics.WiredMachineLinkBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.link.LinkableMachine;
import net.zagdrath.arcforge.machine.link.MachineLink;
import net.zagdrath.arcforge.machine.link.MachineLinks;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.Multiblocks;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Jade for the Wired Machine Link: the block itself (the machine it's linked to, or why not, and each face's mode), and
// the machine or multiblock at the cable's other end (the face its Wired Machine Link port is on).
public final class MachineLinkProviders {
    private MachineLinkProviders() {}

    private static Identifier uid(String name) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, name);
    }

    // The block's own state is synced to clients, so this reads it there.
    public enum Link implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof WiredMachineLinkBlockEntity link)) {
                return;
            }
            if (link.isLinked()) {
                tooltip.add(Component.translatable("jade.arcforge.wired_machine_link.linked", link.getMachineName()).withStyle(ChatFormatting.LIGHT_PURPLE));
                RelativeSide side = link.getMachineSide();
                if (side != null) {
                    tooltip.add(Component.translatable("gui.arcforge.wired_machine_link.cable", side.getDescription(), link.getDistance())
                            .withStyle(ChatFormatting.GRAY));
                }
            } else {
                tooltip.add(Component.translatable("gui.arcforge.wired_machine_link.not_linked").withStyle(ChatFormatting.RED));
                tooltip.add(link.getStatus().description().copy().withStyle(ChatFormatting.GRAY));
            }
            for (Map.Entry<RelativeSide, SideMode> face : link.setFaces()) {
                tooltip.add(Component.translatable("jade.arcforge.wired_machine_link.face", face.getKey().getDescription(), face.getValue().getDescription()));
            }
        }

        @Override
        public Identifier getUid() {
            return uid("wired_machine_link");
        }
    }

    // The machine end: "Wired Machine Link: Back face, 12 blocks of cable". A machine's link isn't synced, so the server
    // sends it.
    public enum Port implements StreamServerDataProvider<BlockAccessor, Port.Data> {
        INSTANCE;

        public record Data(int side, int distance) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::side, ByteBufCodecs.VAR_INT, Data::distance, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            Level level = accessor.getLevel();
            BlockPos pos = accessor.getPosition();
            BlockEntity blockEntity = accessor.getBlockEntity();
            LinkableMachine machine = null;
            if (blockEntity instanceof MachineBlockEntity own && !(own instanceof MultiblockController)) {
                machine = own;
            } else {
                MultiblockController controller = Multiblocks.controllerAt(level, pos);
                if (controller == null && blockEntity instanceof MultiblockController own) {
                    controller = own;
                }
                if (controller instanceof LinkableMachine structure && controller.isFormed()) {
                    machine = structure;
                }
            }
            MachineLink link = machine != null ? machine.getMachineLink() : null;
            if (machine == null || link == null) {
                return null;
            }
            Direction facing = MachineLinks.facing(machine);
            int distance = level.getBlockEntity(link.linkPos()) instanceof WiredMachineLinkBlockEntity wml ? wml.getDistance() : 0;
            return new Data(RelativeSide.fromDirection(facing, link.face()).ordinal(), distance);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return Data.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return uid("machine_link_port");
        }

        // Jade needs the tooltip half as its own provider, under the same uid.
        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                Port.INSTANCE.decodeFromData(accessor).ifPresent(data -> tooltip.add(Component.translatable("jade.arcforge.machine_link_port",
                        RelativeSide.byId(data.side()).getDescription(), data.distance()).withStyle(ChatFormatting.LIGHT_PURPLE)));
            }

            @Override
            public Identifier getUid() {
                return uid("machine_link_port");
            }
        }
    }
}
