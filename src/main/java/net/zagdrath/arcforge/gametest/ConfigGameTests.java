/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.zagdrath.arcforge.config.ArcforgeClientConfig;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.config.ConfigMigration;
import net.zagdrath.arcforge.config.ConfigReset;

// The config: settings saved before the sections were grouped move to their new places, and every section and
// setting has a name on the config screen.
final class ConfigGameTests {
    private ConfigGameTests() {}

    // An old file's sections move under their new categories with their values; one already in place, and anything
    // never moved, is left alone; a second run changes nothing.
    static void migration(GameTestHelper helper) {
        try {
            Path file = Files.createTempFile("arcforge-migration", ".toml");
            try {
                Files.writeString(file, """
                        [power]
                        \t[power.heat]
                        \t\tcontactRate = 77
                        \t[power.firebox]
                        \t\tmaxTemperature = 999
                        [steam]
                        \t[steam.steamBoilerArray]
                        \t\tmaxHeatPerTickPerSection = 601
                        [machines]
                        \t[machines.treeCutter]
                        \t\tareaRadius = 3
                        [quantumTunnel]
                        \titemSlots = 12
                        [farming]
                        \t[farming.loam]
                        \t\tsaplingGrowthMultiplier = 3.0
                        """, StandardCharsets.UTF_8);
                ConfigMigration.migrate(file);
                try (CommentedFileConfig config = CommentedFileConfig.builder(file).build()) {
                    config.load();
                    helper.assertTrue(config.<Integer>getOrElse("power.steam.steamBoilerArray.maxHeatPerTickPerSection", -1) == 601,
                            "Steam didn't move under power");
                    helper.assertTrue(config.<Integer>getOrElse("power.burners.firebox.maxTemperature", -1) == 999, "The Firebox didn't move");
                    helper.assertTrue(config.<Integer>getOrElse("power.heat.contactRate", -1) == 77, "Heat (not moved) was changed");
                    helper.assertTrue(config.<Integer>getOrElse("machines.automation.treeCutter.areaRadius", -1) == 3,
                            "The Tree Cutter didn't move");
                    helper.assertTrue(config.<Integer>getOrElse("logistics.quantumTunnel.itemSlots", -1) == 12, "The Quantum Tunnel didn't move");
                    helper.assertTrue(config.<Double>getOrElse("farming.growing.loam.saplingGrowthMultiplier", -1.0) == 3.0, "Loam didn't move");
                    helper.assertFalse(config.contains("steam") || config.contains("quantumTunnel") || config.contains("machines.treeCutter"),
                            "Old sections were left behind");
                }
                String once = Files.readString(file);
                ConfigMigration.migrate(file);
                helper.assertTrue(Files.readString(file).equals(once), "A second run changed the file");
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        helper.succeed();
    }

    // /arcforge config reset: the command is registered, paths name settings and sections, a changed setting is found
    // and put back to its default (by its own path and by its section's), and "all" sees it too. It's changed and reset in
    // one go, so tests running alongside never see the changed value.
    static void reset(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("arcforge") != null,
                "/arcforge isn't registered");
        String path = ConfigReset.settings().entrySet().stream().filter(entry -> entry.getValue() == ArcforgeConfig.BOILER_ARRAY_HOT_SAVING)
                .map(java.util.Map.Entry::getKey).findFirst().orElse("");
        helper.assertTrue(path.endsWith(".steamBoilerArray.hotSaving"), "hotSaving is at " + path);
        String section = path.substring(0, path.lastIndexOf('.'));
        helper.assertTrue(ConfigReset.exists(path) && ConfigReset.exists(section) && !ConfigReset.exists("power.nothing"), "Paths aren't recognised");
        double fallback = ArcforgeConfig.BOILER_ARRAY_HOT_SAVING.getDefault();
        helper.assertTrue(!ConfigReset.changed(path).contains(path), "hotSaving counts as changed at its default");

        ArcforgeConfig.BOILER_ARRAY_HOT_SAVING.set(0.5);
        boolean seen = ConfigReset.changed("").contains(path) && ConfigReset.changed(section).contains(path);
        List<String> reset = ConfigReset.reset(section);
        double after = ArcforgeConfig.BOILER_ARRAY_HOT_SAVING.getAsDouble();
        ArcforgeConfig.BOILER_ARRAY_HOT_SAVING.set(0.5);
        List<String> resetOne = ConfigReset.reset(path);
        double afterOne = ArcforgeConfig.BOILER_ARRAY_HOT_SAVING.getAsDouble();

        helper.assertTrue(seen, "A changed hotSaving wasn't listed");
        helper.assertTrue(reset.contains(path) && after == fallback, "Resetting " + section + " left hotSaving at " + after);
        helper.assertTrue(resetOne.equals(List.of(path)) && afterOne == fallback, "Resetting " + path + " left it at " + afterOne);

        // The commands parse and run as the console would (nothing left to reset here, so each changes nothing).
        var server = helper.getLevel().getServer();
        try {
            server.getCommands().getDispatcher().execute("arcforge config reset all", server.createCommandSourceStack());
            server.getCommands().getDispatcher().execute("arcforge config reset " + path, server.createCommandSourceStack());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            helper.fail("/arcforge config reset didn't parse: " + e.getMessage());
        }
        helper.succeed();
    }

    // /arcforge config get and set: values parse by their setting's type and range (numbers with separators, booleans,
    // lists), bad ones are refused, a section lists its children, and set (run as the console would) changes the value;
    // it's put back in the same go, so tests running alongside never see it changed.
    static void getAndSet(GameTestHelper helper) {
        var limit = net.zagdrath.arcforge.config.ConfigEditor.setting("logistics.chunkLoader.chunksPerPlayer").orElse(null);
        helper.assertTrue(limit == ArcforgeConfig.CHUNK_LOADER_PLAYER_LIMIT, "logistics.chunkLoader.chunksPerPlayer isn't the chunk limit");
        var editor = new Object() {
            java.util.Optional<Object> parse(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> setting, String text) {
                return net.zagdrath.arcforge.config.ConfigEditor.parse(setting, text);
            }
        };
        helper.assertTrue(editor.parse(limit, "128").equals(java.util.Optional.of(128)), "128 isn't a chunk limit");
        helper.assertTrue(editor.parse(limit, "1_000").equals(java.util.Optional.of(1000)), "1_000 didn't parse");
        helper.assertTrue(editor.parse(limit, "5000").isEmpty() && editor.parse(limit, "-1").isEmpty() && editor.parse(limit, "lots").isEmpty(),
                "An out-of-range or non-number limit was taken");
        helper.assertTrue(editor.parse(ArcforgeConfig.CHUNK_LOADER_REQUIRE_ONLINE, "true").equals(java.util.Optional.of(true))
                && editor.parse(ArcforgeConfig.CHUNK_LOADER_REQUIRE_ONLINE, "maybe").isEmpty(), "Booleans don't parse");
        helper.assertTrue(editor.parse(ArcforgeConfig.JETPACK_TANK, "16000, 64,000, 256000").isEmpty(),
                "A list read 64,000 as one number (commas split lists)");
        helper.assertTrue(editor.parse(ArcforgeConfig.JETPACK_TANK, "16000, 64000, 256000").equals(java.util.Optional.of(List.of(16000, 64000, 256000)))
                && editor.parse(ArcforgeConfig.JETPACK_TANK, "16000, 0").isEmpty(), "Integer lists don't parse or check their elements");
        helper.assertTrue(editor.parse(ArcforgeConfig.SOLAR_HOT_BIOME_TAGS, "[c:is_desert, minecraft:is_savanna]")
                .equals(java.util.Optional.of(List.of("c:is_desert", "minecraft:is_savanna"))), "Text lists don't parse");
        var children = net.zagdrath.arcforge.config.ConfigEditor.children("logistics");
        helper.assertTrue(children.sections().contains("logistics.chunkLoader") && children.settings().stream().noneMatch(s -> s.startsWith("logistics.chunkLoader.")),
                "logistics doesn't list chunkLoader as a section: " + children);
        helper.assertTrue(net.zagdrath.arcforge.config.ConfigEditor.children("").sections().contains("logistics"), "The top doesn't list logistics");

        var server = helper.getLevel().getServer();
        double before = ArcforgeConfig.BOILER_ARRAY_HOT_SAVING.getAsDouble();
        double set;
        try {
            server.getCommands().getDispatcher().execute("arcforge config get", server.createCommandSourceStack());
            server.getCommands().getDispatcher().execute("arcforge config get logistics.chunkLoader", server.createCommandSourceStack());
            server.getCommands().getDispatcher().execute("arcforge config get logistics.chunkLoader.chunksPerPlayer", server.createCommandSourceStack());
            String path = ConfigReset.settings().entrySet().stream().filter(entry -> entry.getValue() == ArcforgeConfig.BOILER_ARRAY_HOT_SAVING)
                    .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
            server.getCommands().getDispatcher().execute("arcforge config set " + path + " 0.25", server.createCommandSourceStack());
            set = ArcforgeConfig.BOILER_ARRAY_HOT_SAVING.getAsDouble();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            helper.fail("/arcforge config get or set didn't parse: " + e.getMessage());
            return;
        } finally {
            net.zagdrath.arcforge.config.ConfigEditor.set(ArcforgeConfig.BOILER_ARRAY_HOT_SAVING, before);
        }
        helper.assertTrue(set == 0.25, "config set left hotSaving at " + set);
        helper.succeed();
    }

    // Every section and setting of both config files has a name in en_us.json, so the config screen never shows a raw
    // arcforge.configuration.* key.
    static void namesTranslated(GameTestHelper helper) {
        JsonObject lang;
        try (InputStream in = ConfigGameTests.class.getResourceAsStream("/assets/arcforge/lang/en_us.json")) {
            helper.assertTrue(in != null, "No en_us.json on the classpath");
            lang = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<String> missing = new ArrayList<>();
        for (ModConfigSpec spec : List.of(ArcforgeConfig.SPEC, ArcforgeClientConfig.SPEC)) {
            collect(spec.getSpec(), "", lang, missing);
        }
        helper.assertTrue(missing.isEmpty(), missing.size() + " config names untranslated: " + missing.subList(0, Math.min(10, missing.size())));
        helper.succeed();
    }

    private static void collect(UnmodifiableConfig config, String path, JsonObject lang, List<String> missing) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String key = entry.getKey();
            String full = path.isEmpty() ? key : path + "." + key;
            if (!lang.has("arcforge.configuration." + key)) {
                missing.add(full);
            }
            if (entry.getRawValue() instanceof UnmodifiableConfig section) {
                collect(section, full, lang, missing);
            }
        }
    }
}
