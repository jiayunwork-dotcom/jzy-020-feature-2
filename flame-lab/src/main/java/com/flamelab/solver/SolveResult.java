package com.flamelab.solver;

import java.util.List;

/** Successful solve: the full residual curve plus the converged adiabatic flame temperature. */
public record SolveResult(List<IterationPoint> iterations, double finalTemperature) {

    public SolveResult {
        iterations = List.copyOf(iterations);
    }
}
