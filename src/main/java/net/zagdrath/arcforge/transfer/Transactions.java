/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.transfer;

import java.util.function.ToIntFunction;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

// For APIs whose methods take a simulate flag rather than a transaction (the machine control and gas APIs).
public final class Transactions {
    private Transactions() {}

    // Runs a transfer in its own transaction (nested in the caller's, if one is open), committing it unless simulating.
    // getCurrentOpenedTransaction is meant for exactly this: a method with no transaction parameter.
    @SuppressWarnings("deprecation")
    public static int transact(boolean simulate, ToIntFunction<TransactionContext> transfer) {
        TransactionContext outer = Transaction.getLifecycle() == Transaction.Lifecycle.OPEN ? Transaction.getCurrentOpenedTransaction() : null;
        try (Transaction transaction = Transaction.open(outer)) {
            int moved = transfer.applyAsInt(transaction);
            if (!simulate) {
                transaction.commit();
            }
            return moved;
        }
    }
}
