package com.flamelab.inverse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.flamelab.balance.ElementBalancer;
import com.flamelab.fuel.Fuel;
import com.flamelab.fuel.FuelCatalog;
import com.flamelab.job.ErrorType;
import com.flamelab.solver.FlameSolver;
import com.flamelab.solver.SolverProperties;
import com.flamelab.thermo.EnthalpyCalculator;
import com.flamelab.thermo.ThermoDatabase;
import com.flamelab.thermo.ThermoLimits;

/**
 * Regression tests for the inverse solve: peak location, the two-sided outer
 * root find and the forward re-check of every found equivalence ratio.
 */
class InverseSolverTest {

    private static final double INTAKE = 298.15;
    private static final double TARGET = 2200.0;

    private final ThermoLimits limits = ThermoLimits.pinned();
    private final ThermoDatabase database = new ThermoDatabase(limits);
    private final EnthalpyCalculator enthalpy = new EnthalpyCalculator(database);
    private final FuelCatalog fuels = new FuelCatalog();
    private final ElementBalancer balancer = new ElementBalancer();
    private final SolverProperties solverProperties = SolverProperties.pinned();
    private final FlameSolver solver = new FlameSolver(enthalpy, limits, solverProperties);
    private final InverseProperties inverseProperties = InverseProperties.pinned();
    private final ForwardFlameEvaluator forward = new ForwardFlameEvaluator(balancer, solver);
    private final PeakLocator peakLocator = new PeakLocator(forward);
    private final SideRootFinder sideRootFinder = new SideRootFinder(forward, inverseProperties);
    private final ForwardVerifier verifier =
            new ForwardVerifier(forward, inverseProperties, solverProperties);

    private Fuel fuel(String id) {
        return fuels.find(id).orElseThrow();
    }

    private SideResult solveSide(String fuelId, double intake, double target, Side side) {
        PeakResult peak = peakLocator.locate(fuel(fuelId), intake);
        SideResult result = sideRootFinder.findRoot(fuel(fuelId), intake, target, side, peak);
        if (result.isConverged()) {
            verifier.verify(fuel(fuelId), target, intake, result);
        }
        return result;
    }

    @Test
    void peakSitsNearStoichiometricInsidePinnedBand() {
        PeakResult peak = peakLocator.locate(fuel("methane"), INTAKE);
        assertTrue(peak.equivalenceRatio() > 0.9 && peak.equivalenceRatio() < 1.2,
                "methane peak equivalence ratio " + peak.equivalenceRatio() + " not near stoichiometric");
        assertTrue(peak.temperature() > 2100.0 && peak.temperature() < 2400.0,
                "methane peak temperature " + peak.temperature() + " K outside pinned band");
    }

    @Test
    void belowPeakTargetYieldsTwoDistinctForwardVerifiedRoots() {
        SideResult lean = solveSide("methane", INTAKE, TARGET, Side.LEAN);
        SideResult rich = solveSide("methane", INTAKE, TARGET, Side.RICH);

        assertTrue(lean.isConverged(), "lean side must converge");
        assertTrue(rich.isConverged(), "rich side must converge");
        assertTrue(lean.getEquivalenceRatio() > 0.0 && lean.getEquivalenceRatio() < 1.0,
                "lean root " + lean.getEquivalenceRatio() + " must be below 1");
        assertTrue(rich.getEquivalenceRatio() > 1.0,
                "rich root " + rich.getEquivalenceRatio() + " must be above 1");
        assertTrue(Math.abs(lean.getEquivalenceRatio() - rich.getEquivalenceRatio()) > 0.1,
                "the two roots must be clearly different");

        for (SideResult side : List.of(lean, rich)) {
            assertTrue(Math.abs(side.getFlameTemperature() - TARGET)
                            <= inverseProperties.temperatureTolerance(),
                    "forward re-check temperature " + side.getFlameTemperature()
                            + " K outside tolerance of target " + TARGET);
            assertTrue(side.getMaxAtomResidual() <= solverProperties.atomBalanceThreshold(),
                    "atom residual above threshold");
            assertFalse(side.getProductMoleFractions().isEmpty(), "product mole fractions must be stored");
            assertTraceClosesBelowTolerance(side);
        }
    }

    @Test
    void rootsCloseInOnEachOtherAsTargetApproachesPeak() {
        PeakResult peak = peakLocator.locate(fuel("methane"), INTAKE);
        double nearTarget = peak.temperature() - 15.0;

        SideResult leanFar = solveSide("methane", INTAKE, TARGET, Side.LEAN);
        SideResult richFar = solveSide("methane", INTAKE, TARGET, Side.RICH);
        SideResult leanNear = solveSide("methane", INTAKE, nearTarget, Side.LEAN);
        SideResult richNear = solveSide("methane", INTAKE, nearTarget, Side.RICH);

        assertTrue(leanNear.getEquivalenceRatio() > leanFar.getEquivalenceRatio(),
                "lean root must move toward stoichiometric as the target approaches the peak");
        assertTrue(richNear.getEquivalenceRatio() < richFar.getEquivalenceRatio(),
                "rich root must move toward stoichiometric as the target approaches the peak");
        double gapFar = richFar.getEquivalenceRatio() - leanFar.getEquivalenceRatio();
        double gapNear = richNear.getEquivalenceRatio() - leanNear.getEquivalenceRatio();
        assertTrue(gapNear < gapFar, "root gap must shrink near the peak: " + gapNear + " vs " + gapFar);
        assertTrue(Math.abs(leanNear.getEquivalenceRatio() - peak.equivalenceRatio()) < 0.2
                        && Math.abs(richNear.getEquivalenceRatio() - peak.equivalenceRatio()) < 0.2,
                "near-peak roots must gather around the stoichiometric peak");
    }

    @Test
    void targetBelowIntakeTemperatureIsUnreachable() {
        PeakResult peak = peakLocator.locate(fuel("methane"), INTAKE);
        SideResult lean = sideRootFinder.findRoot(fuel("methane"), INTAKE, 250.0, Side.LEAN, peak);
        assertFalse(lean.isConverged());
        assertNull(lean.getEquivalenceRatio(), "an unreachable side must not report a ratio");
        assertEquals(ErrorType.TARGET_UNREACHABLE, lean.getError().type());
        assertFalse(lean.getConvergence().isEmpty(), "the failed side keeps its trial trace");
    }

    @Test
    void ethaneRootsDoNotCollapseOntoMethaneRoots() {
        SideResult methaneLean = solveSide("methane", INTAKE, TARGET, Side.LEAN);
        SideResult methaneRich = solveSide("methane", INTAKE, TARGET, Side.RICH);
        SideResult ethaneLean = solveSide("ethane", INTAKE, TARGET, Side.LEAN);
        SideResult ethaneRich = solveSide("ethane", INTAKE, TARGET, Side.RICH);

        assertNotEquals(methaneLean.getEquivalenceRatio(), ethaneLean.getEquivalenceRatio(), 0.01,
                "lean roots of methane and ethane must differ");
        assertNotEquals(methaneRich.getEquivalenceRatio(), ethaneRich.getEquivalenceRatio(), 0.01,
                "rich roots of methane and ethane must differ");
    }

    @Test
    void hotterIntakeMovesBothRootsAwayFromStoichiometric() {
        SideResult leanCold = solveSide("methane", INTAKE, TARGET, Side.LEAN);
        SideResult richCold = solveSide("methane", INTAKE, TARGET, Side.RICH);
        SideResult leanHot = solveSide("methane", 398.15, TARGET, Side.LEAN);
        SideResult richHot = solveSide("methane", 398.15, TARGET, Side.RICH);

        assertTrue(leanHot.getEquivalenceRatio() < leanCold.getEquivalenceRatio(),
                "hotter intake reaches the same flame temperature with a leaner mixture: "
                        + leanHot.getEquivalenceRatio() + " vs " + leanCold.getEquivalenceRatio());
        assertTrue(richHot.getEquivalenceRatio() > richCold.getEquivalenceRatio(),
                "hotter intake pushes the rich root further rich: "
                        + richHot.getEquivalenceRatio() + " vs " + richCold.getEquivalenceRatio());
        for (SideResult side : List.of(leanHot, richHot)) {
            assertTrue(Math.abs(side.getFlameTemperature() - TARGET)
                            <= inverseProperties.temperatureTolerance(),
                    "shifted root must still forward-verify against the target");
        }
    }

    @Test
    void sideThatRunsOutOfStepsFailsWithoutReportingARatio() {
        SideRootFinder crippled = new SideRootFinder(forward, new InverseProperties(0.5, 3));
        PeakResult peak = peakLocator.locate(fuel("methane"), INTAKE);
        SideResult result = crippled.findRoot(fuel("methane"), INTAKE, TARGET, Side.LEAN, peak);

        assertFalse(result.isConverged());
        assertNull(result.getEquivalenceRatio(), "a side out of steps must not report a ratio");
        assertEquals(ErrorType.EQUIVALENCE_RATIO_NOT_CONVERGED, result.getError().type());
        assertEquals(3, result.getConvergence().size(), "the failed side keeps the trials it used");
        double lastDeviation = Math.abs(result.getConvergence().get(2).temperatureDeviation());
        assertTrue(lastDeviation > 0.5, "the failed side must not pretend the target was met");
    }

    @Test
    void eachSideAccumulatesItsOwnSequentialTrace() {
        SideResult lean = solveSide("methane", INTAKE, TARGET, Side.LEAN);
        SideResult rich = solveSide("methane", INTAKE, TARGET, Side.RICH);
        PeakResult peak = peakLocator.locate(fuel("methane"), INTAKE);

        assertTraceClosesBelowTolerance(lean);
        assertTraceClosesBelowTolerance(rich);
        for (PhiTracePoint point : lean.getConvergence()) {
            assertTrue(point.equivalenceRatio() <= peak.equivalenceRatio() + 1e-9,
                    "lean trace must stay on the lean side of the peak");
        }
        for (PhiTracePoint point : rich.getConvergence()) {
            assertTrue(point.equivalenceRatio() >= peak.equivalenceRatio() - 1e-9,
                    "rich trace must stay on the rich side of the peak");
        }
    }

    private void assertTraceClosesBelowTolerance(SideResult side) {
        List<PhiTracePoint> trace = side.getConvergence();
        assertFalse(trace.isEmpty(), "convergence trace must be recorded");
        for (int i = 0; i < trace.size(); i++) {
            assertEquals(i, trace.get(i).step(), "trace steps must be sequential");
        }
        double lastDeviation = Math.abs(trace.get(trace.size() - 1).temperatureDeviation());
        assertTrue(lastDeviation <= inverseProperties.temperatureTolerance(),
                "converged trace must close below tolerance, last deviation " + lastDeviation);
    }
}
