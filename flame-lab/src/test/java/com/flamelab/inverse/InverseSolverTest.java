package com.flamelab.inverse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.flamelab.balance.ElementBalancer;
import com.flamelab.fuel.Fuel;
import com.flamelab.fuel.FuelCatalog;
import com.flamelab.job.ErrorType;
import com.flamelab.job.InputValidationException;
import com.flamelab.solver.FlameSolver;
import com.flamelab.solver.SolverProperties;
import com.flamelab.thermo.EnthalpyCalculator;
import com.flamelab.thermo.ThermoDatabase;
import com.flamelab.thermo.ThermoLimits;

/**
 * Regression tests for the inverse search itself: peak location, lean/rich
 * double roots with forward re-check closure, the branches approaching each
 * other near the peak, unreachable targets reporting the peak, fuel and intake
 * dependence, pre-outer-loop validation, and per-side trace separation.
 */
class InverseSolverTest {

    private static final double INTAKE = 298.15;
    private static final double TARGET = 2150.0;

    private final ThermoLimits limits = ThermoLimits.pinned();
    private final ThermoDatabase database = new ThermoDatabase(limits);
    private final EnthalpyCalculator enthalpy = new EnthalpyCalculator(database);
    private final FuelCatalog fuels = new FuelCatalog();
    private final ElementBalancer balancer = new ElementBalancer();
    private final SolverProperties solverProperties = SolverProperties.pinned();
    private final FlameSolver flameSolver = new FlameSolver(enthalpy, limits, solverProperties);
    private final ForwardEvaluator evaluator = new ForwardEvaluator(balancer, flameSolver);
    private final InverseProperties inverseProperties = InverseProperties.pinned();
    private final PeakLocator peakLocator = new PeakLocator(evaluator);
    private final SideRootFinder sideRootFinder = new SideRootFinder(evaluator, inverseProperties);
    private final InverseSolver solver = new InverseSolver(peakLocator, sideRootFinder, solverProperties);
    private final InverseInputValidator validator = new InverseInputValidator(fuels);

    private InverseOutcome solve(String fuelId, double intake, double target, RequestSide side) {
        Fuel fuel = fuels.find(fuelId).orElseThrow();
        return solver.solve(fuel, intake, target, side);
    }

    @Test
    void targetBelowPeakGivesTwoDistinctForwardVerifiedRoots() {
        InverseOutcome out = solve("methane", INTAKE, TARGET, RequestSide.BOTH);
        assertNotNull(out.lean());
        assertNotNull(out.rich());
        assertTrue(out.lean().equivalenceRatio() < 1.0,
                "lean root must be below stoichiometric, got " + out.lean().equivalenceRatio());
        assertTrue(out.rich().equivalenceRatio() > 1.0,
                "rich root must be above stoichiometric, got " + out.rich().equivalenceRatio());
        assertTrue(out.rich().equivalenceRatio() - out.lean().equivalenceRatio() > 0.1,
                "the two roots must be clearly distinct");

        for (SideRootFinder.SideResult side : new SideRootFinder.SideResult[]{out.lean(), out.rich()}) {
            assertTrue(side.equivalenceRatio() > 0.0, "reported phi must be positive");
            assertTrue(Math.abs(side.temperatureDeviation()) <= inverseProperties.temperatureTolerance(),
                    "forward re-check deviation " + side.temperatureDeviation()
                            + " K exceeds pinned tolerance");
            assertTrue(side.maxAtomResidual() < solverProperties.atomBalanceThreshold(),
                    "atoms must close, residual " + side.maxAtomResidual());
            assertTrue(side.productMoleFractions().containsKey("N2"));
            assertTrue(side.sequence().size() >= 2, "outer search must leave a trace");
            for (OuterStep step : side.sequence()) {
                assertTrue(step.equivalenceRatio() > 0.0);
            }
        }
    }

    @Test
    void leanAndRichTracesAreSeparateSequences() {
        InverseOutcome out = solve("methane", INTAKE, TARGET, RequestSide.BOTH);
        assertEquals(out.lean().sequence().size(),
                out.lean().sequence().stream().map(OuterStep::step).distinct().count(),
                "lean steps must be numbered within the lean sequence");
        assertEquals(out.rich().sequence().size(),
                out.rich().sequence().stream().map(OuterStep::step).distinct().count(),
                "rich steps must be numbered within the rich sequence");
        assertEquals(out.lean().equivalenceRatio(),
                out.lean().sequence().get(out.lean().sequence().size() - 1).equivalenceRatio(),
                1e-12, "lean trace must end at the reported lean phi");
        assertEquals(out.rich().equivalenceRatio(),
                out.rich().sequence().get(out.rich().sequence().size() - 1).equivalenceRatio(),
                1e-12, "rich trace must end at the reported rich phi");
        // First recorded probe of each side is its own peak probe, then it walks outward.
        assertTrue(out.lean().sequence().get(1).equivalenceRatio() < out.peak().equivalenceRatio());
        assertTrue(out.rich().sequence().get(1).equivalenceRatio() > out.peak().equivalenceRatio());
    }

    @Test
    void rootsConvergeTowardStoichiometricAsTargetApproachesPeak() {
        Peak peak = peakLocator.locate(fuels.find("methane").orElseThrow(), INTAKE);
        InverseOutcome low = solve("methane", INTAKE, peak.temperature() - 175.0, RequestSide.BOTH);
        InverseOutcome near = solve("methane", INTAKE, peak.temperature() - 3.0, RequestSide.BOTH);
        double gapLow = low.rich().equivalenceRatio() - low.lean().equivalenceRatio();
        double gapNear = near.rich().equivalenceRatio() - near.lean().equivalenceRatio();
        assertTrue(gapNear < gapLow,
                "roots must approach each other near the peak: " + gapLow + " -> " + gapNear);
        assertTrue(Math.abs(near.lean().equivalenceRatio() - peak.equivalenceRatio()) < 0.02);
        assertTrue(Math.abs(near.rich().equivalenceRatio() - peak.equivalenceRatio()) < 0.02);
        assertTrue(Math.abs(near.lean().temperatureDeviation()) <= inverseProperties.temperatureTolerance());
        assertTrue(Math.abs(near.rich().temperatureDeviation()) <= inverseProperties.temperatureTolerance());
    }

    @Test
    void targetAbovePeakFailsAndReportsPeak() {
        Peak peak = peakLocator.locate(fuels.find("methane").orElseThrow(), INTAKE);
        double unreachable = peak.temperature() + 200.0;
        InverseException e = assertThrows(InverseException.class,
                () -> solve("methane", INTAKE, unreachable, RequestSide.BOTH));
        assertEquals(ErrorType.TARGET_UNREACHABLE, e.type());
        assertNotNull(e.peak());
        assertEquals(peak.temperature(), e.peak().temperature(), 1.0e-6);
        assertEquals(peak.equivalenceRatio(), e.peak().equivalenceRatio(), 1.0e-6);
        assertTrue(e.peak().temperature() < unreachable,
                "reported peak must be below the unreachable target");
        // No root is reported, but the first peak probe is still traced.
        assertTrue(e.leanSteps().size() >= 1 || e.richSteps().size() >= 1);
    }

    @Test
    void ethaneInverseRootsAreNotMethaneRoots() {
        InverseOutcome methane = solve("methane", INTAKE, TARGET, RequestSide.BOTH);
        InverseOutcome ethane = solve("ethane", INTAKE, TARGET, RequestSide.BOTH);
        assertTrue(Math.abs(ethane.lean().equivalenceRatio() - methane.lean().equivalenceRatio()) > 1.0e-3,
                "ethane and methane lean phis must differ");
        assertTrue(Math.abs(ethane.rich().equivalenceRatio() - methane.rich().equivalenceRatio()) > 1.0e-2,
                "ethane and methane rich phis must differ");
        assertTrue(Math.abs(ethane.peak().temperature() - methane.peak().temperature()) > 1.0,
                "ethane and methane peaks must differ");
        assertTrue(Math.abs(ethane.lean().temperatureDeviation()) <= inverseProperties.temperatureTolerance());
        assertTrue(Math.abs(ethane.rich().temperatureDeviation()) <= inverseProperties.temperatureTolerance());
    }

    @Test
    void hotterIntakeMovesTheRootsForTheSameTarget() {
        InverseOutcome cold = solve("methane", INTAKE, TARGET, RequestSide.BOTH);
        InverseOutcome hot = solve("methane", 450.0, TARGET, RequestSide.BOTH);
        assertTrue(hot.lean().equivalenceRatio() < cold.lean().equivalenceRatio(),
                "a hotter intake reaches the same target on the lean side with a leaner mixture: "
                        + cold.lean().equivalenceRatio() + " -> " + hot.lean().equivalenceRatio());
        assertForwardVerified(hot.lean());
        assertForwardVerified(hot.rich());
    }

    @Test
    void singleSideRequestReturnsOnlyThatSide() {
        InverseOutcome lean = solve("methane", INTAKE, TARGET, RequestSide.LEAN);
        assertNotNull(lean.lean());
        assertNull(lean.rich());
        InverseOutcome rich = solve("methane", INTAKE, TARGET, RequestSide.RICH);
        assertNull(rich.lean());
        assertNotNull(rich.rich());
        assertForwardVerified(rich.rich());
    }

    @Test
    void lowTargetNearCpWindowStillBracketsWithFiniteProbes() {
        // Very lean flames at cold intake approach the 250 K Cp-window edge; the
        // outer search must retract from un-evaluable probes and still return a
        // forward-verified lean phi rather than feeding NaN into the bisection.
        InverseOutcome out = solve("methane", 251.0, 600.0, RequestSide.BOTH);
        assertForwardVerified(out.lean());
        assertForwardVerified(out.rich());
        for (OuterStep step : out.lean().sequence()) {
            assertTrue(Double.isFinite(step.temperatureDeviation()),
                    "stored lean probes must be finite after retraction");
        }
    }

    @Test
    void invalidInputIsRejectedBeforeAnyOuterProbe() {
        assertRejected(null, INTAKE, TARGET, "BOTH", ErrorType.MISSING_FIELD);
        assertRejected("methane", null, TARGET, "BOTH", ErrorType.MISSING_FIELD);
        assertRejected("methane", INTAKE, null, "BOTH", ErrorType.MISSING_FIELD);
        assertRejected("methane", INTAKE, TARGET, null, ErrorType.MISSING_FIELD);
        assertRejected("methane", Double.NaN, TARGET, "BOTH", ErrorType.NON_FINITE_VALUE);
        assertRejected("methane", INTAKE, Double.POSITIVE_INFINITY, "BOTH", ErrorType.NON_FINITE_VALUE);
        assertRejected("methane", 0.0, TARGET, "BOTH", ErrorType.NON_POSITIVE_INTAKE_TEMPERATURE);
        assertRejected("methane", -10.0, TARGET, "BOTH", ErrorType.NON_POSITIVE_INTAKE_TEMPERATURE);
        assertRejected("methane", INTAKE, 0.0, "BOTH", ErrorType.NON_POSITIVE_TARGET_TEMPERATURE);
        assertRejected("methane", INTAKE, -273.0, "BOTH", ErrorType.NON_POSITIVE_TARGET_TEMPERATURE);
        assertRejected("methane", INTAKE, TARGET, "UP", ErrorType.ILLEGAL_SIDE);
        assertRejected("methane", INTAKE, TARGET, "richish", ErrorType.ILLEGAL_SIDE);
        assertRejected("propane", INTAKE, TARGET, "BOTH", ErrorType.UNKNOWN_FUEL);
    }

    private void assertRejected(String fuel, Double intake, Double target, String side, ErrorType type) {
        InputValidationException e = assertThrows(InputValidationException.class,
                () -> validator.validate(fuel, intake, target, side));
        assertEquals(type, e.type());
    }

    private void assertForwardVerified(SideRootFinder.SideResult side) {
        assertTrue(side.equivalenceRatio() > 0.0);
        assertTrue(Math.abs(side.temperatureDeviation()) <= inverseProperties.temperatureTolerance());
        assertTrue(side.maxAtomResidual() < solverProperties.atomBalanceThreshold());
    }
}
