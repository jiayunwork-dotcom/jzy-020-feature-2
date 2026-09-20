package com.flamelab.thermo;

/**
 * Species known to the service. Each carries its elemental composition so the
 * element balance can be checked generically instead of being hard-coded to zero.
 */
public enum Species {

    CH4(1, 4, 0, 0),
    C2H6(2, 6, 0, 0),
    CO2(1, 0, 2, 0),
    H2O(0, 2, 1, 0),
    O2(0, 0, 2, 0),
    N2(0, 0, 0, 2);

    private final int carbon;
    private final int hydrogen;
    private final int oxygen;
    private final int nitrogen;

    Species(int carbon, int hydrogen, int oxygen, int nitrogen) {
        this.carbon = carbon;
        this.hydrogen = hydrogen;
        this.oxygen = oxygen;
        this.nitrogen = nitrogen;
    }

    public int carbon() {
        return carbon;
    }

    public int hydrogen() {
        return hydrogen;
    }

    public int oxygen() {
        return oxygen;
    }

    public int nitrogen() {
        return nitrogen;
    }
}
