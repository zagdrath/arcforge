/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.api.gas.Gas;
import net.zagdrath.arcforge.api.gas.GasCapabilities;
import net.zagdrath.arcforge.api.gas.GasHandler;
import net.zagdrath.arcforge.api.gas.GasRegistry;
import net.zagdrath.arcforge.api.gas.GasTank;
import net.zagdrath.arcforge.api.gas.SteamGrade;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.GasTints;

// The gas API (docs/API.md), used only through its types and capabilities, as another mod would: every Arcforge gas
// in the registry with the colour the client draws it in, and inserting and extracting on a Pressurized Cylinder, two
// machines and the gas items, each by its own rules; and every machine a pipe can feed gas into has a gas handler.
public final class GasApiGameTests {
    private GasApiGameTests() {}

    private static Gas gas(Fluid fluid) {
        return GasRegistry.of(fluid).orElseThrow();
    }

    private static @Nullable GasHandler handler(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getCapability(GasCapabilities.BLOCK, helper.absolutePos(relative), null);
    }

    private static GasHandler require(GameTestHelper helper, BlockPos relative, String what) {
        GasHandler handler = handler(helper, relative);
        helper.assertTrue(handler != null, what + " provides no gas handler");
        return handler;
    }

    // Every Arcforge gas and the colour it's drawn in.
    private static Map<Fluid, Integer> arcforgeGases() {
        Map<Fluid, Integer> gases = new LinkedHashMap<>();
        for (net.zagdrath.arcforge.steam.SteamGrade grade : net.zagdrath.arcforge.steam.SteamGrade.values()) {
            gases.put(grade.fluid(), grade.tint());
        }
        gases.put(ModFluids.EXHAUST_STEAM.get(), GasTints.EXHAUST_STEAM);
        gases.put(ModFluids.HYDROGEN.get(), GasTints.HYDROGEN);
        gases.put(ModFluids.OXYGEN.get(), GasTints.OXYGEN);
        gases.put(ModFluids.CARBON_DIOXIDE.get(), GasTints.CARBON_DIOXIDE);
        gases.put(ModFluids.NITROGEN.get(), GasTints.NITROGEN);
        gases.put(ModFluids.AMMONIA.get(), GasTints.AMMONIA);
        gases.put(ModFluids.BIOGAS.get(), GasTints.BIOGAS);
        gases.put(ModFluids.CHLORINE.get(), GasTints.CHLORINE);
        gases.put(ModFluids.ETHYLENE.get(), GasTints.ETHYLENE);
        gases.put(ModFluids.SYNGAS.get(), GasTints.SYNGAS);
        return gases;
    }

    // The registry lists exactly the gases (Arcforge's among them, no liquids), sorted; a gas's id, name, colour,
    // temperature and steam grade are right; flowing forms and ids find the same gas; the codec round-trips.
    static void registry(GameTestHelper helper) {
        List<Gas> all = GasRegistry.all();
        Map<Fluid, Integer> expected = arcforgeGases();
        for (Map.Entry<Fluid, Integer> entry : expected.entrySet()) {
            Gas gas = gas(entry.getKey());
            helper.assertTrue(all.contains(gas), gas.id() + " isn't in the registry");
            helper.assertTrue(gas.color() == entry.getValue(),
                    gas.id() + " has colour " + Integer.toHexString(gas.color()) + ", drawn in " + Integer.toHexString(entry.getValue()));
            helper.assertTrue(gas.displayName().equals(entry.getKey().getFluidType().getDescription()), gas.id() + " is named " + gas.displayName());
        }
        // Every Arcforge gas is one we know the colour of: a new gas needs an arcforge:gas_properties entry.
        for (Gas gas : all) {
            helper.assertTrue(!gas.id().getNamespace().equals(Arcforge.MODID) || expected.containsKey(gas.fluid()),
                    gas.id() + " has no colour in this test (and maybe none in gas_properties.json)");
            helper.assertTrue(gas.fluid().isSource(gas.fluid().defaultFluidState()), gas.id() + " isn't a source fluid");
        }
        helper.assertTrue(all.stream().noneMatch(gas -> gas.fluid() == Fluids.WATER || gas.fluid() == Fluids.LAVA
                || gas.fluid() == ModFluids.CREOSOTE.get()), "A liquid is in the registry");
        for (int i = 1; i < all.size(); i++) {
            helper.assertTrue(all.get(i - 1).id().compareTo(all.get(i).id()) < 0, "Not sorted at " + all.get(i).id());
        }

        Gas hydrogen = gas(ModFluids.HYDROGEN.get());
        helper.assertTrue(hydrogen.id().equals(Identifier.fromNamespaceAndPath(Arcforge.MODID, "hydrogen")), "Hydrogen's id is " + hydrogen.id());
        helper.assertTrue(GasRegistry.get(hydrogen.id()).equals(Optional.of(hydrogen)), "Hydrogen isn't found by id");
        helper.assertTrue(new Gas(ModFluids.FLOWING_HYDROGEN.get()).equals(hydrogen), "Flowing hydrogen is another gas");
        helper.assertTrue(GasRegistry.get(Identifier.withDefaultNamespace("water")).isEmpty() && GasRegistry.of(Fluids.WATER).isEmpty(), "Water is a gas");
        boolean refused = false;
        try {
            new Gas(Fluids.WATER);
        } catch (IllegalArgumentException expectedError) {
            refused = true;
        }
        helper.assertTrue(refused, "Water made a Gas");

        helper.assertTrue(hydrogen.temperature() == 293 && hydrogen.steamGrade().isEmpty(), "Hydrogen at " + hydrogen.temperature() + " K, " + hydrogen.steamGrade());
        Gas superheated = gas(ModFluids.SUPERHEATED_STEAM.get());
        helper.assertTrue(superheated.temperature() == 1_173 && superheated.steamGrade().equals(Optional.of(SteamGrade.SUPERHEATED)),
                "Superheated Steam at " + superheated.temperature() + " K, " + superheated.steamGrade());
        helper.assertTrue(gas(ModFluids.EXHAUST_STEAM.get()).steamGrade().isEmpty(), "Exhaust Steam has a steam grade");
        for (SteamGrade grade : SteamGrade.values()) {
            helper.assertTrue(grade.gas().flatMap(Gas::steamGrade).equals(Optional.of(grade)), grade + " doesn't round-trip");
        }

        helper.assertTrue(Gas.CODEC.encodeStart(JsonOps.INSTANCE, hydrogen).getOrThrow().equals(new JsonPrimitive("arcforge:hydrogen")), "Hydrogen encodes wrongly");
        helper.assertTrue(Gas.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("arcforge:hydrogen")).getOrThrow().equals(hydrogen), "Hydrogen decodes wrongly");
        helper.assertTrue(Gas.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("minecraft:water")).isError(), "Water decoded as a gas");
        helper.succeed();
    }

    // A Pressurized Cylinder: one tank that takes and gives one gas at a time, at most its tier's rate per call;
    // simulating moves nothing, and a transfer inside an aborted transaction is undone. A Fluid Tank and an Arc
    // Crusher hold no gas, so they have no handler.
    static void cylinderTank(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0), liquidPos = new BlockPos(2, 1, 0), crusherPos = new BlockPos(4, 1, 0);
        ConduitTier tier = ConduitTier.WROUGHT;
        helper.setBlock(pos, ModBlocks.pressurizedCylinder(tier).get());
        helper.setBlock(liquidPos, ModBlocks.fluidTank(tier).get());
        helper.setBlock(crusherPos, ModBlocks.ARC_CRUSHER.get());
        PressurizedCylinderBlockEntity cylinder = helper.getBlockEntity(pos, PressurizedCylinderBlockEntity.class);
        GasHandler gases = require(helper, pos, "Pressurized Cylinder");
        helper.assertTrue(handler(helper, liquidPos) == null, "A Fluid Tank has a gas handler");
        helper.assertTrue(handler(helper, crusherPos) == null, "An Arc Crusher has a gas handler");
        Gas hydrogen = gas(ModFluids.HYDROGEN.get()), oxygen = gas(ModFluids.OXYGEN.get());
        int rate = tier.cylinderRate();

        helper.assertTrue(gases.tankCount() == 1, "The cylinder has " + gases.tankCount() + " tanks");
        GasTank empty = gases.tank(0);
        helper.assertTrue(empty.isEmpty() && empty.capacity() == tier.cylinderCapacity() && empty.canInsert() && empty.canExtract(), "Empty tank reads " + empty);
        helper.assertTrue(gases.tank(1) == GasTank.NONE && gases.tanks().equals(List.of(empty)), "Tanks read " + gases.tanks());
        helper.assertTrue(gases.accepts(0, hydrogen) && !gases.accepts(1, hydrogen), "Accepts hydrogen: " + gases.accepts(0, hydrogen));

        // Simulated: reported, not moved.
        helper.assertTrue(gases.insert(0, hydrogen, rate, true) == rate && cylinder.getTank().getAmount() == 0, "A simulated fill moved gas");
        // One call moves at most the rate.
        helper.assertTrue(gases.insert(hydrogen, rate * 3, false) == rate, "Filled more than the rate");
        GasTank filled = gases.tank(0);
        helper.assertTrue(filled.gas().equals(Optional.of(hydrogen)) && filled.amount() == rate && cylinder.getTank().getAmount() == rate,
                "Filled tank reads " + filled);
        // One gas at a time.
        helper.assertTrue(gases.insert(0, oxygen, 100, false) == 0, "Oxygen went in with the hydrogen");
        helper.assertTrue(gases.extract(0, oxygen, 100, false) == 0 && gases.extract(oxygen, 100, false) == 0, "Oxygen came out of a hydrogen tank");
        // Out again.
        helper.assertTrue(gases.extract(0, hydrogen, 50, true) == 50 && cylinder.getTank().getAmount() == rate, "A simulated drain moved gas");
        helper.assertTrue(gases.extract(hydrogen, 50, false) == 50 && cylinder.getTank().getAmount() == rate - 50, "Drained to " + cylinder.getTank().getAmount());
        // Inside a transaction that's aborted: undone.
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(gases.extract(0, hydrogen, 100, false) == 100 && cylinder.getTank().getAmount() == rate - 150, "Drain in a transaction");
        }
        helper.assertTrue(cylinder.getTank().getAmount() == rate - 50, "An aborted transaction kept the drain: " + cylinder.getTank().getAmount());
        helper.succeed();
    }

    // A Haber Reactor: its two input tanks take only the gases it synthesises from, one gas to a tank, and never give
    // them back; its output tank gives Ammonia and takes nothing. An Electrolyzer lists only its gas tanks, not its
    // water or liquid tanks.
    static void machineTanks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0), electrolyzerPos = new BlockPos(2, 1, 0);
        helper.setBlock(pos, ModBlocks.HABER_REACTOR.get());
        helper.setBlock(electrolyzerPos, ModBlocks.ELECTROLYZER.get());
        HaberReactorBlockEntity reactor = helper.getBlockEntity(pos, HaberReactorBlockEntity.class);
        GasHandler gases = require(helper, pos, "Haber Reactor");
        Gas hydrogen = gas(ModFluids.HYDROGEN.get()), nitrogen = gas(ModFluids.NITROGEN.get()), oxygen = gas(ModFluids.OXYGEN.get()),
                ammonia = gas(ModFluids.AMMONIA.get());

        helper.assertTrue(gases.tankCount() == 3, "The Haber Reactor has " + gases.tankCount() + " gas tanks");
        List<GasTank> tanks = gases.tanks();
        helper.assertTrue(tanks.get(0).canInsert() && !tanks.get(0).canExtract() && tanks.get(1).canInsert() && !tanks.get(1).canExtract()
                && !tanks.get(2).canInsert() && tanks.get(2).canExtract(), "Tank roles " + tanks);
        helper.assertTrue(tanks.get(0).capacity() == reactor.getInputA().getCapacity() && tanks.get(2).capacity() == reactor.getOutputTank().getCapacity(),
                "Capacities " + tanks);
        helper.assertTrue(gases.accepts(0, hydrogen) && !gases.accepts(0, oxygen) && !gases.accepts(2, ammonia), "Accepts the wrong gases");

        // Into an input tank; then that gas stays out of the other one.
        helper.assertTrue(gases.insert(0, hydrogen, 500, true) == 500 && reactor.getInputA().getAmount() == 0, "A simulated fill moved gas");
        helper.assertTrue(gases.insert(0, hydrogen, 500, false) == 500 && reactor.getInputA().contains(ModFluids.HYDROGEN.get()), "Hydrogen refused");
        helper.assertTrue(gases.insert(1, hydrogen, 100, false) == 0, "Hydrogen went into both input tanks");
        // Without a tank, a gas goes where the machine routes it.
        helper.assertTrue(gases.insert(nitrogen, 300, false) == 300 && reactor.getInputB().contains(ModFluids.NITROGEN.get())
                && reactor.getInputB().getAmount() == 300, "Nitrogen went to " + reactor.getInputB().getResource(0));
        // Nothing it doesn't use, nothing into the output, nothing back out of an input.
        helper.assertTrue(gases.insert(oxygen, 100, false) == 0, "The reactor took oxygen");
        helper.assertTrue(gases.insert(2, ammonia, 100, false) == 0 && reactor.getOutputTank().getAmount() == 0, "The output tank took ammonia");
        helper.assertTrue(gases.extract(0, hydrogen, 100, false) == 0 && gases.extract(hydrogen, 100, false) == 0
                && reactor.getInputA().getAmount() == 500, "Hydrogen came back out of an input");
        // Ammonia comes out of the output.
        reactor.getOutputTank().set(0, FluidResource.of(ModFluids.AMMONIA.get()), 400);
        helper.assertTrue(gases.tank(2).gas().equals(Optional.of(ammonia)) && gases.tank(2).amount() == 400, "Output reads " + gases.tank(2));
        helper.assertTrue(gases.extract(2, ammonia, 100, true) == 100 && reactor.getOutputTank().getAmount() == 400, "A simulated drain moved gas");
        helper.assertTrue(gases.extract(ammonia, 1_000, false) == 400 && reactor.getOutputTank().getAmount() == 0, "Drained " + reactor.getOutputTank().getAmount());

        // The Electrolyzer: its hydrogen and oxygen tanks only, both giving and neither taking.
        ElectrolyzerBlockEntity electrolyzer = helper.getBlockEntity(electrolyzerPos, ElectrolyzerBlockEntity.class);
        GasHandler split = require(helper, electrolyzerPos, "Electrolyzer");
        helper.assertTrue(split.tankCount() == 2, "The Electrolyzer has " + split.tankCount() + " gas tanks");
        helper.assertTrue(split.tanks().stream().allMatch(tank -> tank.canExtract() && !tank.canInsert()), "Electrolyzer roles " + split.tanks());
        helper.assertTrue(split.insert(hydrogen, 100, false) == 0, "The Electrolyzer took hydrogen");
        electrolyzer.getOxygen().set(0, FluidResource.of(ModFluids.OXYGEN.get()), 250);
        helper.assertTrue(split.extract(oxygen, 1_000, false) == 250 && electrolyzer.getOxygen().getAmount() == 0, "Drained oxygen to " + electrolyzer.getOxygen().getAmount());
        helper.succeed();
    }

    // Every single-block machine that a pipe can feed a gas into, on any face, has a gas handler, and every gas handler
    // has a tank. Machines that only make liquids have none, even where an output tank's filter takes anything.
    static void machineCoverage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(pos);
        List<Gas> all = GasRegistry.all();
        List<String> problems = new ArrayList<>();
        List<String> covered = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!Arcforge.MODID.equals(id.getNamespace()) || !(block instanceof EntityBlock entityBlock)
                    || !(entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof MachineBlockEntity)
                    || entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof MultiblockController) {
                continue;
            }
            helper.setBlock(pos, block);
            GasHandler gases = handler(helper, pos);
            boolean takesGas = false;
            for (Direction side : Direction.values()) {
                ResourceHandler<FluidResource> fluids = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, absolute, side);
                for (int index = 0; fluids != null && index < fluids.size(); index++) {
                    for (Gas gas : all) {
                        takesGas |= fluids.isValid(index, gas.toResource());
                    }
                }
            }
            if (takesGas && gases == null) {
                problems.add(id + ": takes gas from a pipe but has no gas handler");
            }
            if (gases != null) {
                covered.add(id.getPath());
                if (gases.tankCount() == 0) {
                    problems.add(id + ": a gas handler with no tanks");
                }
            }
            helper.setBlock(pos, Blocks.AIR);
        }
        helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
        helper.assertTrue(covered.containsAll(List.of("electrolyzer", "haber_reactor", "fermenter", "hydroponic_cell", "gasifier")), "Gas handlers on " + covered);
        // Their output tanks take any fluid, but only ever hold liquids.
        helper.assertTrue(!covered.contains("electric_pump") && !covered.contains("arc_melter") && !covered.contains("oil_press"), "Gas handlers on " + covered);
        helper.succeed();
    }

    // A Gas Cartridge fills and empties through its item capability, at its own rate, written back to the stack. A
    // Jetpack takes only jetpack fuels. A Canister holds liquids, so it has no gas handler.
    static void items(GameTestHelper helper) {
        PortableStorageItem cartridgeItem = ModItems.portable(PortableStorageItem.Kind.GAS_CARTRIDGE, ConduitTier.TEMPERED);
        ItemStack cartridge = new ItemStack(cartridgeItem);
        GasHandler gases = ItemAccess.forStack(cartridge).getCapability(GasCapabilities.ITEM);
        helper.assertTrue(gases != null, "A Gas Cartridge has no gas handler");
        Gas hydrogen = gas(ModFluids.HYDROGEN.get()), oxygen = gas(ModFluids.OXYGEN.get());
        int rate = cartridgeItem.rate();

        helper.assertTrue(gases.tankCount() == 1 && gases.tank(0).capacity() == cartridgeItem.capacity() && gases.tank(0).isEmpty(), "Cartridge reads " + gases.tank(0));
        helper.assertTrue(gases.insert(hydrogen, rate, true) == rate && cartridgeItem.amount(cartridge) == 0, "A simulated fill changed the cartridge");
        helper.assertTrue(gases.insert(hydrogen, rate * 2, false) == rate && cartridgeItem.amount(cartridge) == rate, "The cartridge holds " + cartridgeItem.amount(cartridge));
        helper.assertTrue(gases.insert(oxygen, 100, false) == 0, "Oxygen went in with the hydrogen");
        // A fresh handler reads the stack's new contents.
        GasHandler again = ItemAccess.forStack(cartridge).getCapability(GasCapabilities.ITEM);
        helper.assertTrue(again != null && again.tank(0).gas().equals(Optional.of(hydrogen)) && again.tank(0).amount() == rate, "Re-read " + (again == null ? null : again.tank(0)));
        helper.assertTrue(again.extract(0, hydrogen, 100, false) == 100 && cartridgeItem.amount(cartridge) == rate - 100, "Emptied to " + cartridgeItem.amount(cartridge));

        ItemStack jetpack = new ItemStack(ModItems.TEMPERED_JETPACK.get());
        GasHandler fuel = ItemAccess.forStack(jetpack).getCapability(GasCapabilities.ITEM);
        helper.assertTrue(fuel != null && fuel.accepts(0, hydrogen) && !fuel.accepts(0, oxygen), "A Jetpack's gas handler is wrong");
        helper.assertTrue(fuel.insert(oxygen, 100, false) == 0 && fuel.insert(hydrogen, 100, false) == 100, "The Jetpack took oxygen or refused hydrogen");
        FluidStack held = jetpack.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        helper.assertTrue(held.is(ModFluids.HYDROGEN.get()) && held.getAmount() == 100, "The Jetpack holds " + held);

        ItemStack canister = new ItemStack(ModItems.portable(PortableStorageItem.Kind.CANISTER, ConduitTier.TEMPERED));
        helper.assertTrue(ItemAccess.forStack(canister).getCapability(GasCapabilities.ITEM) == null, "A Canister has a gas handler");
        helper.succeed();
    }
}
