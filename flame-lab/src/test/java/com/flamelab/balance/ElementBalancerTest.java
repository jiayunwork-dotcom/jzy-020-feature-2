package com.flamelab.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.flamelab.fuel.FuelCatalog;
import com.flamelab.thermo.Species;

/** Element-balance invariants pinned by the service contract. */
class ElementBalancerTest {

    private final FuelCatalog fuels = new FuelCatalog();
    private final ElementBalancer balancer = new ElementBalancer();

    @Test
    void nitrogenIsInertProductNitrogenEqualsIntakeNitrogen() {
        for (double phi : new double[]{0.8, 1.0, 1.2}) {
            BalanceResult balance = balancer.balance(fuels.find("methane").orElseThrow(), phi);
            double n2In = balance.mix().reactantMoles().get(Species.N2);
            double n2Out = balance.mix().productMoles().get(Species.N2);
            assertEquals(n2In, n2Out, 1e-12, "product N2 must equal intake N2 for phi=" + phi);
        }
    }

    @Test
    void totalMolesAreNotAssumedConstant() {
        BalanceResult ethane = balancer.balance(fuels.find("ethane").orElseThrow(), 1.0);
        assertNotEquals(ethane.mix().totalReactantMoles(), ethane.mix().totalProductMoles(),
                "ethane combustion changes the total mole number");
        assertTrue(ethane.mix().totalProductMoles() > ethane.mix().totalReactantMoles());
    }

    @Test
    void stoichiometricMethaneHasNoExcessOxygenAndNoUnburnedFuel() {
        BalanceResult balance = balancer.balance(fuels.find("methane").orElseThrow(), 1.0);
        assertEquals(0.0, balance.mix().productMoles().get(Species.O2), 1e-12);
        assertTrue(!balance.mix().productMoles().containsKey(Species.CH4));
    }

    @Test
    void richMixtureIsDilutedByUnburnedFuel() {
        BalanceResult balance = balancer.balance(fuels.find("methane").orElseThrow(), 1.25);
        assertEquals(0.0, balance.mix().productMoles().get(Species.O2), 1e-12);
        assertEquals(1.0 - 1.0 / 1.25, balance.mix().productMoles().get(Species.CH4), 1e-12);
    }

    @Test
    void productMoleFractionsSumToOne() {
        for (double phi : new double[]{0.8, 1.0, 1.2}) {
            BalanceResult balance = balancer.balance(fuels.find("ethane").orElseThrow(), phi);
            double sum = balance.mix().productMoleFractions().values().stream()
                    .mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 1e-12);
        }
    }

    @Test
    void airCompositionIsPinned() {
        BalanceResult balance = balancer.balance(fuels.find("methane").orElseThrow(), 1.0);
        Map<Species, Double> reactants = balance.mix().reactantMoles();
        double ratio = reactants.get(Species.N2) / reactants.get(Species.O2);
        assertEquals(0.79 / 0.21, ratio, 1e-12, "air must stay pinned at O2 0.21 / N2 0.79");
    }
}
