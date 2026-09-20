package com.flamelab.thermo;

/** Raised when a heat-capacity polynomial is evaluated outside its pinned temperature window. */
public class TemperatureOutOfRangeException extends RuntimeException {

    private final double temperature;
    private final double minTemperature;
    private final double maxTemperature;

    public TemperatureOutOfRangeException(double temperature, double minTemperature, double maxTemperature) {
        super("temperature " + temperature + " K is outside the pinned Cp window ["
                + minTemperature + ", " + maxTemperature + "] K");
        this.temperature = temperature;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
    }

    public double temperature() {
        return temperature;
    }

    public double minTemperature() {
        return minTemperature;
    }

    public double maxTemperature() {
        return maxTemperature;
    }
}
