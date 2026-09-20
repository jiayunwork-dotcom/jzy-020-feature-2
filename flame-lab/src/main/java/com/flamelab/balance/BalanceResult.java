package com.flamelab.balance;

import java.util.EnumMap;

/** Element balance plus the resulting product/reactant composition. */
public record BalanceResult(ProductMix mix, AtomBalance atoms) {

    public static BalanceResult of(ProductMix mix, EnumMap<Element, Double> residuals) {
        double max = residuals.values().stream().mapToDouble(Math::abs).max().orElse(0.0);
        return new BalanceResult(mix, new AtomBalance(new EnumMap<>(residuals), max));
    }
}
