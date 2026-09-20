package com.flamelab.thermo;

/**
 * Pinned validity window of the built-in heat-capacity polynomials plus the
 * reference state for sensible enthalpy. Evaluating Cp (or its integral)
 * outside [minTemperature, maxTemperature] is an out-of-range error and fails
 * the owning job; out-of-range temperatures are never silently extrapolated.
 */
public record ThermoLimits(double minTemperature, double maxTemperature, double referenceTemperature) {

    /** Service-pinned thermodynamic limits (kelvin). */
    public static ThermoLimits pinned() {
        return new ThermoLimits(250.0, 5000.0, 298.15);
    }

    public void check(double temperature) {
        if (!Double.isFinite(temperature) || temperature < minTemperature || temperature > maxTemperature) {
            throw new TemperatureOutOfRangeException(temperature, minTemperature, maxTemperature);
        }
    }

    public double clamp(double temperature) {
        return Math.min(maxTemperature, Math.max(minTemperature, temperature));
    }
}
