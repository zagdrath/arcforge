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
        registrar.playToServer(ArcQuarryAreaPayload.TYPE, ArcQuarryAreaPayload.STREAM_CODEC, ArcQuarryAreaPayload::handle);
        registrar.playToServer(ArcQuarryTagPayload.TYPE, ArcQuarryTagPayload.STREAM_CODEC, ArcQuarryTagPayload::handle);
        registrar.playToServer(JetpackInputPayload.TYPE, JetpackInputPayload.STREAM_CODEC, JetpackInputPayload::handle);
        registrar.playToServer(JetpackModePayload.TYPE, JetpackModePayload.STREAM_CODEC, JetpackModePayload::handle);
        registrar.playToClient(JetpackStatePayload.TYPE, JetpackStatePayload.STREAM_CODEC, JetpackStatePayload::handle);
        registrar.playToServer(ToolModuleScrollPayload.TYPE, ToolModuleScrollPayload.STREAM_CODEC, ToolModuleScrollPayload::handle);
        registrar.playToClient(SecuritySyncPayload.TYPE, SecuritySyncPayload.STREAM_CODEC, SecuritySyncPayload::handle);
        registrar.playToClient(SecurityProfilePayload.TYPE, SecurityProfilePayload.STREAM_CODEC, SecurityProfilePayload::handle);
        registrar.playToServer(SecurityEditPayload.TYPE, SecurityEditPayload.STREAM_CODEC, SecurityEditPayload::handle);
        registrar.playToServer(ThrottleLeverPayload.TYPE, ThrottleLeverPayload.STREAM_CODEC, ThrottleLeverPayload::handle);
        registrar.playToServer(QuantumFrequencyPayload.TYPE, QuantumFrequencyPayload.STREAM_CODEC, QuantumFrequencyPayload::handle);
        registrar.playToClient(QuantumStatePayload.TYPE, QuantumStatePayload.STREAM_CODEC, QuantumStatePayload::handle);
    }
}
