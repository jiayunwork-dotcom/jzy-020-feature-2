package com.flamelab.thermo;

/** Thermodynamic data for one species: formation enthalpy at the reference state plus its Cp polynomial. */
public record SpeciesThermo(Species species, double formationEnthalpy, NasaPolynomial cpPolynomial) {

    /** Absolute molar enthalpy h(T) = h_f(ref) + integral of Cp from ref to T, in J/mol. */
    public double enthalpy(double temperature) {
        return formationEnthalpy + cpPolynomial.sensibleEnthalpy(temperature);
    }

    public double cp(double temperature) {
        return cpPolynomial.cp(temperature);
    }
}
