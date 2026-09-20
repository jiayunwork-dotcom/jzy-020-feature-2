package com.flamelab.job;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.flamelab.balance.BalanceResult;
import com.flamelab.balance.Element;
import com.flamelab.balance.ElementBalancer;
import com.flamelab.solver.FlameSolver;
import com.flamelab.solver.SolveException;
import com.flamelab.solver.SolveResult;
import com.flamelab.solver.SolverProperties;

/**
 * Orchestrates one job per solve: validate input, close the element balance,
 * iterate the flame temperature, then persist everything as a single job
 * record. There is deliberately no batch entry point — one job is exactly one
 * balance iteration.
 */
@Service
public class JobService {

    private final InputValidator validator;
    private final ElementBalancer balancer;
    private final FlameSolver solver;
    private final SolverProperties properties;
    private final JobStore store;

    public JobService(InputValidator validator, ElementBalancer balancer, FlameSolver solver,
                      SolverProperties properties, JobStore store) {
        this.validator = validator;
        this.balancer = balancer;
        this.solver = solver;
        this.properties = properties;
        this.store = store;
    }

    public FlameJob createAndSolve(String fuelId, Double equivalenceRatio, Double intakeTemperature) {
        return createAndSolve(fuelId, equivalenceRatio, intakeTemperature, null);
    }

    public FlameJob createAndSolve(String fuelId, Double equivalenceRatio, Double intakeTemperature, String label) {
        ValidatedInput input = validator.validate(fuelId, equivalenceRatio, intakeTemperature);

        FlameJob job = new FlameJob();
        job.setLabel(label);
        job.setFuel(input.fuel().id());
        job.setEquivalenceRatio(input.equivalenceRatio());
        job.setIntakeTemperature(input.intakeTemperature());
        job.setEnthalpyTolerance(properties.enthalpyTolerance());
        job.setAtomBalanceThreshold(properties.atomBalanceThreshold());
        job.setMaxIterations(properties.maxIterations());
        job.setCreatedAt(Instant.now().toString());

        BalanceResult balance = balancer.balance(input.fuel(), input.equivalenceRatio());
        job.setAtomResiduals(atomResidualMap(balance));
        job.setMaxAtomResidual(balance.atoms().maxAbsResidual());

        try {
            if (balance.atoms().maxAbsResidual() > properties.atomBalanceThreshold()) {
                throw new SolveException(ErrorType.ELEMENT_BALANCE_VIOLATION,
                        "atom balance residual " + balance.atoms().maxAbsResidual()
                                + " mol exceeds pinned threshold " + properties.atomBalanceThreshold(),
                        List.of());
            }
            SolveResult result = solver.solve(balance, input.intakeTemperature());
            job.setStatus(JobStatus.CONVERGED);
            job.setFinalTemperature(result.finalTemperature());
            job.setIterations(result.iterations());
            job.setProductMoleFractions(balance.mix().productMoleFractions());
        } catch (SolveException e) {
            job.setStatus(JobStatus.FAILED);
            job.setIterations(e.iterations());
            job.setError(new ErrorInfo(e.type(), e.getMessage()));
        }
        return store.insert(job);
    }

    public FlameJob find(long id) {
        return store.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    public List<FlameJob> findAll() {
        return store.findAll();
    }

    private Map<String, Double> atomResidualMap(BalanceResult balance) {
        Map<String, Double> map = new LinkedHashMap<>();
        balance.atoms().residuals().forEach((Element element, Double residual) ->
                map.put(element.name(), residual));
        return map;
    }
}
