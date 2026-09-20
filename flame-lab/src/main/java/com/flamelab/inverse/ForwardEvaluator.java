package com.flamelab.inverse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.flamelab.balance.BalanceResult;
import com.flamelab.balance.Element;
import com.flamelab.balance.ElementBalancer;
import com.flamelab.fuel.Fuel;
import com.flamelab.job.ErrorType;
import com.flamelab.solver.FlameSolver;
import com.flamelab.solver.SolveException;
import com.flamelab.solver.SolveResult;

/**
 * The single point where the inverse search is allowed to learn anything about
 * flame temperature: for a probed equivalence ratio it closes the exact same
 * element balance and runs the exact same temperature iteration kernel as the
 * forward service. No lookup table, no polynomial fit — every outer probe is a
 * genuine forward solve through {@link FlameSolver}.
 */
@Component
public class ForwardEvaluator {

    private final ElementBalancer balancer;
    private final FlameSolver solver;

    public ForwardEvaluator(ElementBalancer balancer, FlameSolver solver) {
        this.balancer = balancer;
        this.solver = solver;
    }

    /**
     * Runs one forward balance at the given phi. A probe whose adiabatic root
     * falls outside the pinned Cp window (typically extreme ratios at very cold
     * intake) is reported as a non-finite evaluation rather than aborting the
     * whole outer search; other failures propagate.
     */
    public Probe evaluate(Fuel fuel, double equivalenceRatio, double intakeTemperature) {
        BalanceResult balance = balancer.balance(fuel, equivalenceRatio);
        double temperature;
        try {
            SolveResult result = solver.solve(balance, intakeTemperature);
            temperature = result.finalTemperature();
        } catch (SolveException e) {
            // Only a flame root outside the pinned Cp window is treated as an
            // un-evaluable probe (extreme ratios at very cold intake); the outer
            // search walks away from it. Any other forward failure is real and
            // aborts the job instead of being papered over.
            if (e.type() != ErrorType.TEMPERATURE_OUT_OF_RANGE) {
                throw e;
            }
            return new Probe(equivalenceRatio, false, Double.NaN,
                    balance.mix().productMoleFractions(),
                    atomResidualMap(balance), balance.atoms().maxAbsResidual());
        }
        return new Probe(equivalenceRatio, true, temperature,
                balance.mix().productMoleFractions(),
                atomResidualMap(balance), balance.atoms().maxAbsResidual());
    }

    private Map<String, Double> atomResidualMap(BalanceResult balance) {
        Map<String, Double> map = new LinkedHashMap<>();
        balance.atoms().residuals().forEach((Element element, Double residual) ->
                map.put(element.name(), residual));
        return map;
    }

    /**
     * One forward evaluation: whether it produced a temperature inside the
     * pinned window, the adiabatic flame temperature, and the composition and
     * atom residuals of that balance (reused by the final forward re-check).
     */
    public record Probe(double equivalenceRatio, boolean finite, double temperature,
                        Map<String, Double> productMoleFractions,
                        Map<String, Double> atomResiduals, double maxAtomResidual) {

        public double deviation(double targetTemperature) {
            return temperature - targetTemperature;
        }
    }
}
