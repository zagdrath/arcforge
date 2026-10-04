/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.arcforge.block.logistics.QuantumTunnelBlock;
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.common.MenuReach;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.network.QuantumFrequencyPayload;
import net.zagdrath.arcforge.network.QuantumStatePayload;
import net.zagdrath.arcforge.quantum.Frequency;
import net.zagdrath.arcforge.quantum.QuantumFaces;
import net.zagdrath.arcforge.quantum.QuantumFrequencies;
import net.zagdrath.arcforge.quantum.QuantumResource;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.security.SecuredMenu;
import net.zagdrath.arcforge.security.SecurityRules;

// A Quantum Tunnel's screen: no slots. The buffers and the face settings come through the container data; the
// frequency list (and which one the tunnel is on) through QuantumStatePayload, sent when the menu opens, after every
// change and once a second while it's open. Buttons: FACE_BUTTON + face * 5 + resource cycles that setting; the
// security buttons are MachineMenuButtons'. Picking, creating and deleting frequencies are QuantumFrequencyPayloads.
public class QuantumTunnelMenu extends AbstractContainerMenu implements SecuredMenu {
    public static final int DATA_LINKED = 0;
    public static final int DATA_ENERGY = 1, DATA_ENERGY_CAPACITY = 2;
    public static final int DATA_HEAT = 3, DATA_HEAT_CAPACITY = 4, DATA_TEMPERATURE = 5;
    public static final int DATA_FLUID = 6, DATA_FLUID_CAPACITY = 7, DATA_FLUID_ID = 8;
    public static final int DATA_GAS = 9, DATA_GAS_CAPACITY = 10, DATA_GAS_ID = 11;
    public static final int DATA_ITEMS = 12, DATA_ITEM_SLOTS = 13;
    public static final int DATA_FACES_LOW = 14, DATA_FACES_HIGH = 15;
    // Items held across the slots, and what they could hold (each slot a stack of what's in it; 64 when empty).
    public static final int DATA_ITEM_COUNT = 16, DATA_ITEM_CAPACITY = 17;
    public static final int DATA_VALUES = 18;

    public static final int FACE_BUTTON = 0;
    private static final int STATE_INTERVAL = 20;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Block block;
    private int stateTimer;
    private @Nullable QuantumStatePayload lastSent;

    // Client constructor, called with the tunnel's position written by the server.
    public QuantumTunnelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), WideIntContainerData.client(DATA_VALUES));
    }

    public QuantumTunnelMenu(int containerId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(ModMenuTypes.QUANTUM_TUNNEL.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = data;
        Block here = inventory.player.level().getBlockState(pos).getBlock();
        this.block = here instanceof QuantumTunnelBlock ? here : Blocks.AIR;
        addDataSlots(data);
    }

    public BlockPos getPos() {
        return pos;
    }

    public int value(int index) {
        return WideIntContainerData.read(data, index);
    }

    public boolean isLinked() {
        return value(DATA_LINKED) != 0;
    }

    public long getFaces() {
        return QuantumFaces.join(value(DATA_FACES_LOW), value(DATA_FACES_HIGH));
    }

    public int mode(Direction face, QuantumResource resource) {
        return QuantumFaces.get(getFaces(), face, resource);
    }

    public static int faceButton(Direction face, QuantumResource resource) {
        return FACE_BUTTON + face.get3DDataValue() * QuantumResource.values().length + resource.ordinal();
    }

    public @Nullable QuantumTunnelBlockEntity tunnel(Player player) {
        return player.level().getBlockEntity(pos) instanceof QuantumTunnelBlockEntity tunnel ? tunnel : null;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        int faceButtons = Direction.values().length * QuantumResource.values().length;
        if (buttonId >= FACE_BUTTON && buttonId < FACE_BUTTON + faceButtons) {
            QuantumTunnelBlockEntity tunnel = tunnel(player);
            if (tunnel != null) {
                int index = buttonId - FACE_BUTTON;
                tunnel.cycleMode(Direction.from3DDataValue(index / QuantumResource.values().length),
                        QuantumResource.values()[index % QuantumResource.values().length]);
            }
            return true;
        }
        return MachineMenuButtons.handle(access, player, buttonId);
    }

    // --- The frequency list ---

    // Applies a frequency request from this menu's player; returns the error key ("" if it worked).
    public String apply(ServerPlayer player, QuantumFrequencyPayload request) {
        QuantumTunnelBlockEntity tunnel = tunnel(player);
        MinecraftServer server = player.level().getServer();
        if (tunnel == null || server == null) {
            return "";
        }
        QuantumFrequencies frequencies = QuantumFrequencies.get(server);
        String name = request.name().trim();
        switch (request.action()) {
            case CLEAR -> tunnel.setKey(null);
            case SELECT -> {
                Frequency frequency = frequencies.get(new Frequency.Key(name, request.owner()));
                if (frequency == null || !frequency.canUse(player.getUUID(), server)) {
                    return "gui.arcforge.quantum_tunnel.error.not_found";
                }
                tunnel.setKey(frequency.key());
            }
            case CREATE -> {
                if (name.isEmpty() || name.length() > Frequency.MAX_NAME_LENGTH) {
                    return "gui.arcforge.quantum_tunnel.error.name";
                }
                Frequency.Key key = request.owner().isPresent() ? Frequency.Key.ofPrivate(name, player.getUUID()) : Frequency.Key.ofPublic(name);
                Frequency existing = frequencies.get(key);
                if (existing == null) {
                    if (frequencies.countCreatedBy(player.getUUID()) >= ArcforgeConfig.QUANTUM_FREQUENCIES_PER_PLAYER.getAsInt()) {
                        return "gui.arcforge.quantum_tunnel.error.limit";
                    }
                    existing = frequencies.create(key, player.getUUID(), player.getGameProfile().name());
                } else if (!existing.canUse(player.getUUID(), server)) {
                    return "gui.arcforge.quantum_tunnel.error.not_found";
                }
                tunnel.setKey(existing.key());
            }
            case DELETE -> {
                Frequency.Key key = new Frequency.Key(name, request.owner());
                Frequency frequency = frequencies.get(key);
                if (frequency == null) {
                    return "gui.arcforge.quantum_tunnel.error.not_found";
                }
                if (!frequency.creator().equals(player.getUUID()) && !SecurityRules.isOp(player)) {
                    return "gui.arcforge.quantum_tunnel.error.not_yours";
                }
                frequencies.remove(key);
            }
        }
        return "";
    }

    // The player's view of the frequencies: those they may use, and the one the tunnel is on.
    public QuantumStatePayload state(ServerPlayer player, String error) {
        QuantumTunnelBlockEntity tunnel = tunnel(player);
        MinecraftServer server = player.level().getServer();
        List<QuantumStatePayload.Entry> entries = new ArrayList<>();
        Optional<QuantumStatePayload.Entry> current = Optional.empty();
        if (server != null) {
            QuantumFrequencies frequencies = QuantumFrequencies.get(server);
            for (Frequency frequency : frequencies.usableBy(player.getUUID(), server)) {
                entries.add(QuantumStatePayload.Entry.of(frequency, player));
            }
            if (tunnel != null && tunnel.getKey().isPresent()) {
                Frequency frequency = frequencies.get(tunnel.getKey().get());
                if (frequency != null) {
                    current = Optional.of(QuantumStatePayload.Entry.of(frequency, player));
                }
            }
        }
        return new QuantumStatePayload(containerId, current, entries, error);
    }

    public void sendState(ServerPlayer player, String error) {
        QuantumStatePayload payload = state(player, error);
        lastSent = payload;
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // Keeps the open screen's list current (other players add and delete frequencies).
    public void tickState(ServerPlayer player) {
        if (++stateTimer < STATE_INTERVAL) {
            return;
        }
        stateTimer = 0;
        QuantumStatePayload payload = state(player, "");
        if (lastSent == null || !payload.sameAs(lastSent)) {
            lastSent = payload;
            if (player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @Override
    public ContainerLevelAccess securityAccess() {
        return access;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return MenuReach.stillValid(access, player, block);
    }
}
