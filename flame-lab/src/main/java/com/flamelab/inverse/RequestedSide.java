package com.flamelab.inverse;

import java.util.Optional;

/**
 * Which branch(es) of the flame-temperature curve an inverse job is asked to
 * solve: the lean root, the rich root, or both.
 */
public enum RequestedSide {
    LEAN,
    RICH,
    BOTH;

    public boolean includes(Side side) {
        return this == BOTH || name().equals(side.name());
    }

    /** Case-insensitive parse; empty when the value is not one of LEAN/RICH/BOTH. */
    public static Optional<RequestedSide> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
