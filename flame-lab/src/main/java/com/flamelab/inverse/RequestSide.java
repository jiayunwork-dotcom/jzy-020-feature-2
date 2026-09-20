package com.flamelab.inverse;

/**
 * Which branch of the (non-monotonic) flame-temperature versus equivalence-ratio
 * curve the caller wants the inverse job to search. The temperature curve peaks
 * near stoichiometric, so a target below the peak has one root on each branch.
 */
public enum RequestSide {

    /** Increasing branch below the peak equivalence ratio. */
    LEAN,
    /** Decreasing branch above the peak equivalence ratio. */
    RICH,
    /** Both branches; the job accumulates a separate convergence sequence per side. */
    BOTH;

    /** Parses the request value, rejecting anything outside the pinned vocabulary. */
    public static RequestSide fromText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toUpperCase();
        return switch (normalized) {
            case "LEAN" -> LEAN;
            case "RICH" -> RICH;
            case "BOTH" -> BOTH;
            default -> null;
        };
    }
}
