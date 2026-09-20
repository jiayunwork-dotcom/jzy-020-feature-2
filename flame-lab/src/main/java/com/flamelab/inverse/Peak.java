package com.flamelab.inverse;

/**
 * Located peak of the flame-temperature versus equivalence-ratio curve: the
 * equivalence ratio at which the forward balance runs hottest and the peak
 * adiabatic flame temperature itself. The peak depends on fuel and intake
 * temperature, so it is re-located by repeated forward solves for every job.
 */
public record Peak(double equivalenceRatio, double temperature) {
}
