package com.flamelab.inverse.api;

/**
 * Inverse request body. Boxed types so missing fields are distinguishable from
 * real values and get their own typed error. {@code side} is one of LEAN,
 * RICH, BOTH.
 */
public record InverseSolveRequest(String fuel, Double intakeTemperature,
                                  Double targetTemperature, String side) {
}
