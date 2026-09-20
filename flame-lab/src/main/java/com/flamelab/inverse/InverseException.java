package com.flamelab.inverse;

import java.util.List;

import com.flamelab.job.ErrorType;

/**
 * Failure of an inverse search (one branch did not converge, or the target
 * temperature is above the reachable peak). Carries the outer probe sequences
 * accumulated so far and — for an unreachable target — the located peak, so the
 * persisted job shows how the search walked and how high the flame can actually
 * get. A side that failed never carries a fabricated equivalence ratio.
 */
public class InverseException extends RuntimeException {

    private final ErrorType type;
    private final Peak peak;
    private final List<OuterStep> leanSteps;
    private final List<OuterStep> richSteps;

    public InverseException(ErrorType type, String message, Peak peak,
                            List<OuterStep> leanSteps, List<OuterStep> richSteps) {
        super(message);
        this.type = type;
        this.peak = peak;
        this.leanSteps = leanSteps == null ? List.of() : List.copyOf(leanSteps);
        this.richSteps = richSteps == null ? List.of() : List.copyOf(richSteps);
    }

    public ErrorType type() {
        return type;
    }

    public Peak peak() {
        return peak;
    }

    public List<OuterStep> leanSteps() {
        return leanSteps;
    }

    public List<OuterStep> richSteps() {
        return richSteps;
    }
}
