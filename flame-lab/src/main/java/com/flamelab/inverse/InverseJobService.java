package com.flamelab.inverse;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.flamelab.job.ErrorInfo;
import com.flamelab.job.ErrorType;
import com.flamelab.job.JobNotFoundException;
import com.flamelab.job.JobStatus;
import com.flamelab.solver.SolveException;

/**
 * Orchestrates one inverse job per target temperature: validate input, locate
 * the peak of the flame-temperature curve, reject unreachable targets with
 * the peak attached, then run the outer root find on each requested branch
 * and re-check every found ratio through the forward balance. One job is
 * exactly one target temperature — there is deliberately no batch entry
 * point.
 */
@Service
public class InverseJobService {

    private final InverseInputValidator validator;
    private final PeakLocator peakLocator;
    private final SideRootFinder sideRootFinder;
    private final ForwardVerifier verifier;
    private final InverseProperties properties;
    private final InverseJobStore store;

    public InverseJobService(InverseInputValidator validator, PeakLocator peakLocator,
                             SideRootFinder sideRootFinder, ForwardVerifier verifier,
                             InverseProperties properties, InverseJobStore store) {
        this.validator = validator;
        this.peakLocator = peakLocator;
        this.sideRootFinder = sideRootFinder;
        this.verifier = verifier;
        this.properties = properties;
        this.store = store;
    }

    public InverseJob createAndSolve(String fuelId, Double intakeTemperature,
                                     Double targetTemperature, String side) {
        return createAndSolve(fuelId, intakeTemperature, targetTemperature, side, null);
    }

    public InverseJob createAndSolve(String fuelId, Double intakeTemperature, Double targetTemperature,
                                     String side, String label) {
        ValidatedInverseInput input = validator.validate(fuelId, intakeTemperature, targetTemperature, side);

        InverseJob job = new InverseJob();
        job.setLabel(label);
        job.setFuel(input.fuel().id());
        job.setIntakeTemperature(input.intakeTemperature());
        job.setTargetTemperature(input.targetTemperature());
        job.setRequestedSide(input.side());
        job.setTemperatureTolerance(properties.temperatureTolerance());
        job.setMaxOuterSteps(properties.maxOuterSteps());
        job.setCreatedAt(Instant.now().toString());

        try {
            PeakResult peak = peakLocator.locate(input.fuel(), input.intakeTemperature());
            job.setPeakEquivalenceRatio(peak.equivalenceRatio());
            job.setPeakTemperature(peak.temperature());

            if (input.targetTemperature() > peak.temperature()) {
                markUnreachable(job, input, peak);
                return store.insert(job);
            }

            boolean allConverged = true;
            ErrorInfo firstFailure = null;
            if (input.side().includes(Side.LEAN)) {
                SideResult lean = solveSide(input, Side.LEAN, peak);
                job.setLean(lean);
                allConverged &= lean.isConverged();
                if (!lean.isConverged() && firstFailure == null) {
                    firstFailure = lean.getError();
                }
            }
            if (input.side().includes(Side.RICH)) {
                SideResult rich = solveSide(input, Side.RICH, peak);
                job.setRich(rich);
                allConverged &= rich.isConverged();
                if (!rich.isConverged() && firstFailure == null) {
                    firstFailure = rich.getError();
                }
            }

            if (allConverged) {
                job.setStatus(JobStatus.CONVERGED);
            } else {
                job.setStatus(JobStatus.FAILED);
                job.setError(firstFailure);
            }
        } catch (SolveException e) {
            job.setStatus(JobStatus.FAILED);
            job.setError(new ErrorInfo(e.type(), e.getMessage()));
        }
        return store.insert(job);
    }

    public InverseJob find(long id) {
        return store.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    public List<InverseJob> findAll() {
        return store.findAll();
    }

    /**
     * A target above the located peak is unreachable on both branches: the job
     * fails with the estimated peak ratio and temperature attached, so the
     * caller learns how high this fuel/intake pair can actually go.
     */
    private void markUnreachable(InverseJob job, ValidatedInverseInput input, PeakResult peak) {
        ErrorInfo unreachable = new ErrorInfo(ErrorType.TARGET_UNREACHABLE,
                "target flame temperature " + input.targetTemperature() + " K exceeds the reachable peak "
                        + peak.temperature() + " K at equivalence ratio " + peak.equivalenceRatio()
                        + " for fuel '" + input.fuel().id() + "' at intake " + input.intakeTemperature() + " K");
        job.setStatus(JobStatus.FAILED);
        job.setError(unreachable);
        if (input.side().includes(Side.LEAN)) {
            job.setLean(SideResult.unreachable(Side.LEAN, unreachable));
        }
        if (input.side().includes(Side.RICH)) {
            job.setRich(SideResult.unreachable(Side.RICH, unreachable));
        }
    }

    private SideResult solveSide(ValidatedInverseInput input, Side side, PeakResult peak) {
        SideResult result = sideRootFinder.findRoot(
                input.fuel(), input.intakeTemperature(), input.targetTemperature(), side, peak);
        if (result.isConverged()) {
            verifier.verify(input.fuel(), input.targetTemperature(), input.intakeTemperature(), result);
        }
        return result;
    }
}
