/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.neoforged.neoforge.common.ModConfigSpec;

// Reads and sets arcforge-common.toml's settings by dotted path, for ArcforgeCommands' /arcforge config get and set. A
// value is typed by its setting's default: true/false, a whole or decimal number (underscores and commas allowed, as in
// "1_000" or "1,000"), an enum's name, text, or a list written comma separated ("c:is_desert, minecraft:is_savanna";
// "[]" or nothing for empty). The setting's own validator (its range, or the list's element check) has the last word.
public final class ConfigEditor {
    private ConfigEditor() {}

    // What a section holds directly: its subsections and its settings, in the file's order.
    public record Children(List<String> sections, List<String> settings) {}

    public static Children children(String section) {
        String prefix = section.isEmpty() ? "" : section + ".";
        List<String> sections = new ArrayList<>();
        List<String> settings = new ArrayList<>();
        for (String path : ConfigReset.settings().keySet()) {
            if (!path.startsWith(prefix)) {
                continue;
            }
            String rest = path.substring(prefix.length());
            int dot = rest.indexOf('.');
            if (dot < 0) {
                settings.add(path);
            } else {
                String sub = prefix + rest.substring(0, dot);
                if (!sections.contains(sub)) {
                    sections.add(sub);
                }
            }
        }
        return new Children(sections, settings);
    }

    public static Optional<ModConfigSpec.ConfigValue<?>> setting(String path) {
        return Optional.ofNullable(ConfigReset.settings().get(path));
    }

    // A value as the file would show it: lists comma separated, numbers without exponent noise.
    public static String format(Object value) {
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object element : list) {
                parts.add(format(element));
            }
            return "[" + String.join(", ", parts) + "]";
        }
        if (value instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) {
            return String.format(Locale.ROOT, "%.1f", d);
        }
        return String.valueOf(value);
    }

    // What a setting takes, for help and errors: "true or false", "a whole number from 0 to 1024", "a list".
    public static String expected(ModConfigSpec.ConfigValue<?> setting) {
        Object fallback = setting.getDefault();
        ModConfigSpec.Range<?> range = setting.getSpec().getRange();
        String bounds = range != null ? " from " + format(range.getMin()) + " to " + format(range.getMax()) : "";
        if (fallback instanceof Boolean) {
            return "true or false";
        }
        if (fallback instanceof Integer || fallback instanceof Long) {
            return "a whole number" + bounds;
        }
        if (fallback instanceof Number) {
            return "a number" + bounds;
        }
        if (fallback instanceof Enum<?> e) {
            List<String> names = new ArrayList<>();
            for (Object constant : e.getDeclaringClass().getEnumConstants()) {
                names.add(((Enum<?>) constant).name().toLowerCase(Locale.ROOT));
            }
            return "one of " + String.join(", ", names);
        }
        if (fallback instanceof List<?>) {
            return "a list, comma separated";
        }
        return "text";
    }

    // The text parsed to the setting's type and checked by its validator; empty if it isn't a value the setting takes.
    public static Optional<Object> parse(ModConfigSpec.ConfigValue<?> setting, String text) {
        Object parsed = parseAs(setting.getDefault(), text.trim());
        return parsed != null && setting.getSpec().test(parsed) ? Optional.of(parsed) : Optional.empty();
    }

    private static Object parseAs(Object fallback, String text) {
        try {
            if (fallback instanceof Boolean) {
                return switch (text.toLowerCase(Locale.ROOT)) {
                    case "true", "yes", "on" -> Boolean.TRUE;
                    case "false", "no", "off" -> Boolean.FALSE;
                    default -> null;
                };
            }
            String number = text.replace("_", "").replace(",", "");
            if (fallback instanceof Integer) {
                return Integer.parseInt(number);
            }
            if (fallback instanceof Long) {
                return Long.parseLong(number);
            }
            if (fallback instanceof Double) {
                return Double.parseDouble(number);
            }
            if (fallback instanceof Float) {
                return Float.parseFloat(number);
            }
            if (fallback instanceof Enum<?> e) {
                for (Object constant : e.getDeclaringClass().getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(text)) {
                        return constant;
                    }
                }
                return null;
            }
            if (fallback instanceof List<?> list) {
                return parseList(list, text);
            }
            return text;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Each element typed like the default list's first one (text when the default is empty).
    private static List<Object> parseList(List<?> fallback, String text) {
        String inner = text.startsWith("[") && text.endsWith("]") ? text.substring(1, text.length() - 1) : text;
        List<Object> out = new ArrayList<>();
        if (inner.isBlank()) {
            return out;
        }
        Object model = fallback.isEmpty() ? "" : fallback.getFirst();
        for (String part : inner.split(",")) {
            String element = part.trim();
            if (element.length() >= 2 && element.startsWith("\"") && element.endsWith("\"")) {
                element = element.substring(1, element.length() - 1);
            }
            Object parsed = model instanceof List<?> ? element : parseAs(model, element);
            if (parsed == null) {
                return null;
            }
            out.add(parsed);
        }
        return out;
    }

    // Sets and saves. Returns the old value.
    @SuppressWarnings("unchecked")
    public static Object set(ModConfigSpec.ConfigValue<?> setting, Object value) {
        Object old = setting.getRaw();
        ((ModConfigSpec.ConfigValue<Object>) setting).set(value);
        ArcforgeConfig.SPEC.save();
        return old;
    }

    // Whether a change waits for a world or game restart.
    public static boolean needsRestart(ModConfigSpec.ConfigValue<?> setting) {
        return setting.getSpec().restartType() != ModConfigSpec.RestartType.NONE;
    }

    // Every setting by path, for suggestions.
    public static Map<String, ModConfigSpec.ConfigValue<?>> all() {
        return ConfigReset.settings();
    }
}
