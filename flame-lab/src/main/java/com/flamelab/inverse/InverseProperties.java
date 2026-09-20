package com.flamelab.inverse;

/**
 * Pinned controls for the outer equivalence-ratio search of the inverse solve.
 *
 * @param temperatureTolerance absolute tolerance on |T_forward(phi) - target| in K;
 *                             a root is only accepted inside this band
 * @param maxOuterSteps        maximum recorded phi trials per side (bracketing
 *                             walk plus bisection); running out fails that side
 */
public record InverseProperties(double temperatureTolerance, int maxOuterSteps) {

    /** Service-pinned values; every inverse job stores them alongside its results. */
    public static InverseProperties pinned() {
        return new InverseProperties(0.5, 80);
    }
}
