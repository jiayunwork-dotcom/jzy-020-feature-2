package com.flamelab.inverse.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flamelab.inverse.InverseFlameJob;
import com.flamelab.inverse.InverseJobService;

/**
 * HTTP entry points for the inverse balance: submit one target-temperature
 * search (one job = one target) and retrieve it by id with both branch
 * sequences. No batch endpoint exists on purpose.
 */
@RestController
@RequestMapping("/api/inverse-jobs")
public class InverseJobController {

    private final InverseJobService jobService;

    public InverseJobController(InverseJobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<InverseFlameJob> create(@RequestBody InverseSolveRequest request) {
        InverseFlameJob job = jobService.createAndSolve(
                request.fuel(), request.intakeTemperature(),
                request.targetTemperature(), request.side());
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/{id}")
    public InverseFlameJob getById(@PathVariable long id) {
        return jobService.find(id);
    }

    @GetMapping
    public List<InverseFlameJob> list() {
        return jobService.findAll();
    }
}
