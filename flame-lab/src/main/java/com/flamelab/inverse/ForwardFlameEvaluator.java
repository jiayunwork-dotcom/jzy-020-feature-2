package com.flamelab.inverse;

import org.springframework.stereotype.Component;

import com.flamelab.balance.BalanceResult;
import com.flamelab.balance.ElementBalancer;
import com.flamelab.fuel.Fuel;
import com.flamelab.solver.FlameSolver;
import com.flamelab.solver.SolveResult;

/**
 * The forward balance seen as a function of equivalence ratio. One call is
 * one full forward solve — element balance plus the shared temperature
 * iteration kernel — at the given phi. This is the only way the inverse
 * search learns flame temperatures: no lookup tables, no fitted polynomials.
 */
@Component
public class ForwardFlameEvaluator {

    private final ElementBalancer balancer;
    private final FlameSolver solver;

    public ForwardFlameEvaluator(ElementBalancer balancer, FlameSolver solver) {
        this.balancer = balancer;
        this.solver = solver;
    }

    public ForwardEvaluation evaluate(Fuel fuel, double equivalenceRatio, double intakeTemperature) {
        BalanceResult balance = balancer.balance(fuel, equivalenceRatio);
        SolveResult result = solver.solve(balance, intakeTemperature);
        return new ForwardEvaluation(equivalenceRatio, result.finalTemperature(), balance);
    }
}
