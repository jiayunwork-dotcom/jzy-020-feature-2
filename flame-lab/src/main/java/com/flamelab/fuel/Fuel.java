package com.flamelab.fuel;

import com.flamelab.thermo.Species;

/**
 * A gaseous hydrocarbon fuel C_c H_h. The stoichiometric oxidizer demand per
 * mole of fuel is c + h/4 mol O2. The fuel is also a thermodynamic species so
 * that reactant sensible enthalpy and (for rich mixtures) unburned fuel use
 * the exact same coefficients as every product.
 */
public record Fuel(String id, Species species, int carbon, int hydrogen) {

    public double stoichiometricOxygen() {
        return carbon + hydrogen / 4.0;
    }
}
