package com.flamelab.inverse;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One branch solution of an inverse job: the equivalence ratio the outer search
 * converged to, the flame temperature of a fresh forward re-check at that phi,
 * the deviation from target, the resulting product mole fractions and the atom
 * residuals. The branch's own outer convergence sequence lives on the owning
 * job, not here, so the two branches cannot share a curve.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SideSolution {

    private double equivalenceRatio;
    private double flameTemperature;
    private double temperatureDeviation;
    private Map<String, Double> productMoleFractions;
    private Map<String, Double> atomResiduals;
    private double maxAtomResidual;

    public SideSolution() {
    }

    public static SideSolution from(SideRootFinder.SideResult result) {
        SideSolution solution = new SideSolution();
        solution.equivalenceRatio = result.equivalenceRatio();
        solution.flameTemperature = result.flameTemperature();
        solution.temperatureDeviation = result.temperatureDeviation();
        solution.productMoleFractions = Map.copyOf(result.productMoleFractions());
        solution.atomResiduals = Map.copyOf(result.atomResiduals());
        solution.maxAtomResidual = result.maxAtomResidual();
        return solution;
    }

    public double getEquivalenceRatio() {
        return equivalenceRatio;
    }

    public void setEquivalenceRatio(double equivalenceRatio) {
        this.equivalenceRatio = equivalenceRatio;
    }

    public double getFlameTemperature() {
        return flameTemperature;
    }

    public void setFlameTemperature(double flameTemperature) {
        this.flameTemperature = flameTemperature;
    }

    public double getTemperatureDeviation() {
        return temperatureDeviation;
    }

    public void setTemperatureDeviation(double temperatureDeviation) {
        this.temperatureDeviation = temperatureDeviation;
    }

    public Map<String, Double> getProductMoleFractions() {
        return productMoleFractions;
    }

    public void setProductMoleFractions(Map<String, Double> productMoleFractions) {
        this.productMoleFractions = productMoleFractions;
    }

    public Map<String, Double> getAtomResiduals() {
        return atomResiduals;
    }

    public void setAtomResiduals(Map<String, Double> atomResiduals) {
        this.atomResiduals = atomResiduals;
    }

    public double getMaxAtomResidual() {
        return maxAtomResidual;
    }

    public void setMaxAtomResidual(double maxAtomResidual) {
        this.maxAtomResidual = maxAtomResidual;
    }
}
