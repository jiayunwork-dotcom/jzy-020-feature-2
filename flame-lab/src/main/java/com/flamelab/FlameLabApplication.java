package com.flamelab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.flamelab.inverse.InverseProperties;
import com.flamelab.solver.SolverProperties;
import com.flamelab.thermo.ThermoLimits;

@SpringBootApplication
public class FlameLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlameLabApplication.class, args);
    }

    /** Pinned Cp validity window and enthalpy reference state. */
    @Bean
    ThermoLimits thermoLimits() {
        return ThermoLimits.pinned();
    }

    /** Pinned convergence criteria for the enthalpy iteration. */
    @Bean
    SolverProperties solverProperties() {
        return SolverProperties.pinned();
    }

    /** Pinned tolerance and step budget for the inverse outer search. */
    @Bean
    InverseProperties inverseProperties() {
        return InverseProperties.pinned();
    }
}
