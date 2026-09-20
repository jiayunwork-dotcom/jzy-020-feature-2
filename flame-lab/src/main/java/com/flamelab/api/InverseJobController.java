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

import com.flamelab.inverse.InverseJob;
import com.flamelab.inverse.InverseJobService;

/**
 * HTTP entry points for the inverse solve: submit one target temperature
 * (one job = one ratio search for one target) and retrieve inverse jobs by
 * id. No batch submission endpoint exists on purpose.
 */
@RestController
@RequestMapping("/api/inverse-jobs")
public class InverseJobController {

    private final InverseJobService inverseJobService;

    public InverseJobController(InverseJobService inverseJobService) {
        this.inverseJobService = inverseJobService;
    }

    @PostMapping
    public ResponseEntity<InverseJob> create(@RequestBody InverseRequest request) {
        InverseJob job = inverseJobService.createAndSolve(
                request.fuel(), request.intakeTemperature(), request.targetTemperature(), request.side());
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/{id}")
    public InverseJob getById(@PathVariable long id) {
        return inverseJobService.find(id);
    }

    @GetMapping
    public List<InverseJob> list() {
        return inverseJobService.findAll();
    }
}
