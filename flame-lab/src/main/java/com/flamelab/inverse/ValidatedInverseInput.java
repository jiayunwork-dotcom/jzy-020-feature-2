package com.flamelab.inverse;

import com.flamelab.fuel.Fuel;

/** An inverse-solve request that passed validation, with fuel and side resolved. */
public record ValidatedInverseInput(Fuel fuel, double intakeTemperature, double targetTemperature,
                                    RequestedSide side) {
}
