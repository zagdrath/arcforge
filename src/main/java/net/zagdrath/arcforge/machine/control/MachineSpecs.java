/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control;

import net.zagdrath.arcforge.machine.control.spec.ArrayMultiblockSpecs;
import net.zagdrath.arcforge.machine.control.spec.AutomationMachineSpecs;
import net.zagdrath.arcforge.machine.control.spec.ChemicalMachineSpecs;
import net.zagdrath.arcforge.machine.control.spec.ControllerMultiblockSpecs;
import net.zagdrath.arcforge.machine.control.spec.PowerMachineSpecs;
import net.zagdrath.arcforge.machine.control.spec.ProcessingMachineSpecs;

// Every machine the machine control API covers, by family. Registered when capabilities are (block entity types exist).
final class MachineSpecs {
    private MachineSpecs() {}

    static void registerAll() {
        ProcessingMachineSpecs.register();
        ChemicalMachineSpecs.register();
        PowerMachineSpecs.register();
        AutomationMachineSpecs.register();
        ArrayMultiblockSpecs.register();
        ControllerMultiblockSpecs.register();
    }
}
