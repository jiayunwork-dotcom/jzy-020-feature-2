package com.flamelab.solver;

import java.util.List;

import com.flamelab.job.ErrorType;

/**
 * A solve that failed. Carries the residual curve accumulated so far so the
 * failed job still stores its iteration history; the solver never fabricates
 * a plausible-looking temperature on failure.
 */
public class SolveException extends RuntimeException {

    private final ErrorType type;
    private final List<IterationPoint> iterations;

    public SolveException(ErrorType type, String message, List<IterationPoint> iterations) {
        super(message);
        this.type = type;
        this.iterations = List.copyOf(iterations);
    }

    public ErrorType type() {
        return type;
    }

    public List<IterationPoint> iterations() {
        return iterations;
    }
}
