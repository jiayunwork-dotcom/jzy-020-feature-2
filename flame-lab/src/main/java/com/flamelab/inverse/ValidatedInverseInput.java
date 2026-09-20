package com.flamelab.inverse;

import com.flamelab.fuel.Fuel;

/**
 * An inverse request that passed validation: fuel resolved against the shared
 * catalog, positive finite intake and target temperatures, and a legal side.
 */
public record ValidatedInverseInput(Fuel fuel, double intakeTemperature,
                                    double targetTemperature, RequestSide side) {
}
