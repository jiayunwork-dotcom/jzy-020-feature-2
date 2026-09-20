package com.flamelab.fuel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.flamelab.thermo.Species;

/** Pinned catalog of supported fuels. */
@Component
public class FuelCatalog {

    private final Map<String, Fuel> fuels = new LinkedHashMap<>();

    public FuelCatalog() {
        register(new Fuel("methane", Species.CH4, 1, 4));
        register(new Fuel("ethane", Species.C2H6, 2, 6));
    }

    private void register(Fuel fuel) {
        fuels.put(fuel.id(), fuel);
    }

    public Optional<Fuel> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(fuels.get(id.trim().toLowerCase()));
    }

    public Set<String> supportedIds() {
        return fuels.keySet();
    }
}
