package com.flamelab.inverse;

/**
 * The located peak of the flame-temperature curve for one fuel/intake pair:
 * the equivalence ratio at the maximum and the forward-solved flame
 * temperature there. No target above this temperature is reachable.
 */
public record PeakResult(double equivalenceRatio, double temperature) {
}
