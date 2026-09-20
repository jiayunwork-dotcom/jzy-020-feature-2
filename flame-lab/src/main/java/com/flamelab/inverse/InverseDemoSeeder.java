package com.flamelab.inverse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.flamelab.job.JobStatus;

/**
 * Seeds the built-in inverse demonstration job (methane, intake 298.15 K,
 * target 2200 K — below the stoichiometric peak — both branches requested)
 * once at startup. Both sides must converge and pass their forward re-check;
 * a failed seed fails fast at boot.
 */
@Component
public class InverseDemoSeeder implements ApplicationRunner {

    public static final String DEMO_LABEL = "demo-inverse-methane";
    public static final double DEMO_INTAKE_TEMPERATURE = 298.15;
    public static final double DEMO_TARGET_TEMPERATURE = 2200.0;

    private static final Logger log = LoggerFactory.getLogger(InverseDemoSeeder.class);

    private final InverseJobService inverseJobService;
    private final InverseJobStore store;

    public InverseDemoSeeder(InverseJobService inverseJobService, InverseJobStore store) {
        this.inverseJobService = inverseJobService;
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (store.findByLabel(DEMO_LABEL).isPresent()) {
            return;
        }
        InverseJob demo = inverseJobService.createAndSolve(
                "methane", DEMO_INTAKE_TEMPERATURE, DEMO_TARGET_TEMPERATURE, "BOTH", DEMO_LABEL);
        if (demo.getStatus() != JobStatus.CONVERGED
                || demo.getLean() == null || !demo.getLean().isConverged()
                || demo.getRich() == null || !demo.getRich().isConverged()) {
            throw new IllegalStateException("built-in inverse demo job failed to converge both sides: "
                    + demo.getError());
        }
        log.info("seeded inverse demo job id={} methane intake=298.15 K target=2200 K -> lean phi={} rich phi={}"
                        + " (peak {} K at phi={})",
                demo.getId(), demo.getLean().getEquivalenceRatio(), demo.getRich().getEquivalenceRatio(),
                demo.getPeakTemperature(), demo.getPeakEquivalenceRatio());
    }
}
