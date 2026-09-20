package com.flamelab.inverse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.flamelab.balance.Element;
import com.flamelab.fuel.Fuel;
import com.flamelab.job.ErrorInfo;
import com.flamelab.job.ErrorType;
import com.flamelab.solver.SolverProperties;

/**
 * Re-checks a converged equivalence ratio by feeding it back through the
 * forward balance, exactly as a caller of the forward endpoint would see it:
 * the forward-solved flame temperature must land within the pinned
 * temperature tolerance of the target, the ratio must be positive and the
 * element balance must close below the pinned atom threshold. The re-checked
 * temperature, product mole fractions and atom residuals are what the side
 * result carries; a ratio that fails the re-check is dropped, never reported.
 */
@Component
public class ForwardVerifier {

    private final ForwardFlameEvaluator forward;
    private final InverseProperties inverseProperties;
    private final SolverProperties solverProperties;

    public ForwardVerifier(ForwardFlameEvaluator forward, InverseProperties inverseProperties,
                           SolverProperties solverProperties) {
        this.forward = forward;
        this.inverseProperties = inverseProperties;
        this.solverProperties = solverProperties;
    }

    /** Fills the side result with the forward re-check; flips it to failed when the re-check fails. */
    public void verify(Fuel fuel, double targetTemperature, double intakeTemperature, SideResult side) {
        double phi = side.getEquivalenceRatio();
        ForwardEvaluation check = forward.evaluate(fuel, phi, intakeTemperature);
        double maxAtomResidual = check.balance().atoms().maxAbsResidual();

        if (maxAtomResidual > solverProperties.atomBalanceThreshold()) {
            fail(side, ErrorType.ELEMENT_BALANCE_VIOLATION,
                    "atom balance residual " + maxAtomResidual + " mol exceeds pinned threshold "
                            + solverProperties.atomBalanceThreshold() + " at equivalence ratio " + phi);
            return;
        }
        if (phi <= 0.0
                || Math.abs(check.flameTemperature() - targetTemperature)
                        > inverseProperties.temperatureTolerance()) {
            fail(side, ErrorType.FORWARD_VERIFICATION_FAILED,
                    "forward re-check of equivalence ratio " + phi + " solved to "
                            + check.flameTemperature() + " K, outside tolerance "
                            + inverseProperties.temperatureTolerance() + " K of target "
                            + targetTemperature + " K");
            return;
        }

        side.setFlameTemperature(check.flameTemperature());
        side.setProductMoleFractions(check.balance().mix().productMoleFractions());
        side.setAtomResiduals(atomResidualMap(check));
        side.setMaxAtomResidual(maxAtomResidual);
    }

    private void fail(SideResult side, ErrorType type, String message) {
        side.setConverged(false);
        side.setEquivalenceRatio(null);
        side.setError(new ErrorInfo(type, message));
    }

    private Map<String, Double> atomResidualMap(ForwardEvaluation check) {
        Map<String, Double> map = new LinkedHashMap<>();
        check.balance().atoms().residuals().forEach((Element element, Double residual) ->
                map.put(element.name(), residual));
        return map;
    }
}
