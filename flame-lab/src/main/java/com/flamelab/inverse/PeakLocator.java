package com.flamelab.inverse;

import org.springframework.stereotype.Component;

import com.flamelab.fuel.Fuel;

/**
 * Locates the peak of the flame-temperature curve T(phi) for a fuel/intake
 * pair by golden-section maximization. The curve is unimodal — temperature
 * rises as the mixture richens toward stoichiometric and falls off rich of
 * it — so the section converges to the single maximum without assuming where
 * it sits. Every evaluation is a real forward solve; the reported peak
 * temperature is itself forward-solved at the reported peak ratio.
 */
@Component
public class PeakLocator {

    /**
     * Search bracket in equivalence ratio. The peak of every supported fuel
     * sits near stoichiometric, far inside this interval, and the curve is
     * monotone from each bracket end up to the peak.
     */
    static final double PHI_BRACKET_LOW = 0.2;
    static final double PHI_BRACKET_HIGH = 5.0;

    /** Golden-section iterations; shrinks the bracket by a factor of ~0.618 each step. */
    private static final int ITERATIONS = 60;

    private static final double INV_GOLDEN = (Math.sqrt(5.0) - 1.0) / 2.0;

    private final ForwardFlameEvaluator forward;

    public PeakLocator(ForwardFlameEvaluator forward) {
        this.forward = forward;
    }

    public PeakResult locate(Fuel fuel, double intakeTemperature) {
        double lo = PHI_BRACKET_LOW;
        double hi = PHI_BRACKET_HIGH;
        double x1 = hi - INV_GOLDEN * (hi - lo);
        double x2 = lo + INV_GOLDEN * (hi - lo);
        double t1 = forward.evaluate(fuel, x1, intakeTemperature).flameTemperature();
        double t2 = forward.evaluate(fuel, x2, intakeTemperature).flameTemperature();
        for (int i = 0; i < ITERATIONS; i++) {
            if (t1 < t2) {
                lo = x1;
                x1 = x2;
                t1 = t2;
                x2 = lo + INV_GOLDEN * (hi - lo);
                t2 = forward.evaluate(fuel, x2, intakeTemperature).flameTemperature();
            } else {
                hi = x2;
                x2 = x1;
                t2 = t1;
                x1 = hi - INV_GOLDEN * (hi - lo);
                t1 = forward.evaluate(fuel, x1, intakeTemperature).flameTemperature();
            }
        }
        double peakPhi = 0.5 * (lo + hi);
        double peakTemperature = forward.evaluate(fuel, peakPhi, intakeTemperature).flameTemperature();
        return new PeakResult(peakPhi, peakTemperature);
    }
}
