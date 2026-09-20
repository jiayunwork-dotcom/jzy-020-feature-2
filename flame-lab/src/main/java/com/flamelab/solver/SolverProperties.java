package com.flamelab.solver;

/**
 * Pinned convergence controls for the enthalpy iteration.
 *
 * @param enthalpyTolerance    absolute tolerance on the enthalpy residual
 *                             |H_products(T) - H_reactants| in J per mol of fuel
 * @param maxIterations        maximum number of Newton steps after the initial guess
 * @param atomBalanceThreshold maximum allowed absolute atom-balance residual, mol per mol of fuel
 */
public record SolverProperties(double enthalpyTolerance, int maxIterations, double atomBalanceThreshold) {

    /** Service-pinned values; every job stores them alongside its results. */
    public static SolverProperties pinned() {
        return new SolverProperties(1.0e-3, 50, 1.0e-9);
    }
}
