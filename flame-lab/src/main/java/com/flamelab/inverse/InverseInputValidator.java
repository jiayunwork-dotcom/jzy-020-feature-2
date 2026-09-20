package com.flamelab.inverse;

import org.springframework.stereotype.Component;

import com.flamelab.fuel.Fuel;
import com.flamelab.fuel.FuelCatalog;
import com.flamelab.job.ErrorType;
import com.flamelab.job.InputValidationException;

/**
 * Validation for inverse (target-temperature) requests. All rejection happens
 * before the outer search — and therefore before any forward balance — starts:
 * unknown fuel, missing or non-finite numbers, non-positive intake or target
 * temperature, or an illegal side selector each get a typed error.
 */
@Component
public class InverseInputValidator {

    private final FuelCatalog fuelCatalog;

    public InverseInputValidator(FuelCatalog fuelCatalog) {
        this.fuelCatalog = fuelCatalog;
    }

    public ValidatedInverseInput validate(String fuelId, Double intakeTemperature,
                                          Double targetTemperature, String sideText) {
        if (fuelId == null || fuelId.isBlank()) {
            throw new InputValidationException(ErrorType.MISSING_FIELD, "field 'fuel' is required");
        }
        if (intakeTemperature == null) {
            throw new InputValidationException(ErrorType.MISSING_FIELD,
                    "field 'intakeTemperature' is required");
        }
        if (targetTemperature == null) {
            throw new InputValidationException(ErrorType.MISSING_FIELD,
                    "field 'targetTemperature' is required");
        }
        if (sideText == null || sideText.isBlank()) {
            throw new InputValidationException(ErrorType.MISSING_FIELD, "field 'side' is required");
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
                    "target temperature must be positive, got " + targetTemperature);
        }
        RequestSide side = RequestSide.fromText(sideText);
        if (side == null) {
            throw new InputValidationException(ErrorType.ILLEGAL_SIDE,
                    "field 'side' must be one of LEAN, RICH, BOTH; got '" + sideText + "'");
        }
        Fuel fuel = fuelCatalog.find(fuelId).orElseThrow(() -> new InputValidationException(
                ErrorType.UNKNOWN_FUEL,
                "unknown fuel '" + fuelId + "'; supported fuels: " + String.join(", ", fuelCatalog.supportedIds())));
        return new ValidatedInverseInput(fuel, intakeTemperature, targetTemperature, side);
    }
}
