/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.electronwill.nightconfig.core.UnmodifiableConfig;

import net.neoforged.neoforge.common.ModConfigSpec;

// Puts arcforge-common.toml's settings back to their defaults (see ArcforgeCommands' /arcforge config reset). A path is a
// setting's dotted path ("power.steam.steamBoilerArray.hotSaving") or a section's ("power.steam"); "" is everything.
// Changes are written to the file at once, and most apply straight away; some are read only when a block is placed or
// loaded, or a chunk generated.
public final class ConfigReset {
    private ConfigReset() {}

    // Every setting, by its dotted path, in the file's order.
    public static Map<String, ModConfigSpec.ConfigValue<?>> settings() {
        Map<String, ModConfigSpec.ConfigValue<?>> all = new LinkedHashMap<>();
        collect(ArcforgeConfig.SPEC.getValues(), "", all);
        return all;
    }

    private static void collect(UnmodifiableConfig config, String path, Map<String, ModConfigSpec.ConfigValue<?>> into) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String full = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            Object value = entry.getRawValue();
            if (value instanceof ModConfigSpec.ConfigValue<?> setting) {
                into.put(full, setting);
            } else if (value instanceof UnmodifiableConfig section) {
                collect(section, full, into);
            }
        }
    }

    // Every section's dotted path, for suggestions.
    public static List<String> sections() {
        List<String> sections = new ArrayList<>();
        for (String setting : settings().keySet()) {
            for (int dot = setting.indexOf('.'); dot >= 0; dot = setting.indexOf('.', dot + 1)) {
                String section = setting.substring(0, dot);
                if (!sections.contains(section)) {
                    sections.add(section);
                }
            }
        }
        return sections;
    }

    // Whether the path names a setting or a section ("" names everything).
    public static boolean exists(String path) {
        return path.isEmpty() || settings().containsKey(path) || sections().contains(path);
    }

    // The settings at or under the path that aren't at their defaults.
    public static List<String> changed(String path) {
        List<String> changed = new ArrayList<>();
        settings().forEach((name, setting) -> {
            if (under(name, path) && !isDefault(setting)) {
                changed.add(name);
            }
        });
        return changed;
    }

    // Resets the settings at or under the path and saves the file. Returns the paths that changed.
    public static List<String> reset(String path) {
        List<String> changed = changed(path);
        if (!changed.isEmpty()) {
            Map<String, ModConfigSpec.ConfigValue<?>> settings = settings();
            for (String name : changed) {
                resetValue(settings.get(name));
            }
            ArcforgeConfig.SPEC.save();
        }
        return changed;
    }

    private static boolean under(String name, String path) {
        return path.isEmpty() || name.equals(path) || name.startsWith(path + ".");
    }

    private static boolean isDefault(ModConfigSpec.ConfigValue<?> setting) {
        Object current = setting.getRaw();
        Object fallback = setting.getDefault();
        // The file may hold 3 where the default is 3.0, or a long where it's an int.
        if (current instanceof Number a && fallback instanceof Number b) {
            return a.doubleValue() == b.doubleValue();
        }
        return Objects.equals(current, fallback);
    }

    private static <T> void resetValue(ModConfigSpec.ConfigValue<T> setting) {
        setting.set(setting.getDefault());
    }
}
