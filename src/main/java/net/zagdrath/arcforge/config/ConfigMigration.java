/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.zagdrath.arcforge.Arcforge;

// Moves the settings of a config file written before the sections were grouped (steam under Heat & Power, the machines
// into Processing, Chemistry and Automation, and so on) to where they are now, so nothing anyone set goes back to its
// default. Runs once, before NeoForge reads the file: a section is only moved while it's still at its old path and
// nothing is at the new one.
public final class ConfigMigration {
    // Old section path -> new section path, everything in it moving with it.
    private static final List<Map.Entry<String, String>> MOVES = List.of(
            Map.entry("power.combustionPlant", "power.burners.combustionPlant"),
            Map.entry("power.firebox", "power.burners.firebox"),
            Map.entry("power.fuelBurner", "power.burners.fuelBurner"),
            Map.entry("power.fireboxArray", "power.burners.fireboxArray"),
            Map.entry("power.oxyFuel", "power.burners.oxyFuel"),
            Map.entry("power.flueGas", "power.burners.flueGas"),
            Map.entry("power.geothermalPlant", "power.generators.geothermalPlant"),
            Map.entry("power.solarThermalArray", "power.generators.solarThermalArray"),
            Map.entry("power.thermoelectricPlant", "power.generators.thermoelectricPlant"),
            Map.entry("steam", "power.steam"),
            Map.entry("machines.arcCrusher", "machines.processing.arcCrusher"),
            Map.entry("machines.inductionFurnace", "machines.processing.inductionFurnace"),
            Map.entry("machines.metalPress", "machines.processing.metalPress"),
            Map.entry("machines.arcMelter", "machines.processing.arcMelter"),
            Map.entry("machines.sifter", "machines.processing.sifter"),
            Map.entry("machines.diamondPress", "machines.processing.diamondPress"),
            Map.entry("machines.fiberizer", "machines.processing.fiberizer"),
            Map.entry("machines.infuser", "machines.processing.infuser"),
            Map.entry("machines.fermenter", "machines.chemistry.fermenter"),
            Map.entry("machines.chemicalReactor", "machines.chemistry.chemicalReactor"),
            Map.entry("machines.electrolyzer", "machines.chemistry.electrolyzer"),
            Map.entry("machines.hydrothermalCarbonizer", "machines.chemistry.hydrothermalCarbonizer"),
            Map.entry("machines.gasifier", "machines.chemistry.gasifier"),
            Map.entry("machines.fischerTropschReactor", "machines.chemistry.fischerTropschReactor"),
            Map.entry("machines.carbonReclaimer", "machines.chemistry.carbonReclaimer"),
            Map.entry("machines.assembler", "machines.automation.assembler"),
            Map.entry("machines.blockBreaker", "machines.automation.blockBreaker"),
            Map.entry("machines.blockPlacer", "machines.automation.blockPlacer"),
            Map.entry("machines.vacuumCollector", "machines.automation.vacuumCollector"),
            Map.entry("machines.treeCutter", "machines.automation.treeCutter"),
            Map.entry("machines.electricPump", "machines.automation.electricPump"),
            Map.entry("machines.arcQuarry", "machines.automation.arcQuarry"),
            Map.entry("farming.compostBin", "farming.growing.compostBin"),
            Map.entry("farming.fertilizers", "farming.growing.fertilizers"),
            Map.entry("farming.loam", "farming.growing.loam"),
            Map.entry("farming.loamFarmland", "farming.growing.loamFarmland"),
            Map.entry("farming.hops", "farming.growing.hops"),
            Map.entry("farming.rusticMachines", "farming.cropProcessing.rusticMachines"),
            Map.entry("farming.millstone", "farming.cropProcessing.millstone"),
            Map.entry("farming.mill", "farming.cropProcessing.mill"),
            Map.entry("farming.oilPress", "farming.cropProcessing.oilPress"),
            Map.entry("farming.seedExtractor", "farming.cropProcessing.seedExtractor"),
            Map.entry("farming.grainDryer", "farming.cropProcessing.grainDryer"),
            Map.entry("farming.resinTap", "farming.cropProcessing.resinTap"),
            Map.entry("farming.vulcanizer", "farming.cropProcessing.vulcanizer"),
            Map.entry("farming.airSeparator", "farming.farmChemistry.airSeparator"),
            Map.entry("farming.haberReactor", "farming.farmChemistry.haberReactor"),
            Map.entry("farming.biogasDigester", "farming.farmChemistry.biogasDigester"),
            Map.entry("farming.cloche", "farming.automatedFarms.cloche"),
            Map.entry("farming.greenhouse", "farming.automatedFarms.greenhouse"),
            Map.entry("meters", "logistics.meters"),
            Map.entry("chargepad", "logistics.chargepad"),
            Map.entry("reservoir", "logistics.reservoir"),
            Map.entry("experience", "logistics.experience"),
            Map.entry("quantumTunnel", "logistics.quantumTunnel"),
            Map.entry("chunkLoader", "logistics.chunkLoader"),
            Map.entry("ores", "world.ores"),
            Map.entry("haliteBeds", "world.haliteBeds"),
            Map.entry("tools", "equipment.tools"),
            Map.entry("foundrySuit", "equipment.foundrySuit"));

    private ConfigMigration() {}

    public static void migrate(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(file).preserveInsertionOrder().sync().build()) {
            config.load();
            int moved = 0;
            for (Map.Entry<String, String> move : MOVES) {
                Object section = config.get(move.getKey());
                if (section != null && !config.contains(move.getValue())) {
                    config.set(move.getValue(), section);
                    config.remove(move.getKey());
                    moved++;
                }
            }
            if (moved > 0) {
                config.save();
                Arcforge.LOGGER.info("Moved {} config sections in {} to their new places", moved, file.getFileName());
            }
        } catch (RuntimeException e) {
            // A file that can't be read is left for NeoForge, which reports it (and falls back to the defaults) itself.
            Arcforge.LOGGER.warn("Couldn't move old config sections in {}: {}", file.getFileName(), e.toString());
        }
    }

    // For the GameTest: the moves, oldest path first.
    static List<Map.Entry<String, String>> moves() {
        return MOVES;
    }
}
