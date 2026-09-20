package com.flamelab.api;

/**
 * Solve request body. Boxed types so that missing fields are distinguishable
 * from real values and get their own typed error.
 */
public record SolveRequest(String fuel, Double equivalenceRatio, Double intakeTemperature) {
}
