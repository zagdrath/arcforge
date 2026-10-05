/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine;

import java.util.UUID;

/**
 * The player who owns a machine (normally whoever placed it), as Arcforge's security system tracks it.
 *
 * @param id   the owner's profile id
 * @param name the owner's name when last seen, or an empty string if unknown
 */
public record MachineOwner(UUID id, String name) {}
