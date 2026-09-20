package com.flamelab.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.flamelab.balance.BalanceResult;
import com.flamelab.balance.ElementBalancer;
import com.flamelab.fuel.FuelCatalog;
import com.flamelab.job.ErrorType;
import com.flamelab.thermo.EnthalpyCalculator;
import com.flamelab.thermo.ThermoDatabase;
import com.flamelab.thermo.ThermoLimits;

/**
 * Regression tests for the constant-pressure adiabatic flame temperature
 * balance. All expected behaviours are pinned here.
 */
class FlameSolverTest {

    /** Pinned acceptance interval for stoichiometric methane at ~298 K intake: order of 2200 K. */
    private static final double METHANE_STOICH_MIN = 2100.0;
    private static final double METHANE_STOICH_MAX = 2400.0;

    private final ThermoLimits limits = ThermoLimits.pinned();
    private final ThermoDatabase database = new ThermoDatabase(limits);
    private final EnthalpyCalculator enthalpy = new EnthalpyCalculator(database);
    private final FuelCatalog fuels = new FuelCatalog();
    private final ElementBalancer balancer = new ElementBalancer();
    private final SolverProperties properties = SolverProperties.pinned();
    private final FlameSolver solver = new FlameSolver(enthalpy, limits, properties);

    private SolveResult solve(String fuelId, double phi, double intakeTemperature) {
        BalanceResult balance = balancer.balance(fuels.find(fuelId).orElseThrow(), phi);
        return solver.solve(balance, intakeTemperature);
    }

    @Test
    void stoichiometricMethaneAt298LandsInPinnedInterval() {
        SolveResult result = solve("methane", 1.0, 298.15);
        assertTrue(result.finalTemperature() > METHANE_STOICH_MIN
                        && result.finalTemperature() < METHANE_STOICH_MAX,
                "stoichiometric methane flame temperature " + result.finalTemperature()
                        + " K outside pinned interval [" + METHANE_STOICH_MIN + ", " + METHANE_STOICH_MAX + "]");
    }

    @Test
    void leanAndRichBothStayBelowStoichiometric() {
        double intake = 298.15;
        double stoich = solve("methane", 1.0, intake).finalTemperature();
        double lean = solve("methane", 0.8, intake).finalTemperature();
        double rich = solve("methane", 1.2, intake).finalTemperature();
        assertTrue(lean < stoich, "lean flame " + lean + " K not below stoichiometric " + stoich + " K");
        assertTrue(rich < stoich, "rich flame " + rich + " K not below stoichiometric " + stoich + " K");
    }

    @Test
    void higherIntakeRaisesFlameTemperatureByLessThanTheIntakeRise() {
        double base = solve("methane", 1.0, 298.15).finalTemperature();
        double heated = solve("methane", 1.0, 398.15).finalTemperature();
        double rise = heated - base;
        assertTrue(rise > 0.0, "flame temperature did not rise with intake temperature");
        assertTrue(rise < 100.0, "flame temperature rise " + rise
                + " K is not smaller than the 100 K intake rise");
    }

    @Test
    void ethaneIsNotMethane() {
        double methane = solve("methane", 1.0, 298.15).finalTemperature();
        double ethane = solve("ethane", 1.0, 298.15).finalTemperature();
        assertTrue(Math.abs(ethane - methane) > 1.0,
                "ethane and methane flame temperatures must differ, got " + ethane + " vs " + methane);
        assertTrue(ethane > METHANE_STOICH_MIN && ethane < METHANE_STOICH_MAX,
                "ethane flame temperature " + ethane + " K wildly out of the expected band");
    }

    @Test
    void atomResidualsAreNegligibleAcrossFuelsAndRatios() {
        for (String fuel : new String[]{"methane", "ethane"}) {
            for (double phi : new double[]{0.8, 1.0, 1.2}) {
                BalanceResult balance = balancer.balance(fuels.find(fuel).orElseThrow(), phi);
                assertTrue(balance.atoms().maxAbsResidual() < properties.atomBalanceThreshold(),
                        fuel + " phi=" + phi + " atom residual " + balance.atoms().maxAbsResidual()
                                + " above threshold " + properties.atomBalanceThreshold());
            }
        }
    }

    @Test
    void residualCurveDecreasesMonotonicallyAndClosesBelowTolerance() {
        SolveResult result = solve("methane", 1.0, 298.15);
        List<IterationPoint> curve = result.iterations();
        assertTrue(curve.size() >= 2, "residual curve should record the iteration history");
        for (int i = 1; i < curve.size(); i++) {
            double previous = Math.abs(curve.get(i - 1).enthalpyResidual());
            double current = Math.abs(curve.get(i).enthalpyResidual());
            assertTrue(current < previous,
                    "residual not strictly decreasing at step " + i + ": " + previous + " -> " + current);
        }
        double last = Math.abs(curve.get(curve.size() - 1).enthalpyResidual());
        assertTrue(last <= properties.enthalpyTolerance(),
                "final residual " + last + " above tolerance " + properties.enthalpyTolerance());
    }

    @Test
    void nonClosingEnthalpyBalanceFailsInsteadOfFabricating() {
        FlameSolver crippled = new FlameSolver(enthalpy, limits,
                new SolverProperties(properties.enthalpyTolerance(), 2, properties.atomBalanceThreshold()));
        BalanceResult balance = balancer.balance(fuels.find("methane").orElseThrow(), 1.0);
        SolveException failure = assertThrows(SolveException.class, () -> crippled.solve(balance, 298.15));
        assertEquals(ErrorType.ENTHALPY_NOT_CONVERGED, failure.type());
        assertTrue(Math.abs(failure.iterations().get(failure.iterations().size() - 1).enthalpyResidual())
                > properties.enthalpyTolerance(), "failed job must not pretend the balance closed");
    }

    @Test
    void intakeOutsideCpWindowFailsOutOfRange() {
        BalanceResult balance = balancer.balance(fuels.find("methane").orElseThrow(), 1.0);
        SolveException failure = assertThrows(SolveException.class, () -> solver.solve(balance, 100.0));
        assertEquals(ErrorType.TEMPERATURE_OUT_OF_RANGE, failure.type());
    }

    @Test
    void demoConditionResidualCurveEndsBelowTolerance() {
        SolveResult demo = solve("methane", 1.0, 298.15);
        double last = Math.abs(demo.iterations().get(demo.iterations().size() - 1).enthalpyResidual());
        assertTrue(last <= properties.enthalpyTolerance());
    }
}
