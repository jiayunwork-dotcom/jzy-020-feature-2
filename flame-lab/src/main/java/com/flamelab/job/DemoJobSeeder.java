package com.flamelab.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the built-in demonstration job (stoichiometric methane, intake
 * 298.15 K) once at startup. Its flame temperature must land in the pinned
 * stoichiometric-methane interval and its residual curve must close below
 * tolerance; a failed seed fails fast at boot.
 */
@Component
public class DemoJobSeeder implements ApplicationRunner {

    public static final String DEMO_LABEL = "demo-stoich-methane";
    public static final double DEMO_INTAKE_TEMPERATURE = 298.15;

    private static final Logger log = LoggerFactory.getLogger(DemoJobSeeder.class);

    private final JobService jobService;
    private final JobStore store;

    public DemoJobSeeder(JobService jobService, JobStore store) {
        this.jobService = jobService;
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (store.findByLabel(DEMO_LABEL).isPresent()) {
            return;
        }
        FlameJob demo = jobService.createAndSolve("methane", 1.0, DEMO_INTAKE_TEMPERATURE, DEMO_LABEL);
        if (demo.getStatus() != JobStatus.CONVERGED) {
            throw new IllegalStateException("built-in demo job failed to converge: " + demo.getError());
        }
        log.info("seeded demo job id={} methane phi=1.0 intake=298.15 K -> flame {} K in {} iterations",
                demo.getId(), demo.getFinalTemperature(), demo.getIterations().size());
    }
}
