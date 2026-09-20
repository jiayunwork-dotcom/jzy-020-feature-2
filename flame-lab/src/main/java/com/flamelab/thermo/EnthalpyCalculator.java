package com.flamelab.thermo;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Enthalpy and heat capacity of a mixture of ideal-gas species, built from the
 * single shared thermodynamic database. Constant pressure, kinetic energy ignored.
 */
@Component
public class EnthalpyCalculator {

    private final ThermoDatabase database;

    public EnthalpyCalculator(ThermoDatabase database) {
        this.database = database;
    }

    /** Mixture enthalpy sum n_i * (h_f,i + integral Cp_i dT) in J, for the given mole amounts. */
    public double mixtureEnthalpy(Map<Species, Double> moles, double temperature) {
        double total = 0.0;
        for (Map.Entry<Species, Double> entry : moles.entrySet()) {
            double amount = entry.getValue();
            if (amount != 0.0) {
                total += amount * database.of(entry.getKey()).enthalpy(temperature);
            }
        }
        return total;
    }

    /** Mixture heat capacity sum n_i * Cp_i(T) in J/K; this is dH/dT of the mixture. */
    public double mixtureCp(Map<Species, Double> moles, double temperature) {
        double total = 0.0;
        for (Map.Entry<Species, Double> entry : moles.entrySet()) {
            double amount = entry.getValue();
            if (amount != 0.0) {
                total += amount * database.of(entry.getKey()).cp(temperature);
            }
        }
        return total;
    }
}
