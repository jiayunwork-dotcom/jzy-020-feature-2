package com.flamelab.thermo;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Built-in thermodynamic database. One pinned coefficient set per species,
 * shared by reactants and products alike (never one set per side).
 *
 * Cp polynomials are 7-coefficient NASA polynomials (Cp/R form, GRI-Mech 3.0
 * coefficient set) valid on the service-pinned window; formation enthalpies
 * are at 298.15 K in J/mol.
 */
@Component
public class ThermoDatabase {

    private static final double SWITCH_TEMPERATURE = 1000.0;

    private final ThermoLimits limits;
    private final Map<Species, SpeciesThermo> species;

    public ThermoDatabase(ThermoLimits limits) {
        this.limits = limits;
        this.species = new EnumMap<>(Species.class);
        register(Species.N2, 0.0,
                new double[]{3.53100528E+00, -1.23660988E-04, -5.02999437E-07, 2.43530612E-09, -1.40881235E-12},
                new double[]{2.95257637E+00, 1.39690040E-03, -4.92631603E-07, 7.86010195E-11, -4.60755204E-15});
        register(Species.O2, 0.0,
                new double[]{3.78245636E+00, -2.99673416E-03, 9.84730201E-06, -9.68129509E-09, 3.24372837E-12},
                new double[]{3.66096083E+00, 6.56365811E-04, -1.41149627E-07, 2.05797935E-11, -1.29913436E-15});
        register(Species.CO2, -393_510.0,
                new double[]{2.35677352E+00, 8.98459677E-03, -7.12356269E-06, 2.45919022E-09, -2.43699548E-13},
                new double[]{3.85746029E+00, 4.41437026E-03, -2.21481404E-06, 5.23490188E-10, -4.72084164E-14});
        register(Species.H2O, -241_826.0,
                new double[]{4.19864056E+00, -2.03643410E-03, 6.52040211E-06, -5.48797062E-09, 1.77197817E-12},
                new double[]{3.03399249E+00, 2.17691804E-03, -1.64072518E-07, -9.70419870E-11, 1.68200992E-14});
        register(Species.CH4, -74_850.0,
                new double[]{5.14987613E+00, -1.36709788E-02, 4.91800599E-05, -4.84743026E-08, 1.66693956E-11},
                new double[]{7.48514950E-02, 1.33909467E-02, -5.73285809E-06, 1.22292535E-09, -1.01815230E-13});
        register(Species.C2H6, -84_000.0,
                new double[]{4.29142492E+00, -5.50154904E-03, 5.99438288E-05, -7.08466409E-08, 2.68685771E-11},
                new double[]{1.07188150E+00, 1.39226319E-02, -5.53898839E-06, 1.12634796E-09, -9.14812087E-14});
    }

    private void register(Species key, double formationEnthalpy, double[] low, double[] high) {
        species.put(key, new SpeciesThermo(key, formationEnthalpy,
                new NasaPolynomial(low, high, SWITCH_TEMPERATURE, limits)));
    }

    public SpeciesThermo of(Species key) {
        SpeciesThermo thermo = species.get(key);
        if (thermo == null) {
            throw new IllegalArgumentException("no thermodynamic data for species " + key);
        }
        return thermo;
    }

    public ThermoLimits limits() {
        return limits;
    }
}
