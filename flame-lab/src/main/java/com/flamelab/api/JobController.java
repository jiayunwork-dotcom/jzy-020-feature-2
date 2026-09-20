package com.flamelab.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flamelab.job.FlameJob;
import com.flamelab.job.JobService;

/**
 * HTTP entry points: submit one solve (one job = one balance iteration) and
 * retrieve jobs by id. No batch submission endpoint exists on purpose.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<FlameJob> create(@RequestBody SolveRequest request) {
        FlameJob job = jobService.createAndSolve(
                request.fuel(), request.equivalenceRatio(), request.intakeTemperature());
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/{id}")
    public FlameJob getById(@PathVariable long id) {
        return jobService.find(id);
    }

    @GetMapping
    public List<FlameJob> list() {
        return jobService.findAll();
    }
}
