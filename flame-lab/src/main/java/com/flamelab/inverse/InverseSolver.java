package com.flamelab.inverse;

import java.util.List;

import org.springframework.stereotype.Component;

import com.flamelab.fuel.Fuel;
import com.flamelab.job.ErrorType;
import com.flamelab.solver.SolverProperties;

/**
 * Inverse balance: target adiabatic flame temperature in, equivalence ratio(s)
 * out. This is a layer wrapped around the unchanged forward balance — the
 * forward kernel (same fuel catalog, same pinned air composition, same NASA
 * coefficients, same temperature iteration) is the only thing ever evaluated.
 *
 * Because flame temperature is non-monotonic in phi (rising to a peak near
 * stoichiometric, then falling on the rich side), a target below the peak
 * generally has two roots. The orchestrator first locates that peak from
 * repeated forward solves, rejects targets above it as unreachable (reporting
 * the peak), and then roots the requested branch(es) independently. Lean and
 * rich each keep their own ordered outer convergence sequence.
 */
@Component
public class InverseSolver {

    private final PeakLocator peakLocator;
    private final SideRootFinder sideRootFinder;
    private final SolverProperties solverProperties;

    public InverseSolver(PeakLocator peakLocator, SideRootFinder sideRootFinder,
                         SolverProperties solverProperties) {
        this.peakLocator = peakLocator;
        this.sideRootFinder = sideRootFinder;
        this.solverProperties = solverProperties;
    }

    public InverseOutcome solve(Fuel fuel, double intakeTemperature, double targetTemperature,
                                RequestSide requestedSide) {
        Peak peak = peakLocator.locate(fuel, intakeTemperature);

        SideRootFinder.SideResult lean = null;
        SideRootFinder.SideResult rich = null;
        List<OuterStep> leanSequence = List.of();
        List<OuterStep> richSequence = List.of();
        try {
            if (requestedSide == RequestSide.LEAN || requestedSide == RequestSide.BOTH) {
                lean = runSide(fuel, intakeTemperature, targetTemperature, peak, true);
                leanSequence = lean.sequence();
            }
            if (requestedSide == RequestSide.RICH || requestedSide == RequestSide.BOTH) {
                rich = runSide(fuel, intakeTemperature, targetTemperature, peak, false);
                richSequence = rich.sequence();
            }
        } catch (InverseException e) {
            // Preserve whatever sequences already converged on the other branch too.
            if (leanSequence.isEmpty() && !e.leanSteps().isEmpty()) {
                leanSequence = e.leanSteps();
            }
            if (richSequence.isEmpty() && !e.richSteps().isEmpty()) {
                richSequence = e.richSteps();
            }
            throw new InverseException(e.type(), e.getMessage(),
                    e.peak() != null ? e.peak() : peak, leanSequence, richSequence);
        }
        return new InverseOutcome(peak, lean, rich);
    }

    private SideRootFinder.SideResult runSide(Fuel fuel, double intakeTemperature, double targetTemperature,
                                              Peak peak, boolean leanBranch) {
        SideRootFinder.SideResult result = sideRootFinder.find(
                fuel, intakeTemperature, targetTemperature, peak, leanBranch);
        if (result.maxAtomResidual() > solverProperties.atomBalanceThreshold()) {
            throw new InverseException(ErrorType.ELEMENT_BALANCE_VIOLATION,
                    "inverse solution atom residual " + result.maxAtomResidual()
                            + " mol exceeds pinned threshold " + solverProperties.atomBalanceThreshold(),
                    peak,
                    leanBranch ? result.sequence() : List.of(),
                    leanBranch ? List.of() : result.sequence());
        }
        return result;
    }
}
