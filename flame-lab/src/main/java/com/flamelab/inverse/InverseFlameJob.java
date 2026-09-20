package com.flamelab.inverse;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flamelab.job.ErrorInfo;
import com.flamelab.job.JobStatus;

/**
 * One inverse job: inputs (fuel, intake temperature, wanted flame temperature,
 * requested branch), the located reachable peak, the forward-re-checked
 * solution(s) of the requested branch(es), and the two independently
 * accumulated outer convergence sequences plus the pinned temperature
 * tolerance.
 *
 * A failed job (target above the peak, or a branch that could not press its
 * deviation into tolerance) is still persisted: it carries the peak, the
 * sequences walked so far and a typed error, but never a fabricated solution.
 * One inverse job is exactly one target-temperature search; there is no batch.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class InverseFlameJob {

    private Long id;
    private String label;
    private String fuel;
    private double intakeTemperature;
    private double targetTemperature;
    private RequestSide side;
    private JobStatus status;
    private Double peakEquivalenceRatio;
    private Double peakTemperature;
    private SideSolution leanSolution;
    private SideSolution richSolution;
    private List<OuterStep> leanSequence;
    private List<OuterStep> richSequence;
    private double temperatureTolerance;
    private int maxOuterSteps;
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

    public RequestSide getSide() {
        return side;
    }

    public void setSide(RequestSide side) {
        this.side = side;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
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

    public SideSolution getLeanSolution() {
        return leanSolution;
    }

    public void setLeanSolution(SideSolution leanSolution) {
        this.leanSolution = leanSolution;
    }

    public SideSolution getRichSolution() {
        return richSolution;
    }

    public void setRichSolution(SideSolution richSolution) {
        this.richSolution = richSolution;
    }

    public List<OuterStep> getLeanSequence() {
        return leanSequence;
    }

    public void setLeanSequence(List<OuterStep> leanSequence) {
        this.leanSequence = leanSequence;
    }

    public List<OuterStep> getRichSequence() {
        return richSequence;
    }

    public void setRichSequence(List<OuterStep> richSequence) {
        this.richSequence = richSequence;
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
