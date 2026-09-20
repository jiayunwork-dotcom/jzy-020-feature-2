package com.flamelab.inverse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flamelab.job.ErrorInfo;
import com.flamelab.job.JobStatus;

/**
 * One inverse-solve job: given a fuel, an intake temperature and a target
 * adiabatic flame temperature, find the equivalence ratio(s) whose forward
 * balance lands on the target. The record keeps the located peak, each
 * requested side's result (ratio, forward re-check, own convergence
 * sequence), the pinned search controls and — on failure — a typed error.
 * One job is exactly one target temperature; there is no batch entry point.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class InverseJob {

    private Long id;
    private String label;
    private String fuel;
    private double intakeTemperature;
    private double targetTemperature;
    private RequestedSide requestedSide;
    private JobStatus status;
    private double temperatureTolerance;
    private int maxOuterSteps;
    private Double peakEquivalenceRatio;
    private Double peakTemperature;
    private SideResult lean;
    private SideResult rich;
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

    public double getIntakeTemperature() {
        return intakeTemperature;
    }

    public void setIntakeTemperature(double intakeTemperature) {
        this.intakeTemperature = intakeTemperature;
    }

    public double getTargetTemperature() {
        return targetTemperature;
    }

    public void setTargetTemperature(double targetTemperature) {
        this.targetTemperature = targetTemperature;
    }

    public RequestedSide getRequestedSide() {
        return requestedSide;
    }

    public void setRequestedSide(RequestedSide requestedSide) {
        this.requestedSide = requestedSide;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public double getTemperatureTolerance() {
        return temperatureTolerance;
    }

    public void setTemperatureTolerance(double temperatureTolerance) {
        this.temperatureTolerance = temperatureTolerance;
    }

    public int getMaxOuterSteps() {
        return maxOuterSteps;
    }

    public void setMaxOuterSteps(int maxOuterSteps) {
        this.maxOuterSteps = maxOuterSteps;
    }

    public Double getPeakEquivalenceRatio() {
        return peakEquivalenceRatio;
    }

    public void setPeakEquivalenceRatio(Double peakEquivalenceRatio) {
        this.peakEquivalenceRatio = peakEquivalenceRatio;
    }

    public Double getPeakTemperature() {
        return peakTemperature;
    }

    public void setPeakTemperature(Double peakTemperature) {
        this.peakTemperature = peakTemperature;
    }

    public SideResult getLean() {
        return lean;
    }

    public void setLean(SideResult lean) {
        this.lean = lean;
    }

    public SideResult getRich() {
        return rich;
    }

    public void setRich(SideResult rich) {
        this.rich = rich;
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
