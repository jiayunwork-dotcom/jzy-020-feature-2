package com.flamelab.inverse;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.flamelab.fuel.Fuel;
import com.flamelab.job.ErrorType;

/**
 * Outer root-finding on one branch of the flame-temperature curve. Given the
 * located peak and a target below it, finds the equivalence ratio on either the
 * lean branch (temperature increases with phi) or the rich branch (temperature
 * decreases with phi) whose forward flame temperature matches the target.
 *
 * Every temperature observation is a genuine forward solve. Each branch is
 * monotone by construction, so the search first walks outward from the peak
 * until it brackets the target, then bisects inside the bracket. Every probe is
 * appended, in order, to that side's own convergence sequence — lean and rich
 * sequences are accumulated separately by the caller and never merged.
 *
 * The probe budget is pinned per side and split between bracket walking and
 * bisection; if either phase exhausts it without pressing the deviation inside
 * the pinned temperature tolerance, the side fails instead of reporting a phi
 * that was never aligned with the target.
 */
@Component
public class SideRootFinder {

    /** Outward walking step when building the bracket, multiplicative in phi. */
    private static final double BRACKET_STEP = 1.25;
    /** Searchable phi window; brackets never walk outside it. */
    private static final double PHI_FLOOR = 1.0e-3;
    private static final double PHI_CEIL = 1.0e2;
    /** Probes spent walking outward to build the bracket; the rest of the budget goes to bisection. */
    private static final int BRACKET_BUDGET = 25;

    private final ForwardEvaluator forward;
    private final InverseProperties properties;

    public SideRootFinder(ForwardEvaluator forward, InverseProperties properties) {
        this.forward = forward;
        this.properties = properties;
    }

    /**
     * @param leanBranch true for the lean (increasing) branch below the peak phi,
     *                   false for the rich (decreasing) branch above it
     */
    public SideResult find(Fuel fuel, double intakeTemperature, double targetTemperature,
                           Peak peak, boolean leanBranch) {
        List<OuterStep> sequence = new ArrayList<>();

        // On the lean branch temperature rises with phi toward the peak; on the
        // rich branch it falls as phi moves past the peak. dev = T(phi) - target.
        // Lean needs dev<=0 below the root and dev>=0 at the peak; rich the mirror.
        ForwardEvaluator.Probe peakProbe = forward.evaluate(fuel, peak.equivalenceRatio(), intakeTemperature);
        recordStep(sequence, peakProbe, targetTemperature);
        if (peakProbe.deviation(targetTemperature) < 0.0) {
            throw new InverseException(ErrorType.TARGET_UNREACHABLE,
                    "target temperature " + targetTemperature + " K is above the reachable peak "
                            + peakProbe.temperature() + " K",
                    new Peak(peak.equivalenceRatio(), peakProbe.temperature()),
                    leanBranch ? List.copyOf(sequence) : List.of(),
                    leanBranch ? List.of() : List.copyOf(sequence));
        }

        Bracket bracket = bracket(fuel, intakeTemperature, targetTemperature,
                peakProbe, leanBranch, sequence);
        // The two edges are kept in phi order (left < right). On the lean branch
        // the far edge with dev<=0 is on the left; on the rich branch it is on the
        // right. Bisection tracks each edge's own deviation sign, never the branch.
        ForwardEvaluator.Probe left;
        ForwardEvaluator.Probe right;
        if (bracket.outer.equivalenceRatio() < bracket.inner.equivalenceRatio()) {
            left = bracket.outer;
            right = bracket.inner;
        } else {
            left = bracket.inner;
            right = bracket.outer;
        }
        return bisect(fuel, intakeTemperature, targetTemperature, leanBranch, sequence, left, right);
    }

    private Bracket bracket(Fuel fuel, double intakeTemperature, double targetTemperature,
                            ForwardEvaluator.Probe inner, boolean leanBranch, List<OuterStep> sequence) {
        double outerPhi = inner.equivalenceRatio();

        for (int walk = 0; walk < BRACKET_BUDGET; walk++) {
            outerPhi = leanBranch
                    ? Math.max(PHI_FLOOR, outerPhi / BRACKET_STEP)
                    : Math.min(PHI_CEIL, outerPhi * BRACKET_STEP);
            ForwardEvaluator.Probe outer = forward.evaluate(fuel, outerPhi, intakeTemperature);
            recordStep(sequence, outer, targetTemperature);

            if (!outer.finite()) {
                // The flame root fell outside the pinned Cp window at this extreme
                // ratio. Retract toward the peak until a finite, target-crossing edge
                // exists; bisection only ever brackets with finite probes.
                ForwardEvaluator.Probe retracted = retractToFiniteBelowTarget(
                        fuel, intakeTemperature, targetTemperature,
                        inner.equivalenceRatio(), outerPhi, leanBranch, sequence);
                return new Bracket(retracted, inner);
            }
            // A temperature at/below target closes the bracket.
            if (outer.deviation(targetTemperature) <= 0.0) {
                return new Bracket(outer, inner);
            }
            if ((leanBranch && outerPhi <= PHI_FLOOR * (1.0 + 1.0e-12))
                    || (!leanBranch && outerPhi >= PHI_CEIL * (1.0 - 1.0e-12))) {
                break;
            }
        }
        throw failedSide(leanBranch, targetTemperature, sequence,
                "could not bracket target " + targetTemperature + " K on the "
                        + (leanBranch ? "lean" : "rich") + " branch within the phi window ["
                        + PHI_FLOOR + ", " + PHI_CEIL + "]");
    }

    /**
     * Binary retraction from an un-evaluable extreme probe toward the peak edge,
     * returning the first finite probe whose temperature is at/below the target.
     * Every retraction is itself a recorded forward solve.
     */
    private ForwardEvaluator.Probe retractToFiniteBelowTarget(Fuel fuel, double intakeTemperature,
                                                               double targetTemperature,
                                                               double innerPhi, double extremePhi,
                                                               boolean leanBranch, List<OuterStep> sequence) {
        double boundPhi = extremePhi;
        for (int retraction = 0; retraction < BRACKET_BUDGET; retraction++) {
            double midPhi = 0.5 * (innerPhi + boundPhi);
            ForwardEvaluator.Probe probe = forward.evaluate(fuel, midPhi, intakeTemperature);
            recordStep(sequence, probe, targetTemperature);
            if (probe.finite() && probe.deviation(targetTemperature) <= 0.0) {
                return probe;
            }
            // Finite but still above target: the crossing (or the Cp-window edge)
            // lies between this point and the un-evaluable extreme; keep retracting.
            boundPhi = midPhi;
        }
        throw failedSide(leanBranch, targetTemperature, sequence,
                "could not find a finite target-crossing edge near the pinned Cp window");
    }

    private SideResult bisect(Fuel fuel, double intakeTemperature, double targetTemperature,
                              boolean leanBranch, List<OuterStep> sequence,
                              ForwardEvaluator.Probe left, ForwardEvaluator.Probe right) {
        // Edges stay in phi order; dev(left) and dev(right) have opposite signs
        // (one is at/below target, the other at/above it via the peak edge).
        int bisectionBudget = properties.maxOuterSteps() - BRACKET_BUDGET;
        for (int step = 0; step < bisectionBudget; step++) {
            if (acceptable(left, targetTemperature)) {
                return accept(fuel, intakeTemperature, targetTemperature, leanBranch, sequence, left);
            }
            if (acceptable(right, targetTemperature)) {
                return accept(fuel, intakeTemperature, targetTemperature, leanBranch, sequence, right);
            }

            double midPhi = 0.5 * (left.equivalenceRatio() + right.equivalenceRatio());
            ForwardEvaluator.Probe mid = forward.evaluate(fuel, midPhi, intakeTemperature);
            recordStep(sequence, mid, targetTemperature);
            if (acceptable(mid, targetTemperature)) {
                return accept(fuel, intakeTemperature, targetTemperature, leanBranch, sequence, mid);
            }
            // Replace whichever edge shares mid's deviation sign: that keeps the
            // root bracketed regardless of which branch is monotone which way.
            if (Math.signum(mid.deviation(targetTemperature)) == Math.signum(left.deviation(targetTemperature))) {
                left = mid;
            } else {
                right = mid;
            }
        }
        throw failedSide(leanBranch, targetTemperature, sequence,
                "outer search used all " + properties.maxOuterSteps()
                        + " probes without pressing the temperature deviation into "
                        + properties.temperatureTolerance() + " K");
    }

    private boolean acceptable(ForwardEvaluator.Probe probe, double targetTemperature) {
        return probe.finite() && probe.equivalenceRatio() > 0.0
                && Math.abs(probe.deviation(targetTemperature)) <= properties.temperatureTolerance();
    }

    private SideResult accept(Fuel fuel, double intakeTemperature, double targetTemperature,
                              boolean leanBranch, List<OuterStep> sequence, ForwardEvaluator.Probe probe) {
        // Fresh forward re-check of the reported phi — the number handed back must
        // itself land inside the pinned temperature window, with phi positive.
        ForwardEvaluator.Probe recheck = forward.evaluate(fuel, probe.equivalenceRatio(), intakeTemperature);
        double deviation = recheck.finite() ? recheck.deviation(targetTemperature) : Double.NaN;
        if (acceptable(recheck, targetTemperature)) {
            return new SideResult(recheck.equivalenceRatio(), recheck.temperature(), deviation,
                    recheck.productMoleFractions(), recheck.atomResiduals(),
                    recheck.maxAtomResidual(), List.copyOf(sequence));
        }
        throw failedSide(leanBranch, targetTemperature, sequence,
                "forward re-check at phi=" + probe.equivalenceRatio()
                        + " missed target by " + deviation + " K");
    }

    private void recordStep(List<OuterStep> sequence, ForwardEvaluator.Probe probe, double targetTemperature) {
        double deviation = probe.finite() ? probe.deviation(targetTemperature) : Double.NaN;
        sequence.add(new OuterStep(sequence.size(), probe.equivalenceRatio(), deviation));
    }

    private InverseException failedSide(boolean leanBranch, double targetTemperature,
                                        List<OuterStep> sequence, String message) {
        return new InverseException(ErrorType.SIDE_NOT_CONVERGED,
                message + " (target " + targetTemperature + " K)",
                null,
                leanBranch ? List.copyOf(sequence) : List.of(),
                leanBranch ? List.of() : List.copyOf(sequence));
    }

    private record Bracket(ForwardEvaluator.Probe outer, ForwardEvaluator.Probe inner) {
    }

    /** One converged branch solution: phi, its fresh forward re-check, and that branch's probe sequence. */
    public record SideResult(double equivalenceRatio, double flameTemperature, double temperatureDeviation,
                             java.util.Map<String, Double> productMoleFractions,
                             java.util.Map<String, Double> atomResiduals, double maxAtomResidual,
                             List<OuterStep> sequence) {

        public SideResult {
            sequence = List.copyOf(sequence);
        }
    }
}
