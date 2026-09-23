package com.sparsh.sentinel.ingest.simulate;

import com.sparsh.sentinel.ingest.simulate.TrafficSimulator.SimulationReport;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Demo only. Goes behind the ADMIN role when Keycloak arrives in week 7. */
@Validated
@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private final TrafficSimulator simulator;

    public SimulationController(TrafficSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationReport simulate(@RequestParam(defaultValue = "40") @Min(0) @Max(5000) int ordinary) {
        return simulator.run(ordinary);
    }
}
