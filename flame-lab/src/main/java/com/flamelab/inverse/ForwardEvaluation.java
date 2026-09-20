package com.flamelab.inverse;

import com.flamelab.balance.BalanceResult;

/**
 * One forward balance at a trial equivalence ratio: the iterated flame
 * temperature plus the closed element balance behind it.
 */
public record ForwardEvaluation(double equivalenceRatio, double flameTemperature, BalanceResult balance) {
}
