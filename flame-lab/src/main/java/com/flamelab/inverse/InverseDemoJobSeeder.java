package com.flamelab.inverse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.flamelab.job.JobStatus;

/**
 * Seeds the built-in inverse demonstration job once at startup: methane,
 * intake 298.15 K, a target temperature a notch below the reachable peak, both
 * branches requested. Both the lean and the rich equivalence ratios must be
 * found and each pass its own forward re-check; a failed seed fails fast at
 * boot, mirroring the forward demo.
 */
@Component
public class InverseDemoJobSeeder implements ApplicationRunner {

    public static final String DEMO_LABEL = "demo-inverse-methane-2150";
    public static final double DEMO_INTAKE_TEMPERATURE = 298.15;
    public static final double DEMO_TARGET_TEMPERATURE = 2150.0;
    public static final String DEMO_SIDE = "BOTH";

    private static final Logger log = LoggerFactory.getLogger(InverseDemoJobSeeder.class);

    private final InverseJobService jobService;
    private final InverseJobStore store;

    public InverseDemoJobSeeder(InverseJobService jobService, InverseJobStore store) {
        this.jobService = jobService;
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (store.findByLabel(DEMO_LABEL).isPresent()) {
            return;
        }
        InverseFlameJob demo = jobService.createAndSolve("methane", DEMO_INTAKE_TEMPERATURE,
                DEMO_TARGET_TEMPERATURE, DEMO_SIDE, DEMO_LABEL);
        if (demo.getStatus() != JobStatus.CONVERGED
                || demo.getLeanSolution() == null || demo.getRichSolution() == null) {
            throw new IllegalStateException("built-in inverse demo job failed to converge: " + demo.getError());
        }
        log.info("seeded inverse demo job id={} methane target={} K -> lean phi={}, rich phi={} (peak {} K at phi={})",
                demo.getId(), DEMO_TARGET_TEMPERATURE,
                demo.getLeanSolution().getEquivalenceRatio(),
                demo.getRichSolution().getEquivalenceRatio(),
                demo.getPeakTemperature(), demo.getPeakEquivalenceRatio());
    }
}
