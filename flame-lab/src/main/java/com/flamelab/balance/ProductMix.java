package com.flamelab.balance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.flamelab.thermo.Species;

/**
 * Mole amounts on both sides of the balance, per 1 mol of fuel fed. Total mole
 * numbers are deliberately NOT assumed constant across the reaction; they are
 * summed from the actual species amounts on each side.
 */
public record ProductMix(EnumMap<Species, Double> reactantMoles, EnumMap<Species, Double> productMoles) {

    public ProductMix {
        reactantMoles = new EnumMap<>(reactantMoles);
        productMoles = new EnumMap<>(productMoles);
    }

    @Override
    public EnumMap<Species, Double> reactantMoles() {
        return new EnumMap<>(reactantMoles);
    }

    @Override
    public EnumMap<Species, Double> productMoles() {
        return new EnumMap<>(productMoles);
    }

    public double totalProductMoles() {
        return productMoles.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double totalReactantMoles() {
        return reactantMoles.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Product mole fractions keyed by species name; sums to 1. */
    public Map<String, Double> productMoleFractions() {
        double total = totalProductMoles();
        Map<String, Double> fractions = new LinkedHashMap<>();
        productMoles.forEach((species, moles) -> fractions.put(species.name(), moles / total));
        return Collections.unmodifiableMap(fractions);
    }
}
