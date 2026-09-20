package com.flamelab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.flamelab.balance.BalanceResult;
import com.flamelab.job.ErrorType;
import com.flamelab.thermo.EnthalpyCalculator;
import com.flamelab.thermo.Species;
import com.flamelab.thermo.TemperatureOutOfRangeException;
import com.flamelab.thermo.ThermoLimits;

/**
 * Adiabatic flame temperature iteration for the constant-pressure balance
 * H_products(T) = H_reactants(T_intake).
 *
 * The residual R(T) = H_products(T) - H_reactants is strictly increasing in T
 * (its derivative is the product heat capacity, always positive), so exactly
 * one root exists. The initial guess overshoots the root on purpose; damped
 * Newton steps then walk downhill, and a step is only accepted (and recorded)
 * if it strictly reduces |R|. The recorded residual curve is therefore
 * monotonically decreasing, and the loop stops the moment the pinned
 * tolerance is met instead of wandering off to another temperature.
 */
@Component
public class FlameSolver {

    /** Safety margin on the first-guess overshoot; Cp grows with T, so this lands above the root. */
    private static final double INITIAL_GUESS_MARGIN = 1.25;
    private static final int MAX_DAMPING_HALVINGS = 25;

    private final EnthalpyCalculator enthalpy;
    private final ThermoLimits limits;
    private final SolverProperties properties;

    public FlameSolver(EnthalpyCalculator enthalpy, ThermoLimits limits, SolverProperties properties) {
        this.enthalpy = enthalpy;
        this.limits = limits;
        this.properties = properties;
    }

    public SolveResult solve(BalanceResult balance, double intakeTemperature) {
        Map<Species, Double> reactants = balance.mix().reactantMoles();
        Map<Species, Double> products = balance.mix().productMoles();

        double reactantEnthalpy = reactantEnthalpy(reactants, intakeTemperature);
        preflightRootBracket(products, reactantEnthalpy);

        List<IterationPoint> iterations = new ArrayList<>();
        double temperature = initialGuess(products, reactantEnthalpy, intakeTemperature);
        double residual = residual(products, reactantEnthalpy, temperature, iterations);
        iterations.add(new IterationPoint(0, temperature, residual));

        int step = 0;
        while (true) {
            if (Math.abs(residual) <= properties.enthalpyTolerance()) {
                return new SolveResult(iterations, temperature);
            }
            if (step >= properties.maxIterations()) {
                throw new SolveException(ErrorType.ENTHALPY_NOT_CONVERGED,
                        "enthalpy residual " + residual + " J/mol still above tolerance "
                                + properties.enthalpyTolerance() + " after " + step + " iterations",
                        iterations);
            }

            double slope = mixtureCp(products, temperature, iterations);
            double candidate = temperature - residual / slope;
            double candidateResidual = residualOrNaN(products, reactantEnthalpy, candidate);

            // Damped Newton: only accept a point that strictly reduces |R|,
            // keeping the recorded residual curve monotone.
            int halvings = 0;
            while ((!Double.isFinite(candidateResidual) || Math.abs(candidateResidual) >= Math.abs(residual))
                    && halvings < MAX_DAMPING_HALVINGS) {
                candidate = 0.5 * (temperature + candidate);
                candidateResidual = residualOrNaN(products, reactantEnthalpy, candidate);
                halvings++;
            }
            if (!Double.isFinite(candidateResidual) || Math.abs(candidateResidual) >= Math.abs(residual)) {
                throw new SolveException(ErrorType.ENTHALPY_NOT_CONVERGED,
                        "iteration stalled at " + temperature + " K with residual " + residual + " J/mol",
                        iterations);
            }

            step++;
            temperature = candidate;
            residual = candidateResidual;
            iterations.add(new IterationPoint(step, temperature, residual));
        }
    }

    private double reactantEnthalpy(Map<Species, Double> reactants, double intakeTemperature) {
        try {
            return enthalpy.mixtureEnthalpy(reactants, intakeTemperature);
        } catch (TemperatureOutOfRangeException e) {
            throw new SolveException(ErrorType.TEMPERATURE_OUT_OF_RANGE,
                    "intake " + e.getMessage(), List.of());
        }
    }

    /**
     * Verifies the root actually lies inside the pinned Cp window before
     * iterating; otherwise the job fails instead of extrapolating polynomials.
     */
    private void preflightRootBracket(Map<Species, Double> products, double reactantEnthalpy) {
        double atMin = residualOrNaN(products, reactantEnthalpy, limits.minTemperature());
        double atMax = residualOrNaN(products, reactantEnthalpy, limits.maxTemperature());
        if (!Double.isFinite(atMin) || !Double.isFinite(atMax) || atMin > 0.0 || atMax < 0.0) {
            throw new SolveException(ErrorType.TEMPERATURE_OUT_OF_RANGE,
                    "adiabatic flame temperature lies outside the pinned Cp window ["
                            + limits.minTemperature() + ", " + limits.maxTemperature() + "] K",
                    List.of());
        }
    }

    private double initialGuess(Map<Species, Double> products, double reactantEnthalpy, double intakeTemperature) {
        double productEnthalpyAtIntake = residualOrNaN(products, 0.0, intakeTemperature);
        double heatRelease = reactantEnthalpy - productEnthalpyAtIntake;
        double cpAtIntake = mixtureCp(products, intakeTemperature, List.of());
        double guess = intakeTemperature;
        if (heatRelease > 0.0 && cpAtIntake > 0.0) {
            guess = intakeTemperature + INITIAL_GUESS_MARGIN * heatRelease / cpAtIntake;
        }
        return limits.clamp(guess);
    }

    private double residual(Map<Species, Double> products, double reactantEnthalpy,
                            double temperature, List<IterationPoint> iterations) {
        double value = residualOrNaN(products, reactantEnthalpy, temperature);
        if (!Double.isFinite(value)) {
            throw new SolveException(ErrorType.TEMPERATURE_OUT_OF_RANGE,
                    "iteration reached temperature " + temperature + " K outside the pinned Cp window ["
                            + limits.minTemperature() + ", " + limits.maxTemperature() + "] K",
                    iterations);
        }
        return value;
    }

    private double mixtureCp(Map<Species, Double> products, double temperature, List<IterationPoint> iterations) {
        try {
            return enthalpy.mixtureCp(products, temperature);
        } catch (TemperatureOutOfRangeException e) {
            throw new SolveException(ErrorType.TEMPERATURE_OUT_OF_RANGE, e.getMessage(), iterations);
        }
    }

    /** Residual evaluation that reports out-of-range temperatures as NaN instead of throwing. */
    private double residualOrNaN(Map<Species, Double> products, double reactantEnthalpy, double temperature) {
        try {
            return enthalpy.mixtureEnthalpy(products, temperature) - reactantEnthalpy;
        } catch (TemperatureOutOfRangeException e) {
            return Double.NaN;
        }
    }
}
