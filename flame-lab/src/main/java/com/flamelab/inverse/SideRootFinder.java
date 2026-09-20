package com.flamelab.inverse;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.flamelab.fuel.Fuel;
import com.flamelab.job.ErrorInfo;
import com.flamelab.job.ErrorType;

/**
 * Outer root find on one branch of the flame-temperature curve: adjusts the
 * equivalence ratio until the forward-solved flame temperature meets the
 * target within the pinned temperature tolerance. The located peak anchors
 * one end of the bracket; a multiplicative walk away from the peak finds the
 * other end, then bisection closes in. Every trial ratio — walk and bisection
 * alike — is recorded in order with its temperature deviation, so the trace
 * shows exactly how the ratio converged. A side that runs out of steps fails
 * and reports no ratio instead of a not-actually-converged one.
 */
@Component
public class SideRootFinder {

    /**
     * Bracketing-walk limits. A target so close to the intake temperature
     * that the root would lie outside these ratios is treated as unreachable.
     */
    static final double PHI_FLOOR = 1.0e-4;
    static final double PHI_CEILING = 1.0e4;

    /** Multiplicative step of the bracketing walk away from the peak. */
    private static final double WALK_FACTOR = 2.0;

    private final ForwardFlameEvaluator forward;
    private final InverseProperties properties;

    public SideRootFinder(ForwardFlameEvaluator forward, InverseProperties properties) {
        this.forward = forward;
        this.properties = properties;
    }

    public SideResult findRoot(Fuel fuel, double intakeTemperature, double targetTemperature,
                               Side side, PeakResult peak) {
        List<PhiTracePoint> trace = new ArrayList<>();
        // Step 0: the located peak anchors the near end of the bracket.
        trace.add(new PhiTracePoint(0, peak.equivalenceRatio(), peak.temperature() - targetTemperature));
        if (Math.abs(peak.temperature() - targetTemperature) <= properties.temperatureTolerance()) {
            return SideResult.converged(side, peak.equivalenceRatio(), trace);
        }

        // Walk away from the peak until the flame temperature drops to the
        // target: the last two trials then bracket the root on this branch.
        double nearPhi = peak.equivalenceRatio();
        double farPhi = nearPhi;
        double farDeviation = peak.temperature() - targetTemperature;
        while (farDeviation > properties.temperatureTolerance()) {
            farPhi = side == Side.LEAN ? farPhi / WALK_FACTOR : farPhi * WALK_FACTOR;
            if (farPhi < PHI_FLOOR || farPhi > PHI_CEILING) {
                return SideResult.failed(side, trace, new ErrorInfo(ErrorType.TARGET_UNREACHABLE,
                        "target " + targetTemperature + " K is below the flame temperature approached"
                                + " at the " + side.name().toLowerCase() + " end of the curve"));
            }
            double temperature = forward.evaluate(fuel, farPhi, intakeTemperature).flameTemperature();
            farDeviation = temperature - targetTemperature;
            trace.add(new PhiTracePoint(trace.size(), farPhi, farDeviation));
            if (Math.abs(farDeviation) <= properties.temperatureTolerance()) {
                return SideResult.converged(side, farPhi, trace);
            }
            if (trace.size() >= properties.maxOuterSteps()) {
                return notConverged(side, trace, targetTemperature);
            }
        }

        // Bisect the bracket: on the lean branch temperature rises with phi,
        // on the rich branch it falls, so the half to keep differs by side.
        double lo = side == Side.LEAN ? farPhi : nearPhi;
        double hi = side == Side.LEAN ? nearPhi : farPhi;
        while (true) {
            double mid = 0.5 * (lo + hi);
            double temperature = forward.evaluate(fuel, mid, intakeTemperature).flameTemperature();
            double deviation = temperature - targetTemperature;
            trace.add(new PhiTracePoint(trace.size(), mid, deviation));
            if (Math.abs(deviation) <= properties.temperatureTolerance()) {
                return SideResult.converged(side, mid, trace);
            }
            if (trace.size() >= properties.maxOuterSteps()) {
                return notConverged(side, trace, targetTemperature);
            }
            if (side == Side.LEAN) {
                if (deviation < 0.0) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            } else {
                if (deviation < 0.0) {
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
        }
    }

    private SideResult notConverged(Side side, List<PhiTracePoint> trace, double targetTemperature) {
        double lastDeviation = trace.get(trace.size() - 1).temperatureDeviation();
        return SideResult.failed(side, trace, new ErrorInfo(ErrorType.EQUIVALENCE_RATIO_NOT_CONVERGED,
                "temperature deviation " + lastDeviation + " K from target " + targetTemperature
                        + " K still above tolerance " + properties.temperatureTolerance()
                        + " K after " + trace.size() + " outer steps on the "
                        + side.name().toLowerCase() + " branch"));
    }
}
