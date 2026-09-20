package com.flamelab.inverse;

import org.springframework.stereotype.Component;

import com.flamelab.fuel.Fuel;
import com.flamelab.fuel.FuelCatalog;
import com.flamelab.job.ErrorType;
import com.flamelab.job.InputValidationException;

/**
 * Input validation for inverse-solve requests. Unknown fuels, non-positive
 * intake or target temperatures, non-finite numbers, missing fields and
 * illegal side values are all rejected with typed errors before the outer
 * search starts.
 */
@Component
public class InverseInputValidator {

    private final FuelCatalog fuelCatalog;

    public InverseInputValidator(FuelCatalog fuelCatalog) {
        this.fuelCatalog = fuelCatalog;
    }

    public ValidatedInverseInput validate(String fuelId, Double intakeTemperature,
                                          Double targetTemperature, String side) {
        if (fuelId == null || fuelId.isBlank()) {
            throw new InputValidationException(ErrorType.MISSING_FIELD, "field 'fuel' is required");
        }
        if (intakeTemperature == null) {
            throw new InputValidationException(ErrorType.MISSING_FIELD, "field 'intakeTemperature' is required");
        }
        if (targetTemperature == null) {
            throw new InputValidationException(ErrorType.MISSING_FIELD, "field 'targetTemperature' is required");
        }
        if (side == null || side.isBlank()) {
            throw new InputValidationException(ErrorType.MISSING_FIELD,
                    "field 'side' is required (LEAN, RICH or BOTH)");
        }
        if (!Double.isFinite(intakeTemperature)) {
            throw new InputValidationException(ErrorType.NON_FINITE_VALUE,
                    "field 'intakeTemperature' must be a finite number");
        }
        if (!Double.isFinite(targetTemperature)) {
            throw new InputValidationException(ErrorType.NON_FINITE_VALUE,
                    "field 'targetTemperature' must be a finite number");
        }
        if (intakeTemperature <= 0.0) {
            throw new InputValidationException(ErrorType.NON_POSITIVE_INTAKE_TEMPERATURE,
                    "intake temperature must be a positive thermodynamic temperature, got " + intakeTemperature);
        }
        if (targetTemperature <= 0.0) {
            throw new InputValidationException(ErrorType.NON_POSITIVE_TARGET_TEMPERATURE,
                    "target flame temperature must be a positive thermodynamic temperature, got "
                            + targetTemperature);
        }
        RequestedSide requestedSide = RequestedSide.parse(side).orElseThrow(() ->
                new InputValidationException(ErrorType.INVALID_SIDE,
                        "field 'side' must be one of LEAN, RICH, BOTH; got '" + side + "'"));
        Fuel fuel = fuelCatalog.find(fuelId).orElseThrow(() -> new InputValidationException(
                ErrorType.UNKNOWN_FUEL,
                "unknown fuel '" + fuelId + "'; supported fuels: " + String.join(", ", fuelCatalog.supportedIds())));
        return new ValidatedInverseInput(fuel, intakeTemperature, targetTemperature, requestedSide);
    }
}
