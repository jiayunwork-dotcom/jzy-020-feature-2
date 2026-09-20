package com.flamelab.thermo;

/**
 * Piecewise NASA-style heat-capacity polynomial for one species:
 * Cp/R = a1 + a2 T + a3 T^2 + a4 T^3 + a5 T^4, with one coefficient set below
 * and one above a pinned switch temperature. The same polynomial instance is
 * used no matter whether the species currently sits on the reactant or the
 * product side of the balance.
 */
public final class NasaPolynomial {

    static final double GAS_CONSTANT = 8.31446261815324; // J/(mol K)

    private final double[] low;
    private final double[] high;
    private final double switchTemperature;
    private final ThermoLimits limits;

    public NasaPolynomial(double[] low, double[] high, double switchTemperature, ThermoLimits limits) {
        if (low.length != 5 || high.length != 5) {
            throw new IllegalArgumentException("NASA Cp polynomials need exactly 5 coefficients per branch");
        }
        this.low = low.clone();
        this.high = high.clone();
        this.switchTemperature = switchTemperature;
        this.limits = limits;
    }

    /** Cp in J/(mol K). Fails out-of-range instead of extrapolating. */
    public double cp(double temperature) {
        limits.check(temperature);
        double[] a = branch(temperature);
        return GAS_CONSTANT * (a[0] + a[1] * temperature + a[2] * temperature * temperature
                + a[3] * Math.pow(temperature, 3) + a[4] * Math.pow(temperature, 4));
    }

    /** Sensible enthalpy integral from the reference temperature to {@code temperature}, in J/mol. */
    public double sensibleEnthalpy(double temperature) {
        limits.check(temperature);
        return antiderivative(temperature) - antiderivative(limits.referenceTemperature());
    }

    private double[] branch(double temperature) {
        return temperature <= switchTemperature ? low : high;
    }

    /**
     * Continuous antiderivative of Cp dT: integrates the low branch up to the
     * switch temperature and the high branch beyond it, so the two branches
     * join without a jump.
     */
    private double antiderivative(double temperature) {
        if (temperature <= switchTemperature) {
            return integrate(low, temperature);
        }
        return integrate(low, switchTemperature)
                + integrate(high, temperature) - integrate(high, switchTemperature);
    }

    private static double integrate(double[] a, double t) {
        return GAS_CONSTANT * (a[0] * t + a[1] * t * t / 2.0 + a[2] * Math.pow(t, 3) / 3.0
                + a[3] * Math.pow(t, 4) / 4.0 + a[4] * Math.pow(t, 5) / 5.0);
    }
}
