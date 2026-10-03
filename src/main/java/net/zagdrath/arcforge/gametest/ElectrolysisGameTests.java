/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.heat.EnergyBalance;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.recipe.ElectrolyzingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Electrolyzer, hydrogen as a (net-negative) fuel, oxygen in the Arcforge Furnace and Plastic Sheets.
public final class ElectrolysisGameTests {
    private static final BlockPos POS = new BlockPos(0, 1, 0);

    private ElectrolysisGameTests() {}

    private static ElectrolyzerBlockEntity electrolyzer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ELECTROLYZER.get());
        return helper.getBlockEntity(pos, ElectrolyzerBlockEntity.class);
    }

    private static int fill(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static ElectrolyzingRecipe waterRecipe(GameTestHelper helper) {
        var recipe = MachineRecipes.electrolyzing(helper.getLevel(), FluidResource.of(Fluids.WATER)).orElse(null);
        helper.assertTrue(recipe != null, "No electrolyzing recipe for water");
        return recipe.value();
    }

    // Keeps the buffer full every tick and counts what it took, so used() is the FE the machine drew.
    private static final class Meter {
        private final ElectrolyzerBlockEntity machine;
        private final int start;
        private long added;

        Meter(ElectrolyzerBlockEntity machine) {
            this.machine = machine;
            CrushingGameTests.charge(machine.getEnergy(), machine.getEnergy().getCapacityAsInt());
            this.start = machine.getEnergy().getAmountAsInt();
        }

        void topUp() {
            int before = machine.getEnergy().getAmountAsInt();
            CrushingGameTests.charge(machine.getEnergy(), machine.getEnergy().getCapacityAsInt());
            added += machine.getEnergy().getAmountAsInt() - before;
        }

        long used() {
            return start + added - machine.getEnergy().getAmountAsInt();
        }
    }

    // 1,000 mB of water becomes 2,000 mB of hydrogen and 1,000 mB of oxygen, with nothing lost.
    static void ratio(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = electrolyzer(helper, POS);
        CrushingGameTests.install(machine.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
        helper.assertTrue(fill(machine.getFluidHandler(Direction.UP), Fluids.WATER, 1_000) == 1_000, "Top face refused water");
        Meter meter = new Meter(machine);
        helper.onEachTick(meter::topUp);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.getWater().getAmount() == 0 && machine.getStatus() == MachineStatus.IDLE,
                        "Still " + machine.getWater().getAmount() + " mB of water"))
                .thenExecute(() -> {
                    helper.assertTrue(machine.getHydrogen().contains(ModFluids.HYDROGEN.get()) && machine.getHydrogen().getAmount() == 2_000,
                            "Hydrogen tank holds " + machine.getHydrogen().getAmount() + " mB");
                    helper.assertTrue(machine.getOxygen().contains(ModFluids.OXYGEN.get()) && machine.getOxygen().getAmount() == 1_000,
                            "Oxygen tank holds " + machine.getOxygen().getAmount() + " mB");
                    helper.assertTrue(meter.used() == 10L * machine.costFor(waterRecipe(helper)), "10 operations used " + meter.used() + " FE");
                })
                .thenSucceed();
    }

    // A full oxygen tank stops it; with oxygen venting on it carries on, filling hydrogen and releasing the oxygen that
    // doesn't fit.
    static void ventKeepsSplitting(GameTestHelper helper) {
        ElectrolyzerBlockEntity blocked = electrolyzer(helper, new BlockPos(0, 1, 0));
        ElectrolyzerBlockEntity vented = electrolyzer(helper, new BlockPos(2, 1, 0));
        for (ElectrolyzerBlockEntity machine : List.of(blocked, vented)) {
            CrushingGameTests.install(machine.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
            fill(machine.getFluidHandler(Direction.UP), Fluids.WATER, 300);
            machine.getOxygen().set(0, FluidResource.of(ModFluids.OXYGEN.get()), machine.getOxygen().getCapacity());
        }
        vented.setVenting(false, true);
        List<Meter> meters = List.of(new Meter(blocked), new Meter(vented));
        helper.onEachTick(() -> meters.forEach(Meter::topUp));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vented.getWater().getAmount() == 0, "The venting Electrolyzer still has water"))
                .thenExecute(() -> {
                    helper.assertTrue(vented.getHydrogen().getAmount() == 600, "Vented hydrogen: " + vented.getHydrogen().getAmount() + " mB, not 600");
                    helper.assertTrue(vented.getOxygen().getAmount() == vented.getOxygen().getCapacity(), "The oxygen tank overflowed");
                    helper.assertTrue(blocked.getStatus() == MachineStatus.OUTPUT_FULL && blocked.getWater().getAmount() == 300,
                            "Without venting it is " + blocked.getStatus() + " with " + blocked.getWater().getAmount() + " mB of water");
                })
                .thenSucceed();
    }

    // One operation (100 mB of water) uses exactly its cost: the balance floor of 141,600 FE (its 200 mB of hydrogen can
    // give back 113,280 FE through a Steam Turbine Array, times the 1.25 safety factor), which is above the recipe's
    // 120,000, so Energy upgrades can't take it lower.
    static void energyExact(GameTestHelper helper) {
        int[] energyUpgrades = { 0, 1, 8 };
        int[] expected = { 141_600, 141_600, 141_600 };
        List<ElectrolyzerBlockEntity> machines = new ArrayList<>();
        List<Meter> meters = new ArrayList<>();
        for (int i = 0; i < energyUpgrades.length; i++) {
            ElectrolyzerBlockEntity machine = electrolyzer(helper, new BlockPos(i * 2, 1, 0));
            CrushingGameTests.install(machine.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
            if (energyUpgrades[i] > 0) {
                CrushingGameTests.install(machine.getItems(), ModItems.ENERGY_UPGRADE.get(), energyUpgrades[i]);
            }
            fill(machine.getFluidHandler(Direction.UP), Fluids.WATER, 100);
            machines.add(machine);
            meters.add(new Meter(machine));
        }
        ElectrolyzingRecipe recipe = waterRecipe(helper);
        helper.assertTrue(EnergyBalance.minEnergyFor(recipe) == 141_600, "The floor is " + EnergyBalance.minEnergyFor(recipe) + " FE, not 141,600");
        helper.onEachTick(() -> meters.forEach(Meter::topUp));
        helper.startSequence()
                .thenWaitUntil(() -> machines.forEach(machine -> helper.assertTrue(machine.getOxygen().getAmount() == 100, "Not split yet")))
                .thenIdle(3)
                .thenExecute(() -> {
                    for (int i = 0; i < machines.size(); i++) {
                        helper.assertTrue(machines.get(i).costFor(recipe) == expected[i],
                                energyUpgrades[i] + " Energy upgrades cost " + machines.get(i).costFor(recipe) + " FE, not " + expected[i]);
                        helper.assertTrue(meters.get(i).used() == expected[i],
                                energyUpgrades[i] + " Energy upgrades used " + meters.get(i).used() + " FE, not " + expected[i]);
                    }
                })
                .thenSucceed();
    }

    // Hydrogen can never make FE: at every Energy-upgrade count the cost beats the best recovery by the safety
    // factor, and bestFePerHu() is at least every heat -> FE route worked out here on its own (and the fuel -> FE
    // Gas Turbine Array route is counted too). If this fails after adding a route, add that route to
    // EnergyBalance.bestFePerHu().
    static void hydrogenNetNegative(GameTestHelper helper) {
        double lubricant = ArcforgeConfig.LUBRICANT_OUTPUT_BONUS.getAsDouble();
        double vacuum = ArcforgeConfig.TURBINE_ARRAY_VACUUM_BONUS.getAsDouble();
        double boilerCost = ArcforgeConfig.BOILER_ARRAY_HEAT_COST.getAsDouble();
        double superheaterCost = ArcforgeConfig.SUPERHEATER_HEAT_COST.getAsDouble();
        List<Double> routes = new ArrayList<>();
        routes.add(ArcforgeConfig.THERMOELECTRIC_BONUS_EFFICIENCY.getAsDouble()
                * (1.0 + ArcforgeConfig.THERMOELECTRIC_UPGRADE_EFFICIENCY.getAsDouble() * UpgradeType.MAX_PER_MACHINE));
        for (SteamGrade grade : SteamGrade.values()) {
            routes.add(grade.arrayFePerMb() * (1.0 + lubricant + vacuum) / (grade.huPerMb() * boilerCost));
            for (SteamGrade better : SteamGrade.values()) {
                if (better.ordinal() > grade.ordinal()) {
                    routes.add(better.arrayFePerMb() * (1.0 + lubricant + vacuum)
                            / (grade.huPerMb() * boilerCost + (better.huPerMb() - grade.huPerMb()) * superheaterCost));
                }
            }
        }
        double best = EnergyBalance.bestFePerHu();
        for (double route : routes) {
            helper.assertTrue(best >= route - 1e-9, "A route gives " + route + " FE/HU, more than bestFePerHu() " + best);
        }
        // Boiling Steam (8 HU) and superheating it (6 HU) into 66.08 FE: 4.72 FE per HU.
        helper.assertTrue(Math.abs(best - 4.72) < 0.001, "bestFePerHu() is " + best + ", not 4.72");
        helper.assertTrue(Math.abs(EnergyBalance.thermoelectricFePerHu() - 1.725) < 0.001, "Thermoelectric route is " + EnergyBalance.thermoelectricFePerHu());
        double hydrogen = EnergyBalance.recoverableFePerMb(ModFluids.HYDROGEN.get());
        helper.assertTrue(Math.abs(hydrogen - 60 * 2.0 * best) < 1e-6, "Hydrogen gives back " + hydrogen + " FE/mB");
        // The Gas Turbine Array burns it straight to FE (1.5 x 1.08 lubricated, plus a quarter of the heat as
        // exhaust raising steam: 2.80 FE per HU), under the upgraded burner route (9.44), so the floor stays the
        // burner's. recoverableFePerMb takes the larger of the two if the turbine is ever tuned past it.
        double turbine = 60 * EnergyBalance.gasTurbineFePerHu(1_400);
        helper.assertTrue(Math.abs(turbine / 60 - (1.5 * 1.08 + 0.25 * best)) < 1e-6, "The Gas Turbine route is " + turbine / 60 + " FE/HU");
        helper.assertTrue(hydrogen >= turbine, "Hydrogen gives back " + hydrogen + " FE/mB, less than the Gas Turbine's " + turbine);
        helper.assertTrue(EnergyBalance.recoverableFePerMb(ModFluids.OXYGEN.get()) == 0.0, "Oxygen burns");

        ElectrolyzingRecipe recipe = waterRecipe(helper);
        double factor = ArcforgeConfig.ELECTROLYZER_BALANCE_SAFETY_FACTOR.getAsDouble();
        double recoverable = recipe.primary().amount() * hydrogen;
        for (int n = 0; n <= UpgradeType.MAX_PER_MACHINE; n++) {
            ElectrolyzerBlockEntity machine = electrolyzer(helper, new BlockPos(n, 1, 0));
            if (n > 0) {
                CrushingGameTests.install(machine.getItems(), ModItems.ENERGY_UPGRADE.get(), n);
            }
            int cost = machine.costFor(recipe);
            helper.assertTrue(cost >= factor * recoverable - 1e-6, n + " Energy upgrades: " + cost + " FE for " + recoverable + " FE back");
        }
        helper.succeed();
    }

    // Hydrogen burns at 1 mB/t for 60 HU/t (120 with 8 Heat upgrades), and as hot as 1,400°C.
    static void hydrogenBurnsInFuelBurner(GameTestHelper helper) {
        BlockPos plainPos = new BlockPos(0, 1, 0);
        BlockPos upgradedPos = new BlockPos(3, 1, 0);
        helper.setBlock(plainPos, ModBlocks.FUEL_BURNER.get());
        helper.setBlock(upgradedPos, ModBlocks.FUEL_BURNER.get());
        FuelBurnerBlockEntity plain = helper.getBlockEntity(plainPos, FuelBurnerBlockEntity.class);
        FuelBurnerBlockEntity upgraded = helper.getBlockEntity(upgradedPos, FuelBurnerBlockEntity.class);
        CrushingGameTests.install(upgraded.getItems(), ModItems.HEAT_UPGRADE.get(), 8);
        helper.assertTrue(fill(plain.getInteractionFluidHandler(), ModFluids.HYDROGEN.get(), 1_000) == 1_000, "Burner refused hydrogen");
        fill(upgraded.getInteractionFluidHandler(), ModFluids.HYDROGEN.get(), 1_000);
        BurnerFuel fuel = BurnerFuel.of(ModFluids.HYDROGEN.get());
        helper.assertTrue(fuel != null && fuel.burnTemperature(ArcforgeConfig.FUEL_BURNER_MAX_TEMPERATURE.getAsInt()) == 1_400,
                "Hydrogen doesn't burn at 1,400°C");
        helper.assertTrue(plain.getHeat().getMaxCelsius() == 1_400, "The burner tops out at " + plain.getHeat().getMaxCelsius() + "°C");
        helper.startSequence()
                .thenIdle(21)
                .thenExecute(() -> {
                    helper.assertTrue(plain.getHeatPerTick() == 60, "Burner makes " + plain.getHeatPerTick() + " HU/t from hydrogen");
                    helper.assertTrue(upgraded.getHeatPerTick() == 120, "Upgraded burner makes " + upgraded.getHeatPerTick() + " HU/t");
                    int used = 1_000 - plain.getTank().getAmount();
                    helper.assertTrue(used >= 19 && used <= 23, "Burner used " + used + " mB in ~21 ticks");
                })
                .thenSucceed();
    }

    // Electrolyzer Hydrogen face -> Pressurized Conduit -> Fuel Burner input face.
    static void gasConduitFeedsBurner(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = electrolyzer(helper, POS);
        Direction out = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (machine.getSideMode(RelativeSide.fromDirection(machine.getFacing(), direction)) == SideMode.HYDROGEN) {
                out = direction;
            }
        }
        helper.assertTrue(out != null, "No Hydrogen face");
        BlockPos conduit = POS.relative(out);
        BlockPos burnerPos = POS.relative(out, 2);
        helper.setBlock(burnerPos, ModBlocks.FUEL_BURNER.get());
        FuelBurnerBlockEntity burner = helper.getBlockEntity(burnerPos, FuelBurnerBlockEntity.class);
        for (RelativeSide side : RelativeSide.values()) {
            burner.setSideMode(side, SideMode.INPUT);
        }
        helper.setBlock(conduit, ModBlocks.conduit(ConduitType.GAS, ConduitTier.WROUGHT).get());
        ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(conduit));
        machine.getHydrogen().set(0, FluidResource.of(ModFluids.HYDROGEN.get()), 2_000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(burner.getTank().contains(ModFluids.HYDROGEN.get()), "No hydrogen reached the burner"))
                .thenExecute(() -> helper.assertTrue(machine.getHydrogen().getAmount() < 2_000, "The Electrolyzer still holds all its hydrogen"))
                .thenSucceed();
    }

    // Oxygen through an Oxygen port: a steel smelt takes ceil(400 / 1.5) = 267 ticks instead of 400 and uses 50 mB.
    // With only 40 mB, the smelt runs at normal speed and leaves the oxygen alone.
    static void oxygenSpeedsFurnace(GameTestHelper helper) {
        MultiblockGameTests.buildFurnace(helper);
        long[] start = new long[1];
        int normal = 400;
        int boosted = (int) Math.ceil(normal / ArcforgeConfig.FURNACE_OXYGEN_SPEED.getAsDouble());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcforgeFurnaceBlockEntity furnace = furnace(helper);
                    helper.assertTrue(furnace.isFormed(), "Furnace didn't form");
                    furnace.setHeat(ArcforgeConfig.FURNACE_MAX_HEAT.getAsInt());
                    CrushingGameTests.insert(furnace.getItemHandler(null), ModItems.COAL_COKE.get(), 8);
                    CrushingGameTests.insert(furnace.getItemHandler(null), Items.IRON_INGOT, 1);
                    start[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(steel(helper) == 1, "No steel yet"))
                .thenExecute(() -> {
                    assertTook(helper, start[0], normal, "A smelt without oxygen");
                    // An Oxygen port on the back wall, and 1,000 mB through it.
                    BlockPos port = helper.absolutePos(new BlockPos(1, 3, 2));
                    MultiblockPorts.set(helper.getLevel(), furnace(helper), port, SideMode.OXYGEN, Direction.SOUTH);
                    var handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, port, Direction.SOUTH);
                    helper.assertTrue(handler != null && fill(handler, ModFluids.OXYGEN.get(), 1_000) == 1_000, "The Oxygen port refused oxygen");
                    helper.assertTrue(fill(handler, ModFluids.HYDROGEN.get(), 100) == 0, "The Oxygen port took hydrogen");
                    CrushingGameTests.insert(furnace(helper).getItemHandler(null), Items.IRON_INGOT, 1);
                    start[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(steel(helper) == 2, "No second steel yet"))
                .thenExecute(() -> {
                    assertTook(helper, start[0], boosted, "A smelt with oxygen");
                    ArcforgeFurnaceBlockEntity furnace = furnace(helper);
                    helper.assertTrue(furnace.getOxygen().getAmount() == 950, "Oxygen tank holds " + furnace.getOxygen().getAmount() + " mB, not 950");
                    furnace.getOxygen().set(0, FluidResource.of(ModFluids.OXYGEN.get()), 40);
                    CrushingGameTests.insert(furnace.getItemHandler(null), Items.IRON_INGOT, 1);
                    start[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(steel(helper) == 3, "No third steel yet"))
                .thenExecute(() -> {
                    assertTook(helper, start[0], normal, "A smelt with 40 mB of oxygen");
                    helper.assertTrue(furnace(helper).getOxygen().getAmount() == 40, "40 mB of oxygen was used anyway");
                })
                .thenSucceed();
    }

    private static ArcforgeFurnaceBlockEntity furnace(GameTestHelper helper) {
        return helper.getBlockEntity(new BlockPos(1, 1, 0), ArcforgeFurnaceBlockEntity.class);
    }

    private static int steel(GameTestHelper helper) {
        ItemStack output = furnace(helper).getItems().getStack(ArcforgeFurnaceBlockEntity.SLOT_OUTPUT);
        return output.is(ModItems.STEEL_INGOT.get()) ? output.getCount() : 0;
    }

    // Within a couple of ticks, for when the insert and the check land in the tick.
    private static void assertTook(GameTestHelper helper, long start, int expected, String what) {
        long took = helper.getTick() - start;
        helper.assertTrue(Math.abs(took - expected) <= 2, what + " took " + took + " ticks, not " + expected);
    }

    // 100 mB Light Oil + 50 mB Hydrogen make 2 Plastic Sheets, using exactly that.
    static void plasticRecipe(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(POS, ChemicalReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
        helper.assertTrue(fill(reactor.getRouter(), ModFluids.LIGHT_OIL.get(), 150) == 150, "Reactor refused Light Oil");
        helper.assertTrue(fill(reactor.getRouter(), ModFluids.HYDROGEN.get(), 75) == 75, "Reactor refused hydrogen");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT).isEmpty(), "No plastic yet"))
                .thenIdle(5)
                .thenExecute(() -> {
                    ItemStack out = reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(out.is(ModItems.PLASTIC_SHEET.get()) && out.getCount() == 2, "Reactor made " + out);
                    int oil = reactor.getInputA().contains(ModFluids.LIGHT_OIL.get()) ? reactor.getInputA().getAmount() : reactor.getInputB().getAmount();
                    int hydrogen = reactor.getInputA().contains(ModFluids.HYDROGEN.get()) ? reactor.getInputA().getAmount() : reactor.getInputB().getAmount();
                    helper.assertTrue(oil == 50 && hydrogen == 25, "Left " + oil + " mB Light Oil and " + hydrogen + " mB hydrogen, not 50 and 25");
                })
                .thenSucceed();
    }

    // The Conduit Filter and the Storage Upgrades need a Plastic Sheet where it goes, and don't craft without it.
    static void plasticInRecipes(GameTestHelper helper) {
        ItemStack wrought = new ItemStack(ModItems.WROUGHT_ALLOY.get());
        ItemStack tempered = new ItemStack(ModItems.TEMPERED_ALLOY.get());
        ItemStack hardened = new ItemStack(ModItems.HARDENED_ALLOY.get());
        ItemStack arcforged = new ItemStack(ModItems.ARCFORGED_ALLOY.get());
        ItemStack chest = new ItemStack(Items.CHEST);
        ItemStack arcite = new ItemStack(ModItems.ARCITE_CRYSTAL.get());
        assertNeedsPlastic(helper, "crafting/conduit_filter", new String[] { " P ", "WBW", " W " },
                Map.of('W', wrought, 'B', new ItemStack(Items.IRON_BARS)), wrought, ModItems.CONDUIT_FILTER.get().getDefaultInstance());
        assertNeedsPlastic(helper, "crafting/tempered_storage_upgrade", new String[] { "APA", "K K", "AAA" },
                Map.of('A', tempered, 'K', chest), tempered, null);
        assertNeedsPlastic(helper, "crafting/hardened_storage_upgrade", new String[] { "APA", "K K", "AAA" },
                Map.of('A', hardened, 'K', chest), hardened, null);
        assertNeedsPlastic(helper, "crafting/arcforged_storage_upgrade", new String[] { "ACA", "K K", "APA" },
                Map.of('A', arcforged, 'K', chest, 'C', arcite), arcforged, null);
        helper.succeed();
    }

    // The recipe matches with a Plastic Sheet at every P, and not with `instead` there.
    private static void assertNeedsPlastic(GameTestHelper helper, String path, String[] rows, Map<Character, ItemStack> key, ItemStack instead,
            ItemStack expected) {
        RecipeHolder<?> holder = helper.getLevel().recipeAccess().recipeMap()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, path)));
        helper.assertTrue(holder != null && holder.value() instanceof CraftingRecipe, path + " isn't a crafting recipe");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        ItemStack plastic = new ItemStack(ModItems.PLASTIC_SHEET.get());
        CraftingInput with = grid(rows, key, plastic);
        helper.assertTrue(recipe.matches(with, helper.getLevel()), path + " doesn't craft with a Plastic Sheet");
        if (expected != null) {
            helper.assertTrue(recipe.assemble(with).is(expected.getItem()), path + " makes " + recipe.assemble(with));
        }
        helper.assertFalse(recipe.matches(grid(rows, key, instead), helper.getLevel()), path + " crafts without a Plastic Sheet");
    }

    private static CraftingInput grid(String[] rows, Map<Character, ItemStack> key, ItemStack atP) {
        List<ItemStack> grid = new ArrayList<>();
        for (String row : rows) {
            for (char c : row.toCharArray()) {
                grid.add(c == ' ' ? ItemStack.EMPTY : c == 'P' ? atP.copy() : key.get(c).copy());
            }
        }
        return CraftingInput.of(3, 3, grid);
    }
}
