/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.command;

import java.util.List;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ConfigReset;

// /arcforge config reset: puts arcforge-common.toml back to its defaults from in game or the server console, for admins
// (permission level 3) who'd rather not edit the file.
//  - reset all            lists the settings that differ from their defaults (nothing changes)
//  - reset all confirm    resets every one of them
//  - reset <path>         resets one setting, or every setting in a section ("power.steam"), at once
// The file is saved straight away; see ConfigReset for when the new values apply.
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
                        .then(Commands.literal("reset")
                                .then(Commands.literal("all")
                                        .executes(ArcforgeCommands::preview)
                                        .then(Commands.literal("confirm").executes(context -> reset(context, ""))))
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Stream.concat(ConfigReset.sections().stream(), ConfigReset.settings().keySet().stream()), builder))
                                        .executes(context -> reset(context, StringArgumentType.getString(context, "path")))))));
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
