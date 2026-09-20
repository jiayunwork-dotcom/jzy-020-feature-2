package com.flamelab.job;

import org.springframework.stereotype.Component;

import com.flamelab.fuel.Fuel;
import com.flamelab.fuel.FuelCatalog;

/**
 * Input validation for solve requests. Unknown fuels, non-positive
 * equivalence ratios and non-positive intake temperatures are rejected before
 * any iteration starts; missing fields and non-finite numbers get their own
 * typed errors.
 */
@Component
public class InputValidator {

    private final FuelCatalog fuelCatalog;

    public InputValidator(FuelCatalog fuelCatalog) {
        this.fuelCatalog = fuelCatalog;
    }

    public ValidatedInput validate(String fuelId, Double equivalenceRatio, Double intakeTemperature) {
        if (fuelId == null || fuelId.isBlank()) {
            throw new InputValidationException(ErrorType.MISSING_FIELD, "field 'fuel' is required");
        }
        if (equivalenceRatio == null) {
            throw new InputValidationException(ErrorType.MISSING_FIELD, "field 'equivalenceRatio' is required");
        }
        if (intakeTemperature == null) {
            throw new InputValidationException(ErrorType.MISSING_FIELD, "field 'intakeTemperature' is required");
        }
        if (!Double.isFinite(equivalenceRatio)) {
            throw new InputValidationException(ErrorType.NON_FINITE_VALUE,
                    "field 'equivalenceRatio' must be a finite number");
        }
        if (!Double.isFinite(intakeTemperature)) {
            throw new InputValidationException(ErrorType.NON_FINITE_VALUE,
                    "field 'intakeTemperature' must be a finite number");
        }
        if (equivalenceRatio <= 0.0) {
            throw new InputValidationException(ErrorType.NON_POSITIVE_EQUIVALENCE_RATIO,
                    "equivalence ratio must be positive, got " + equivalenceRatio);
        }
        if (intakeTemperature <= 0.0) {
            throw new InputValidationException(ErrorType.NON_POSITIVE_INTAKE_TEMPERATURE,
                    "intake temperature must be a positive thermodynamic temperature, got " + intakeTemperature);
        }
        Fuel fuel = fuelCatalog.find(fuelId).orElseThrow(() -> new InputValidationException(
                ErrorType.UNKNOWN_FUEL,
                "unknown fuel '" + fuelId + "'; supported fuels: " + String.join(", ", fuelCatalog.supportedIds())));
        return new ValidatedInput(fuel, equivalenceRatio, intakeTemperature);
    }
}
