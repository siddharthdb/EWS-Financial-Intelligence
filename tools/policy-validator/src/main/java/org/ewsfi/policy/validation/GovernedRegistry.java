package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Immutable snapshot of registries used for one validation run. */
public record GovernedRegistry(
        String featureRegistryVersion,
        Map<String, FeatureRef> features,
        String signalRegistryVersion,
        Map<String, SignalRef> signals,
        String riskDimensionRegistryVersion,
        Set<String> riskDimensions) {

    public record FeatureRef(String name, String semanticScope, String entityGrain, String valueType, String window) {}
    public record SignalRef(String code, String status, String semanticScope) {}

    public static GovernedRegistry from(JsonNode featureRegistry, JsonNode signalRegistry, JsonNode riskRegistry) {
        Map<String, FeatureRef> fs = new HashMap<>();
        for (JsonNode f : featureRegistry.path("features")) fs.put(f.path("featureName").asText(), new FeatureRef(f.path("featureName").asText(), f.path("semanticScope").asText(), f.path("entityGrain").asText(), f.path("valueType").asText(), f.path("window").isNull()?null:f.path("window").asText()));
        Map<String, SignalRef> ss = new HashMap<>();
        for (JsonNode s : signalRegistry.path("signals")) ss.put(s.path("signalCode").asText(), new SignalRef(s.path("signalCode").asText(), s.path("status").asText(), s.path("semanticScope").asText()));
        Set<String> ds = new HashSet<>(); for (JsonNode d : riskRegistry.path("dimensions")) ds.add(d.asText());
        return new GovernedRegistry(featureRegistry.path("version").asText(), Map.copyOf(fs), signalRegistry.path("version").asText(), Map.copyOf(ss), riskRegistry.path("version").asText(), Set.copyOf(ds));
    }
}