package com.flamelab.inverse;

/**
 * Result of a successful inverse solve: the located peak and the branch
 * solution(s) that were requested. A single-side request leaves the other side
 * null; BOTH always returns two independently re-checked solutions.
 */
public record InverseOutcome(Peak peak,
                             SideRootFinder.SideResult lean,
                             SideRootFinder.SideResult rich) {
}
