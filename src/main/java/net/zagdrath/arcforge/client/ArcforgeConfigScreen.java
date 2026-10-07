/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.electronwill.nightconfig.core.UnmodifiableConfig;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// The Mods screen's config screen: NeoForge's, whose top page (the categories) also has a Reset All button
// that puts every value on every page back to its default, after asking.
public final class ArcforgeConfigScreen {
    private static final Component RESET_ALL = Component.translatable("arcforge.configuration.reset_all");
    private static final Component RESET_ALL_TOOLTIP = Component.translatable("arcforge.configuration.reset_all.tooltip");
    private static final Component RESET_ALL_TITLE = Component.translatable("arcforge.configuration.reset_all.title");
    private static final Component RESET_ALL_MESSAGE = Component.translatable("arcforge.configuration.reset_all.message");

    private ArcforgeConfigScreen() {}

    public static Screen create(ModContainer container, Screen parent) {
        return new ConfigurationScreen(container, parent, (screen, type, modConfig, title) -> new TopPage(screen, type, modConfig, title));
    }

    // Sets every value in this config file (the common rules, or the client's own settings) back to its default and
    // saves it.
    static void resetAll(ModConfig modConfig) {
        ModConfigSpec spec = modConfig.getSpec() instanceof ModConfigSpec own ? own : ArcforgeConfig.SPEC;
        resetAll(spec.getValues());
        spec.save();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void resetAll(UnmodifiableConfig values) {
        for (UnmodifiableConfig.Entry entry : values.entrySet()) {
            Object value = entry.getRawValue();
            if (value instanceof ModConfigSpec.ConfigValue config) {
                config.set(config.getDefault());
            } else if (value instanceof UnmodifiableConfig section) {
                resetAll(section);
            }
        }
    }

    private static final class TopPage extends ConfigurationScreen.ConfigurationSectionScreen {
        private final ModConfig modConfig;

        TopPage(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
            super(parent, type, modConfig, title);
            this.modConfig = modConfig;
        }

        @Override
        protected Collection<? extends Element> createSyntheticValues() {
            List<Element> elements = new ArrayList<>(super.createSyntheticValues());
            Button button = Button.builder(RESET_ALL, pressed -> confirm())
                    .tooltip(Tooltip.create(RESET_ALL_TOOLTIP))
                    .width(Button.SMALL_WIDTH)
                    .build();
            elements.add(new Element(RESET_ALL, RESET_ALL_TOOLTIP, button, false));
            return elements;
        }

        private void confirm() {
            minecraft.setScreen(new ConfirmScreen(yes -> {
                if (yes) {
                    resetAll(modConfig);
                    // The pages below were built with the old values.
                    sectionCache.clear();
                    rebuild();
                }
                minecraft.setScreen(this);
            }, RESET_ALL_TITLE, RESET_ALL_MESSAGE));
        }
    }
}
