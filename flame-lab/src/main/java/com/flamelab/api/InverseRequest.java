package com.flamelab.api;

/**
 * Inverse-solve request body: fuel, intake temperature, target flame
 * temperature and which side(s) of the peak to solve. Boxed types so missing
 * fields are distinguishable from real values and get their own typed error.
 */
public record InverseRequest(String fuel, Double intakeTemperature, Double targetTemperature, String side) {
}
