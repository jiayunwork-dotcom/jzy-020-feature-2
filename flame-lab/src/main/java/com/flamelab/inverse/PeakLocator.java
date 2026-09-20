package com.flamelab.inverse;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.flamelab.fuel.Fuel;

/**
 * Locates the peak of the non-monotonic flame-temperature versus
 * equivalence-ratio curve, using only forward balances through
 * {@link ForwardEvaluator}.
 *
 * The curve rises from very lean mixtures, peaks near stoichiometric and falls
 * on the rich side as excess fuel soaks up heat. Locating the peak happens in
 * two stages, both of them repeated forward solves — never a table or a fit:
 * <ol>
 *     <li>a geometric scan over phi brackets a discrete maximum, extending the
 *         range if the hottest sampled point sits on an edge;</li>
 *     <li>a golden-section search inside that bracket refines the maximum to
 *         the pinned relative width.</li>
 * </ol>
 */
@Component
public class PeakLocator {

    /** Geometric grid spans [phiMin, phiMax]; extended on demand. */
    private static final double PHI_MIN = 0.3;
    private static final double PHI_MAX = 3.0;
    private static final double PHI_FLOOR = 1.0e-3;
    private static final double PHI_CEIL = 1.0e2;
    private static final double GRID_RATIO = 1.12;
    private static final double GOLDEN_INV = 0.6180339887498949;
    private static final double PEAK_RELATIVE_WIDTH = 1.0e-8;

    private final ForwardEvaluator forward;

    public PeakLocator(ForwardEvaluator forward) {
        this.forward = forward;
    }

    public Peak locate(Fuel fuel, double intakeTemperature) {
        List<ForwardEvaluator.Probe> grid = scan(fuel, intakeTemperature);
        int hottest = indexOfHottest(grid);
        double lower = grid.get(hottest - 1).equivalenceRatio();
        double upper = grid.get(hottest + 1).equivalenceRatio();
        return refine(fuel, intakeTemperature, lower, upper,
                grid.get(hottest - 1), grid.get(hottest), grid.get(hottest + 1));
    }

    /** Geometric scan, expanding downward/upward while the edge point is hottest. */
    private List<ForwardEvaluator.Probe> scan(Fuel fuel, double intakeTemperature) {
        List<ForwardEvaluator.Probe> grid = new ArrayList<>();
        for (double phi = PHI_MIN; phi <= PHI_MAX; phi *= GRID_RATIO) {
            grid.add(forward.evaluate(fuel, phi, intakeTemperature));
        }
        grid.add(forward.evaluate(fuel, PHI_MAX, intakeTemperature));

        // Extend the lean edge downward while temperatures still climb toward the peak.
        while (hottestIndex(grid) == 0 && grid.get(0).equivalenceRatio() > PHI_FLOOR) {
            double next = Math.max(PHI_FLOOR, grid.get(0).equivalenceRatio() / GRID_RATIO);
            if (next >= grid.get(0).equivalenceRatio()) {
                break;
            }
            grid.add(0, forward.evaluate(fuel, next, intakeTemperature));
        }
        // Extend the rich edge upward while temperatures still climb toward the peak.
        int last = grid.size() - 1;
        while (hottestIndex(grid) == last && grid.get(last).equivalenceRatio() < PHI_CEIL) {
            double next = Math.min(PHI_CEIL, grid.get(last).equivalenceRatio() * GRID_RATIO);
            if (next <= grid.get(last).equivalenceRatio()) {
                break;
            }
            grid.add(forward.evaluate(fuel, next, intakeTemperature));
            last++;
        }
        int hottest = hottestIndex(grid);
        if (hottest == 0 || hottest == grid.size() - 1) {
            throw new InverseException(com.flamelab.job.ErrorType.TEMPERATURE_OUT_OF_RANGE,
                    "flame-temperature peak lies outside the searchable equivalence-ratio window ["
                            + PHI_FLOOR + ", " + PHI_CEIL + "]", null, List.of(), List.of());
        }
        return grid;
    }

    private int hottestIndex(List<ForwardEvaluator.Probe> grid) {
        int best = 0;
        for (int i = 1; i < grid.size(); i++) {
            if (grid.get(i).temperature() > grid.get(best).temperature()) {
                best = i;
            }
        }
        return best;
    }

    private int indexOfHottest(List<ForwardEvaluator.Probe> grid) {
        int hottest = hottestIndex(grid);
        if (hottest == 0 || hottest == grid.size() - 1) {
            throw new InverseException(com.flamelab.job.ErrorType.TEMPERATURE_OUT_OF_RANGE,
                    "could not bracket the flame-temperature peak on the geometric scan",
                    null, List.of(), List.of());
        }
        return hottest;
    }

    /**
     * Golden-section maximum search. The three interior probes are reused from
     * the scan; each refinement step is one more genuine forward solve.
     */
    private Peak refine(Fuel fuel, double intakeTemperature, double lower, double upper,
                        ForwardEvaluator.Probe lowProbe,
                        ForwardEvaluator.Probe middleProbe,
                        ForwardEvaluator.Probe highProbe) {
        double x1 = lowProbe.equivalenceRatio();
        double f1 = lowProbe.temperature();
        double xm = middleProbe.equivalenceRatio();
        double fm = middleProbe.temperature();
        double x2 = highProbe.equivalenceRatio();
        double f2 = highProbe.temperature();

        double lo = lower;
        double hi = upper;
        while ((hi - lo) / Math.max(1.0, xm) > PEAK_RELATIVE_WIDTH) {
            if (xm - lo > hi - xm) {
                double nx = lo + GOLDEN_INV * (xm - lo);
                double nf = forward.evaluate(fuel, nx, intakeTemperature).temperature();
                if (nf > fm) {
                    hi = xm;
                    x2 = xm;
                    f2 = fm;
                    xm = nx;
                    fm = nf;
                } else {
                    lo = nx;
                    x1 = nx;
                    f1 = nf;
                }
            } else {
                double nx = hi - GOLDEN_INV * (hi - xm);
                double nf = forward.evaluate(fuel, nx, intakeTemperature).temperature();
                if (nf > fm) {
                    lo = xm;
                    x1 = xm;
                    f1 = fm;
                    xm = nx;
                    fm = nf;
                } else {
                    hi = nx;
                    x2 = nx;
                    f2 = nf;
                }
            }
        }
        return new Peak(xm, fm);
    }
}
