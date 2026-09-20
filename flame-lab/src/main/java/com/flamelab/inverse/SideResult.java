package com.flamelab.inverse;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flamelab.job.ErrorInfo;

/**
 * Outcome of the outer root find on one branch of the curve. A converged side
 * carries the found equivalence ratio plus its forward re-check (flame
 * temperature, product mole fractions, atom residuals); a failed side keeps
 * its full trial trace and a typed error but never reports a ratio that did
 * not actually meet the target.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SideResult {

    private Side side;
    private boolean converged;
    private Double equivalenceRatio;
    private Double flameTemperature;
    private Map<String, Double> productMoleFractions;
    private Map<String, Double> atomResiduals;
    private Double maxAtomResidual;
    private List<PhiTracePoint> convergence;
    private ErrorInfo error;

    public static SideResult converged(Side side, double equivalenceRatio, List<PhiTracePoint> trace) {
        SideResult result = new SideResult();
        result.setSide(side);
        result.setConverged(true);
        result.setEquivalenceRatio(equivalenceRatio);
        result.setConvergence(trace);
        return result;
    }

    public static SideResult failed(Side side, List<PhiTracePoint> trace, ErrorInfo error) {
        SideResult result = new SideResult();
        result.setSide(side);
        result.setConverged(false);
        result.setConvergence(trace);
        result.setError(error);
        return result;
    }

    /** A side that was never searched because the target sits above the located peak. */
    public static SideResult unreachable(Side side, ErrorInfo error) {
        return failed(side, List.of(), error);
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public boolean isConverged() {
        return converged;
    }

    public void setConverged(boolean converged) {
        this.converged = converged;
    }

    public Double getEquivalenceRatio() {
        return equivalenceRatio;
    }

    public void setEquivalenceRatio(Double equivalenceRatio) {
        this.equivalenceRatio = equivalenceRatio;
    }

    public Double getFlameTemperature() {
        return flameTemperature;
    }

    public void setFlameTemperature(Double flameTemperature) {
        this.flameTemperature = flameTemperature;
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

    public Double getMaxAtomResidual() {
        return maxAtomResidual;
    }

    public void setMaxAtomResidual(Double maxAtomResidual) {
        this.maxAtomResidual = maxAtomResidual;
    }

    public List<PhiTracePoint> getConvergence() {
        return convergence;
    }

    public void setConvergence(List<PhiTracePoint> convergence) {
        this.convergence = convergence;
    }

    public ErrorInfo getError() {
        return error;
    }

    public void setError(ErrorInfo error) {
        this.error = error;
    }
}
