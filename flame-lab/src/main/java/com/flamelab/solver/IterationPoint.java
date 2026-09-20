package com.flamelab.solver;

/**
 * One recorded iteration of the temperature loop: the temperature that was
 * tried and the enthalpy residual H_products(T) - H_reactants at that point
 * (J per mol of fuel).
 */
public record IterationPoint(int step, double temperature, double enthalpyResidual) {
}
