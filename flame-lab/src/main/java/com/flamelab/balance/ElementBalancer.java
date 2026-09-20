package com.flamelab.balance;

import java.util.EnumMap;

import org.springframework.stereotype.Component;

import com.flamelab.fuel.Fuel;
import com.flamelab.thermo.Species;

/**
 * Element balance for constant-pressure combustion of a hydrocarbon with air
 * (pinned composition: O2 0.21, N2 0.79 by mole).
 *
 * Basis: 1 mol of fuel. The product set is pinned to CO2, H2O, O2, N2; there
 * is no CO/H2 dissociation. Lean mixtures are diluted by excess O2, rich
 * mixtures by unburned fuel (only the fraction 1/phi of the fuel finds enough
 * oxygen). N2 is inert: product N2 moles always equal intake N2 moles.
 */
@Component
public class ElementBalancer {

    /** Pinned oxidizer composition: dry air, mole fraction of O2. */
    public static final double OXYGEN_MOLE_FRACTION_IN_AIR = 0.21;

    /** N2 moles accompanying each mole of O2 in air: 0.79 / 0.21. */
    public static final double NITROGEN_PER_OXYGEN =
            (1.0 - OXYGEN_MOLE_FRACTION_IN_AIR) / OXYGEN_MOLE_FRACTION_IN_AIR;

    public BalanceResult balance(Fuel fuel, double equivalenceRatio) {
        double stoichO2 = fuel.stoichiometricOxygen();
        double o2In = stoichO2 / equivalenceRatio;
        double n2In = o2In * NITROGEN_PER_OXYGEN;

        // Fraction of fuel that actually burns; oxygen-limited when rich.
        double burned = Math.min(1.0, o2In / stoichO2);

        EnumMap<Species, Double> reactants = new EnumMap<>(Species.class);
        reactants.put(fuel.species(), 1.0);
        reactants.put(Species.O2, o2In);
        reactants.put(Species.N2, n2In);

        EnumMap<Species, Double> products = new EnumMap<>(Species.class);
        products.put(Species.CO2, fuel.carbon() * burned);
        products.put(Species.H2O, fuel.hydrogen() / 2.0 * burned);
        products.put(Species.O2, Math.max(0.0, o2In - stoichO2 * burned));
        products.put(Species.N2, n2In);
        double unburned = 1.0 - burned;
        if (unburned > 0.0) {
            products.put(fuel.species(), unburned);
        }

        ProductMix mix = new ProductMix(reactants, products);
        return BalanceResult.of(mix, atomResiduals(mix));
    }

    /** Atoms in minus atoms out for every tracked element, computed from the species composition data. */
    private EnumMap<Element, Double> atomResiduals(ProductMix mix) {
        EnumMap<Element, Double> incoming = atoms(mix.reactantMoles());
        EnumMap<Element, Double> outgoing = atoms(mix.productMoles());
        EnumMap<Element, Double> residuals = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            residuals.put(element, incoming.get(element) - outgoing.get(element));
        }
        return residuals;
    }

    private EnumMap<Element, Double> atoms(EnumMap<Species, Double> moles) {
        EnumMap<Element, Double> totals = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            totals.put(element, 0.0);
        }
        moles.forEach((species, amount) -> {
            totals.merge(Element.C, amount * species.carbon(), Double::sum);
            totals.merge(Element.H, amount * species.hydrogen(), Double::sum);
            totals.merge(Element.O, amount * species.oxygen(), Double::sum);
            totals.merge(Element.N, amount * species.nitrogen(), Double::sum);
        });
        return totals;
    }
}
