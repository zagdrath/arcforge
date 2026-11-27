/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.HeliumGasTurbineBlockEntity;
import net.zagdrath.arcforge.client.renderer.ChunkMeshes;
import net.zagdrath.arcforge.client.renderer.LargeModels;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.steam.ModelMesh;
import net.zagdrath.arcforge.steam.HeliumTurbineLayout;
import net.zagdrath.arcforge.steam.HeliumTurbineMesh;
import net.zagdrath.arcforge.steam.TurbineLayout;

// Draws a formed Helium Gas Turbine as one closed-cycle turbine-generator set on its raised deck (its blocks draw nothing
// but their port plates), as NuclearSteamTurbineRenderer draws the Nuclear Steam Turbine: the compressor and its plenum,
// the precooler, the power turbines and their Hot Helium ducts, the recuperator and its crossovers and the generator, from
// its HeliumTurbineLayout, built by HeliumTurbineMesh on the sheet block/helium_gas_turbine/turbine, with a nozzle from the
// model out to each port. All of that is still: built once (and again when the ports change, checked every second) and
// baked into the chunk mesh (ChunkMeshes). This draws only what turns: the shaft, its couplings and the turning gear,
// built once at rest and drawn turned to the shaft's angle (see HeliumGasTurbineBlockEntity.advanceAngle). Past
// simpleMovingPartsFrom it's a few plain shapes standing still, past stillPartsOnlyFrom nothing (see LargeModels).
public class HeliumGasTurbineRenderer implements ShellMultiblockRenderer<HeliumGasTurbineBlockEntity, HeliumGasTurbineRenderer.State> {
    public static final Identifier SHEET = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/helium_gas_turbine/turbine");
    private static final int PORT_SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];

    private final Map<HeliumGasTurbineBlockEntity, Built> built = new WeakHashMap<>();

    private static final class Built {
        final ShellStructure.Shell shell;
        final Direction front;
        final HeliumTurbineMesh mesh;
        final float[] rotor, lod, axis;
        final AABB drawn;
        List<ModelMesh.Port> ports;
        float[] solid = EMPTY, cutout = EMPTY;
        long scanned;

        Built(ShellStructure.Shell shell, Direction front, List<ModelMesh.Port> ports) {
            this.shell = shell;
            this.front = front;
            this.mesh = new HeliumTurbineMesh(HeliumTurbineLayout.of(shell, front), shell.axis() == Direction.Axis.X);
            this.ports = ports;
            this.rotor = mesh.buildRotor();
            this.lod = mesh.buildRotorLod();
            this.axis = mesh.rotorAxis();
            this.drawn = MeshRendering.bounds(shell.min(), rotor, lod).inflate(0.5);
            rebuild();
        }

        void rebuild() {
            float[][] parts = mesh.buildStatic(ports);
            solid = parts[0];
            cutout = parts.length > 1 ? parts[1] : EMPTY;
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public float[] rotor = EMPTY;
        public @Nullable Matrix4f turn;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public HeliumGasTurbineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean bakes() {
        return true;
    }

    @Override
    public boolean bakesStill(HeliumGasTurbineBlockEntity turbine) {
        return ChunkMeshes.isBaked(turbine, MeshRendering.SOLID, MeshRendering.GLASS);
    }

    // Only what turns is drawn here; the rest is in the chunk mesh.
    @Override
    public AABB getRenderBoundingBox(HeliumGasTurbineBlockEntity turbine) {
        Built model = built.get(turbine);
        return model != null && model.shell.equals(turbine.getShell()) ? model.drawn : turbine.getRenderBox();
    }

    @Override
    public void extractRenderState(HeliumGasTurbineBlockEntity turbine, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        ShellMultiblockRenderer.super.extractRenderState(turbine, state, partialTicks, cameraPosition, breakProgress);
        ShellStructure.Shell shell = turbine.getShell();
        Level level = turbine.getLevel();
        state.formed = turbine.isMaster() && shell != null && level != null;
        state.rotor = EMPTY;
        state.turn = null;
        if (!state.formed || shell == null || level == null) {
            built.remove(turbine);
            ChunkMeshes.remove(turbine);
            return;
        }
        Direction front = turbine.getFacing();
        Built model = built.get(turbine);
        long time = level.getGameTime();
        if (model == null || !model.shell.equals(shell) || model.front != front) {
            model = new Built(shell, front, ports(level, shell));
            model.scanned = time;
            built.put(turbine, model);
        } else if (Math.abs(time - model.scanned) >= PORT_SCAN_INTERVAL) {
            model.scanned = time;
            List<ModelMesh.Port> ports = ports(level, shell);
            if (!ports.equals(model.ports)) {
                model.ports = ports;
                model.rebuild();
            }
        }
        TextureAtlasSprite sheet = SteamBoilerArrayRenderer.sprite(SHEET);
        int light = LightCoordsUtil.getLightCoords(level, shell.centre());
        state.sheet = sheet;
        state.light = light;
        ShellStructure.Shell builtShell = model.shell;
        java.util.function.BooleanSupplier stands = () -> turbine.isMaster() && builtShell.equals(turbine.getShell()) && turbine.getFacing() == front;
        MeshRendering.bake(turbine, MeshRendering.SOLID, shell.min(), ChunkSectionLayer.SOLID, model.solid, sheet, light, stands);
        MeshRendering.bake(turbine, MeshRendering.GLASS, shell.min(), ChunkSectionLayer.CUTOUT, model.cutout, sheet, light, stands);
        float angle = turbine.advanceAngle(time + partialTicks);
        switch (LargeModels.detail(model.drawn, cameraPosition)) {
            case FULL -> {
                state.rotor = model.rotor;
                state.turn = MeshRendering.turn(model.axis, angle);
            }
            case SIMPLE -> state.rotor = model.lod;
            case STILL -> {}
        }
    }

    // The ports on the shell's outer faces, in the turbine's frame (as SteamTurbineArrayRenderer finds them).
    private static List<ModelMesh.Port> ports(Level level, ShellStructure.Shell shell) {
        List<ModelMesh.Port> ports = new ArrayList<>();
        boolean alongX = shell.axis() == Direction.Axis.X;
        for (BlockPos pos : shell.positions()) {
            if (shell.boundaries(pos) == 0) {
                continue;
            }
            PortFaces faces = MultiblockPorts.faces(level, pos);
            if (faces.isEmpty()) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PortHolder holder) || !holder.holdsPorts(state)) {
                continue;
            }
            for (Direction face : Direction.values()) {
                if (faces.get(face) == SideMode.NONE || shell.contains(pos.relative(face))) {
                    continue;
                }
                int nx = alongX ? -face.getStepZ() : face.getStepX();
                int nu = alongX ? face.getStepX() : face.getStepZ();
                ports.add(new ModelMesh.Port(TurbineLayout.blockU(shell, pos), shell.offset(pos, Direction.Axis.Y), TurbineLayout.blockX(shell, pos),
                        nx, face.getStepY(), nu));
            }
        }
        return ports;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        ChunkMeshes.submitHeld(state, poseStack, collector);
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null || state.rotor.length == 0) {
            return;
        }
        var solid = RenderTypes.entitySolid(sheet.atlasLocation());
        Matrix4f turn = state.turn;
        if (turn != null) {
            MeshRendering.submitMoving(collector, poseStack, solid, state.rotor, sheet, state.light, turn);
        } else {
            MeshRendering.submitStill(collector, poseStack, solid, state.rotor, sheet, state.light);
        }
    }
}
