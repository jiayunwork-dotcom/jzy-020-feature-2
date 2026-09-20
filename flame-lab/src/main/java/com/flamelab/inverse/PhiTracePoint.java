package com.flamelab.inverse;

/**
 * One recorded trial of the outer equivalence-ratio search: the equivalence
 * ratio that was tried and the signed deviation T_forward(phi) - target (K)
 * at that point. Each side accumulates its own sequence, in order.
 */
public record PhiTracePoint(int step, double equivalenceRatio, double temperatureDeviation) {
}
