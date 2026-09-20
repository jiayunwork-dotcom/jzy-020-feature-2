package com.flamelab.inverse;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.flamelab.job.ErrorInfo;
import com.flamelab.job.JobNotFoundException;
import com.flamelab.job.JobStatus;
import com.flamelab.solver.SolveException;

/**
 * Orchestrates one inverse job: validate, locate the peak, root the requested
 * branch(es) by repeatedly invoking the forward balance, persist every trace.
 * Validation runs before any outer probe; solve-time failures (target above the
 * peak, a branch that does not close) are persisted as FAILED with peak and
 * sequences intact rather than fabricated away.
 */
@Service
public class InverseJobService {

    private final InverseInputValidator validator;
    private final InverseSolver solver;
    private final InverseProperties properties;
    private final InverseJobStore store;

    public InverseJobService(InverseInputValidator validator, InverseSolver solver,
                             InverseProperties properties, InverseJobStore store) {
        this.validator = validator;
        this.solver = solver;
        this.properties = properties;
        this.store = store;
    }

    public InverseFlameJob createAndSolve(String fuelId, Double intakeTemperature,
                                          Double targetTemperature, String sideText) {
        return createAndSolve(fuelId, intakeTemperature, targetTemperature, sideText, null);
    }

    public InverseFlameJob createAndSolve(String fuelId, Double intakeTemperature,
                                          Double targetTemperature, String sideText, String label) {
        ValidatedInverseInput input =
                validator.validate(fuelId, intakeTemperature, targetTemperature, sideText);

        InverseFlameJob job = new InverseFlameJob();
        job.setLabel(label);
        job.setFuel(input.fuel().id());
        job.setIntakeTemperature(input.intakeTemperature());
        job.setTargetTemperature(input.targetTemperature());
        job.setSide(input.side());
        job.setTemperatureTolerance(properties.temperatureTolerance());
        job.setMaxOuterSteps(properties.maxOuterSteps());
        job.setCreatedAt(Instant.now().toString());
        job.setLeanSequence(List.of());
        job.setRichSequence(List.of());

        try {
            InverseOutcome outcome = solver.solve(input.fuel(), input.intakeTemperature(),
                    input.targetTemperature(), input.side());
            job.setStatus(JobStatus.CONVERGED);
            job.setPeakEquivalenceRatio(outcome.peak().equivalenceRatio());
            job.setPeakTemperature(outcome.peak().temperature());
            if (outcome.lean() != null) {
                job.setLeanSolution(SideSolution.from(outcome.lean()));
                job.setLeanSequence(outcome.lean().sequence());
            }
            if (outcome.rich() != null) {
                job.setRichSolution(SideSolution.from(outcome.rich()));
                job.setRichSequence(outcome.rich().sequence());
            }
        } catch (InverseException e) {
            job.setStatus(JobStatus.FAILED);
            if (e.peak() != null) {
                job.setPeakEquivalenceRatio(e.peak().equivalenceRatio());
                job.setPeakTemperature(e.peak().temperature());
            }
            job.setLeanSequence(e.leanSteps());
            job.setRichSequence(e.richSteps());
            job.setError(new ErrorInfo(e.type(), e.getMessage()));
        } catch (SolveException e) {
            // A genuine forward-kernel failure surfacing out of an outer probe.
            job.setStatus(JobStatus.FAILED);
            job.setError(new ErrorInfo(e.type(), e.getMessage()));
        }
        return store.insert(job);
    }

    public InverseFlameJob find(long id) {
        return store.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    public List<InverseFlameJob> findAll() {
        return store.findAll();
    }
}
