package com.flamelab.inverse;

/**
 * One recorded probe of the outer root-finding loop: the equivalence ratio that
 * was tried and the resulting forward-solve temperature deviation
 * T_flame(phi) - T_target, in kelvin. Lean and rich branches each accumulate
 * their own ordered sequence; they are never merged into one curve.
 */
public record OuterStep(int step, double equivalenceRatio, double temperatureDeviation) {
}
