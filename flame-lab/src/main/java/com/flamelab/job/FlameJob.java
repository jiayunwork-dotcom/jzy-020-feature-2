package com.flamelab.job;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flamelab.solver.IterationPoint;

/**
 * One flame-balance job: inputs, pinned convergence criteria, the recorded
 * iteration sequence, and — on success — final temperature and product mole
 * fractions. Failed jobs keep their partial residual curve and a typed error
 * but never carry a fabricated final temperature or composition.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class FlameJob {

    private Long id;
    private String label;
    private String fuel;
    private double equivalenceRatio;
    private double intakeTemperature;
    private JobStatus status;
    private Double finalTemperature;
    private Map<String, Double> productMoleFractions;
    private Map<String, Double> atomResiduals;
    private double maxAtomResidual;
    private double atomBalanceThreshold;
    private double enthalpyTolerance;
    private int maxIterations;
    private List<IterationPoint> iterations;
    private ErrorInfo error;
    private String createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getFuel() {
        return fuel;
    }

    public void setFuel(String fuel) {
        this.fuel = fuel;
    }

    public double getEquivalenceRatio() {
        return equivalenceRatio;
    }

    public void setEquivalenceRatio(double equivalenceRatio) {
        this.equivalenceRatio = equivalenceRatio;
    }

    public double getIntakeTemperature() {
        return intakeTemperature;
    }

    public void setIntakeTemperature(double intakeTemperature) {
        this.intakeTemperature = intakeTemperature;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Double getFinalTemperature() {
        return finalTemperature;
    }

    public void setFinalTemperature(Double finalTemperature) {
        this.finalTemperature = finalTemperature;
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

    public double getAtomBalanceThreshold() {
        return atomBalanceThreshold;
    }

    public void setAtomBalanceThreshold(double atomBalanceThreshold) {
        this.atomBalanceThreshold = atomBalanceThreshold;
    }

    public double getEnthalpyTolerance() {
        return enthalpyTolerance;
    }

    public void setEnthalpyTolerance(double enthalpyTolerance) {
        this.enthalpyTolerance = enthalpyTolerance;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public List<IterationPoint> getIterations() {
        return iterations;
    }

    public void setIterations(List<IterationPoint> iterations) {
        this.iterations = iterations;
    }

    public ErrorInfo getError() {
        return error;
    }

    public void setError(ErrorInfo error) {
        this.error = error;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
