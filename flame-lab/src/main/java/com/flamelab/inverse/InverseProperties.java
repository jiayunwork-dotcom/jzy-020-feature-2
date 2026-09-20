package com.flamelab.inverse;

/**
 * Pinned controls for the inverse (target-temperature -> equivalence-ratio)
 * search.
 *
 * @param temperatureTolerance absolute acceptance window |T_flame(phi) - T_target| in kelvin;
 *                             a reported phi must pass a fresh forward re-check inside it
 * @param maxOuterSteps        maximum number of forward-solve probes one side may spend
 *                             while bracketing and bisecting its root
 */
public record InverseProperties(double temperatureTolerance, int maxOuterSteps) {

    /** Service-pinned values; every inverse job stores them alongside its results. */
    public static InverseProperties pinned() {
        return new InverseProperties(1.0, 60);
    }
}
