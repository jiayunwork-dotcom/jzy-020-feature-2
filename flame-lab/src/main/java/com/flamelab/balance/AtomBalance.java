package com.flamelab.balance;

import java.util.EnumMap;

/**
 * Per-element closure of the atom balance: atoms in minus atoms out, per mole
 * of fuel fed. Must stay below the pinned threshold for a job to converge.
 */
public record AtomBalance(EnumMap<Element, Double> residuals, double maxAbsResidual) {
}
