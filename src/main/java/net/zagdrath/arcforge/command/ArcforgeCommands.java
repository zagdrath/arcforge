/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.conduit.network.ConduitNetwork;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.config.ConfigEditor;
import net.zagdrath.arcforge.config.ConfigReset;

// /arcforge config: reads, sets and resets arcforge-common.toml's settings from in game or the server console, for admins
// (permission level 3) who'd rather not edit the file.
//  - get                  the top sections; click one to open it
//  - get <section>        its subsections and settings with their values (changed ones in gold); click a setting to fill
//                         in its set command
//  - get <setting>        its value, default, what it takes, and its description
//  - set <setting> <value> sets and saves it (see ConfigEditor for how values are written)
//  - reset all            lists the settings that differ from their defaults (nothing changes)
//  - reset all confirm    resets every one of them
//  - reset <path>         resets one setting, or every setting in a section ("power.steam"), at once
// The file is saved straight away; see ConfigReset for when the new values apply.
// /arcforge debug conduits: the busiest conduit networks in the caller's dimension (see debugConduits).
@EventBusSubscriber(modid = Arcforge.MODID)
public final class ArcforgeCommands {
    // How many changed settings a reply names before "and N more".
    private static final int LISTED = 10;

    private ArcforgeCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Arcforge.MODID)
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("config")
                        .then(Commands.literal("get")
                                .executes(context -> get(context, ""))
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Stream.concat(ConfigReset.sections().stream(), ConfigReset.settings().keySet().stream()), builder))
                                        .executes(context -> get(context, StringArgumentType.getString(context, "path")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ConfigReset.settings().keySet(), builder))
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        valueSuggestions(StringArgumentType.getString(context, "path")), builder))
                                                .executes(ArcforgeCommands::set))))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("all")
                                        .executes(ArcforgeCommands::preview)
                                        .then(Commands.literal("confirm").executes(context -> reset(context, ""))))
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Stream.concat(ConfigReset.sections().stream(), ConfigReset.settings().keySet().stream()), builder))
                                        .executes(context -> reset(context, StringArgumentType.getString(context, "path"))))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("conduits").executes(ArcforgeCommands::debugConduits))));
    }

    // The conduit networks in the caller's dimension, busiest (most insert calls) first: per network its targets
    // (input sides), insert calls a tick and client updates a second, both over the last full second.
    private static int debugConduits(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        List<ConduitNetwork<?>> networks = new ArrayList<>(ConduitNetworkManager.get(level).getNetworks());
        networks.sort(Comparator.comparingInt((ConduitNetwork<?> network) -> network.getInsertCallsPerSecond())
                .thenComparingInt(ConduitNetwork::getSyncsPerSecond).reversed());
        int conduits = networks.stream().mapToInt(network -> network.getMembers().size()).sum();
        int calls = networks.stream().mapToInt(ConduitNetwork::getInsertCallsPerSecond).sum();
        int syncs = networks.stream().mapToInt(ConduitNetwork::getSyncsPerSecond).sum();
        source.sendSuccess(() -> Component.translatable("command.arcforge.debug.conduits", networks.size(), conduits,
                perTick(calls), syncs, level.dimension().identifier().toString()), false);
        for (ConduitNetwork<?> network : networks.subList(0, Math.min(LISTED, networks.size()))) {
            BlockPos at = network.getMembers().getFirst();
            Component type = level.getBlockState(at).getBlock() instanceof ConduitBlock conduit
                    ? conduit.getConduitType().getDisplayName() : Component.literal("?");
            String coordinates = at.getX() + " " + at.getY() + " " + at.getZ();
            MutableComponent where = Component.literal(coordinates).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + coordinates)));
            source.sendSuccess(() -> Component.translatable("command.arcforge.debug.conduits.network", type, where,
                    network.getMembers().size(), network.getSourceCount(), network.getSinkCount(),
                    perTick(network.getInsertCallsPerSecond()), network.getSyncsPerSecond()), false);
        }
        if (networks.size() > LISTED) {
            source.sendSuccess(() -> Component.translatable("command.arcforge.debug.conduits.more", networks.size() - LISTED), false);
        }
        return networks.size();
    }

    private static String perTick(int perSecond) {
        return String.format(Locale.ROOT, "%.1f", perSecond / 20.0);
    }

    // --- get and set ---

    private static int get(CommandContext<CommandSourceStack> context, String path) {
        CommandSourceStack source = context.getSource();
        var setting = ConfigEditor.setting(path);
        if (setting.isPresent()) {
            showSetting(source, path, setting.get());
            return 1;
        }
        if (!ConfigReset.exists(path)) {
            source.sendFailure(Component.translatable("command.arcforge.config.unknown", path));
            return 0;
        }
        ConfigEditor.Children children = ConfigEditor.children(path);
        source.sendSuccess(() -> path.isEmpty()
                ? Component.translatable("command.arcforge.config.top")
                : Component.translatable("command.arcforge.config.section", Component.literal(path).withStyle(ChatFormatting.YELLOW),
                        children.settings().size(), children.sections().size()), false);
        String prefix = path.isEmpty() ? "" : path + ".";
        for (String section : children.sections()) {
            String command = "/" + Arcforge.MODID + " config get " + section;
            MutableComponent line = Component.literal("  " + section.substring(prefix.length()) + " >").withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.RunCommand(command))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
            source.sendSuccess(() -> line, false);
        }
        var settings = ConfigReset.settings();
        for (String name : children.settings()) {
            var value = settings.get(name);
            boolean changed = ConfigReset.changed(name).contains(name);
            String command = "/" + Arcforge.MODID + " config set " + name + " ";
            String comment = value.getSpec().getComment();
            MutableComponent line = Component.literal("  " + name.substring(prefix.length()) + " = ")
                    .append(Component.literal(ConfigEditor.format(value.getRaw())).withStyle(changed ? ChatFormatting.GOLD : ChatFormatting.WHITE))
                    .withStyle(style -> style.withColor(ChatFormatting.GRAY)
                            .withClickEvent(new ClickEvent.SuggestCommand(command))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(comment != null ? comment : name))));
            source.sendSuccess(() -> line, false);
        }
        return children.sections().size() + children.settings().size();
    }

    private static void showSetting(CommandSourceStack source, String path, ModConfigSpec.ConfigValue<?> setting) {
        String command = "/" + Arcforge.MODID + " config set " + path + " ";
        MutableComponent set = Component.translatable("command.arcforge.config.set_button").withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
        source.sendSuccess(() -> Component.translatable("command.arcforge.config.value",
                Component.literal(path).withStyle(ChatFormatting.YELLOW),
                Component.literal(ConfigEditor.format(setting.getRaw())).withStyle(ChatFormatting.GOLD),
                ConfigEditor.format(setting.getDefault()), set), false);
        source.sendSuccess(() -> Component.translatable("command.arcforge.config.takes", ConfigEditor.expected(setting))
                .withStyle(ChatFormatting.GRAY), false);
        String comment = setting.getSpec().getComment();
        if (comment != null) {
            for (String line : comment.split("\n")) {
                if (!line.isBlank()) {
                    source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.DARK_GRAY), false);
                }
            }
        }
        if (ConfigEditor.needsRestart(setting)) {
            source.sendSuccess(() -> Component.translatable("command.arcforge.config.restart").withStyle(ChatFormatting.RED), false);
        }
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String path = StringArgumentType.getString(context, "path");
        String text = StringArgumentType.getString(context, "value");
        var setting = ConfigEditor.setting(path);
        if (setting.isEmpty()) {
            source.sendFailure(ConfigReset.exists(path)
                    ? Component.translatable("command.arcforge.config.not_setting", path)
                    : Component.translatable("command.arcforge.config.unknown", path));
            return 0;
        }
        var parsed = ConfigEditor.parse(setting.get(), text);
        if (parsed.isEmpty()) {
            source.sendFailure(Component.translatable("command.arcforge.config.invalid", text, path, ConfigEditor.expected(setting.get())));
            return 0;
        }
        Object old = ConfigEditor.set(setting.get(), parsed.get());
        String now = ConfigEditor.format(parsed.get());
        // Told to other admins too, as /gamerule is.
        source.sendSuccess(() -> Component.translatable(ConfigEditor.needsRestart(setting.get())
                ? "command.arcforge.config.set.restart" : "command.arcforge.config.set", path, now, ConfigEditor.format(old)), true);
        return 1;
    }

    // For the value argument: true/false, or the current value and the default (lists without their brackets).
    private static List<String> valueSuggestions(String path) {
        var setting = ConfigEditor.setting(path);
        if (setting.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (setting.get().getDefault() instanceof Boolean) {
            out.add("true");
            out.add("false");
        } else {
            for (Object value : List.of(setting.get().getRaw(), setting.get().getDefault())) {
                String shown = ConfigEditor.format(value);
                if (value instanceof List<?> && shown.length() >= 2) {
                    shown = shown.substring(1, shown.length() - 1);
                }
                if (!out.contains(shown)) {
                    out.add(shown);
                }
            }
        }
        return out;
    }

    private static int preview(CommandContext<CommandSourceStack> context) {
        List<String> changed = ConfigReset.changed("");
        CommandSourceStack source = context.getSource();
        if (changed.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.arcforge.config.all_default"), false);
            return 0;
        }
        String command = "/" + Arcforge.MODID + " config reset all confirm";
        MutableComponent confirm = Component.literal(command).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.SuggestCommand(command)));
        source.sendSuccess(() -> Component.translatable("command.arcforge.config.preview", changed.size(), list(changed), confirm), false);
        return changed.size();
    }

    private static int reset(CommandContext<CommandSourceStack> context, String path) {
        CommandSourceStack source = context.getSource();
        if (!ConfigReset.exists(path)) {
            source.sendFailure(Component.translatable("command.arcforge.config.unknown", path));
            return 0;
        }
        List<String> changed = ConfigReset.reset(path);
        String scope = path.isEmpty() ? "all" : path;
        if (changed.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.arcforge.config.already_default", scope), false);
        } else {
            // Told to other admins too, as /gamerule is.
            source.sendSuccess(() -> Component.translatable("command.arcforge.config.reset", changed.size(), list(changed)), true);
        }
        return changed.size();
    }

    // The first few paths, comma separated, then how many more.
    private static Component list(List<String> paths) {
        String shown = String.join(", ", paths.subList(0, Math.min(LISTED, paths.size())));
        return paths.size() > LISTED
                ? Component.translatable("command.arcforge.config.and_more", shown, paths.size() - LISTED)
                : Component.literal(shown);
    }
}
