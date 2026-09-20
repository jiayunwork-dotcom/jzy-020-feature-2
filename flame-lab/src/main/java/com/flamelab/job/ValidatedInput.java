package com.flamelab.job;

import com.flamelab.fuel.Fuel;

/** A solve request that passed validation, with the fuel resolved against the catalog. */
public record ValidatedInput(Fuel fuel, double equivalenceRatio, double intakeTemperature) {
}
