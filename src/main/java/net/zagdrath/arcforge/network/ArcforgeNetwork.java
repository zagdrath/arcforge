/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// Arcforge's own packets: the Wrench's mode change, the port lists of open multiblock menus, and JEI setting an
// Assembler's pattern.
public final class ArcforgeNetwork {
    private static final String VERSION = "1";

    private ArcforgeNetwork() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ArcforgeNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(WrenchModePayload.TYPE, WrenchModePayload.STREAM_CODEC, WrenchModePayload::handle);
        registrar.playToClient(PortsPayload.TYPE, PortsPayload.STREAM_CODEC, PortsPayload::handle);
        registrar.playToServer(AssemblerPatternPayload.TYPE, AssemblerPatternPayload.STREAM_CODEC, AssemblerPatternPayload::handle);
    }
}
