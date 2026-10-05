/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.settings;

import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * A setting only some machines have, such as the Electrolyzer's hydrogen venting or the Vacuum Collector's range.
 * Values are passed as strings whose form depends on {@link #type()}. Change one with
 * {@link MachineSettings#setOption(String, String)}.
 *
 * @param id      the option's id, stable across versions, e.g. {@code "vent_hydrogen"}
 * @param name    the option's display name
 * @param type    the kind of value it takes
 * @param value   the current value
 * @param min     the smallest value of an {@link OptionType#INTEGER} option, otherwise 0
 * @param max     the largest value of an {@link OptionType#INTEGER} option, otherwise 0
 * @param choices the values a {@link OptionType#CHOICE} option takes, otherwise empty
 */
public record MachineOption(String id, Component name, OptionType type, String value, int min, int max, List<String> choices) {
    /**
     * Copies the choices so the record cannot change.
     *
     * @param id      the option's id
     * @param name    the option's display name
     * @param type    the kind of value it takes
     * @param value   the current value
     * @param min     the smallest integer value
     * @param max     the largest integer value
     * @param choices the values a choice option takes
     */
    public MachineOption {
        choices = List.copyOf(choices);
    }
}
