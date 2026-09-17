package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/**
 * Deterministic semantic validator for institution-authored EWS policies.
 * Phase 1 deliberately keeps validator logic independent of Drools/DMN.
 */
public final class PolicySemanticValidator {
    public record Finding(String ruleId, Severity severity, String code, String message, String jsonPointer) {}
    public enum Severity { ERROR, WARNING, INFO }

    public List<Finding> validate(JsonNode policy) {
        List<Finding> findings = new ArrayList<>();
        Map<String, JsonNode> parameters = indexBy(policy.path("parameters"), "name", findings, "PSV-019", "/parameters");
        Set<String> ruleIds = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        String mode = policy.path("evaluation").path("mode").asText();

        int i = 0;
        for (JsonNode rule : policy.path("rules")) {
            String ptr = "/rules/" + i++;
            String ruleId = rule.path("ruleId").asText();
            if (!ruleIds.add(ruleId)) findings.add(error("PSV-019", "DUPLICATE_RULE_ID", "Duplicate ruleId: " + ruleId, ptr + "/ruleId"));
            if ("PRIORITY".equals(mode) && rule.path("enabled").asBoolean()) {
                int p = rule.path("priority").asInt();
                if (!priorities.add(p)) findings.add(error("PSV-007", "DUPLICATE_PRIORITY", "Enabled PRIORITY rules must have unique priorities: " + p, ptr + "/priority"));
            }
            validateExpression(rule.path("condition"), parameters, findings, ptr + "/condition");
        }

        JsonNode from = policy.path("effectivePeriod").path("effectiveFrom");
        JsonNode to = policy.path("effectivePeriod").path("effectiveTo");
        if (from.isTextual() && to.isTextual() && !to.asText().isBlank()) {
            try {
                if (!java.time.Instant.parse(to.asText()).isAfter(java.time.Instant.parse(from.asText())))
                    findings.add(error("PSV-009", "INVALID_EFFECTIVE_PERIOD", "effectiveTo must be later than effectiveFrom", "/effectivePeriod"));
            } catch (Exception ex) {
                findings.add(error("PSV-009", "INVALID_EFFECTIVE_PERIOD", "Effective timestamps must be ISO-8601 instants", "/effectivePeriod"));
            }
        }

        if ("ACTIVE".equals(policy.path("lifecycle").asText()) && policy.path("governance").path("makerCheckerRequired").asBoolean()) {
            if (policy.path("governance").path("approvedBy").isNull() || policy.path("governance").path("approvedAt").isNull())
                findings.add(error("PSV-011", "MISSING_APPROVAL", "ACTIVE maker-checker policy requires approval identity and timestamp", "/governance"));
            if (policy.path("governance").path("simulationRunId").isNull())
                findings.add(error("PSV-011", "MISSING_SIMULATION", "ACTIVE policy requires accepted simulation evidence", "/governance/simulationRunId"));
        }

        String engine = policy.path("evaluation").path("engine").asText();
        if (("DMN".equals(engine) || "DROOLS".equals(engine)) && policy.path("evaluation").path("externalArtifactRef").isNull())
            findings.add(error("PSV-012", "MISSING_ENGINE_ARTIFACT", engine + " policy requires an approved artifact reference", "/evaluation/externalArtifactRef"));

        return List.copyOf(findings);
    }

    private void validateExpression(JsonNode node, Map<String, JsonNode> parameters, List<Finding> findings, String ptr) {
        if (node.has("all")) { int n=0; for (JsonNode c: node.path("all")) validateExpression(c, parameters, findings, ptr+"/all/"+n++); return; }
        if (node.has("any")) { int n=0; for (JsonNode c: node.path("any")) validateExpression(c, parameters, findings, ptr+"/any/"+n++); return; }
        if (node.has("not")) { validateExpression(node.path("not"), parameters, findings, ptr+"/not"); return; }
        validateOperand(node.path("left"), parameters, findings, ptr+"/left");
        validateOperand(node.path("right"), parameters, findings, ptr+"/right");
    }

    private void validateOperand(JsonNode operand, Map<String, JsonNode> parameters, List<Finding> findings, String ptr) {
        if (operand.has("parameter") && !parameters.containsKey(operand.path("parameter").asText()))
            findings.add(error("PSV-001", "UNKNOWN_PARAMETER", "Referenced parameter does not exist: " + operand.path("parameter").asText(), ptr));
        // PSV-002 feature catalogue resolution is intentionally injected in the next registry-aware pass.
    }

    private Map<String, JsonNode> indexBy(JsonNode array, String field, List<Finding> findings, String ruleId, String ptr) {
        Map<String, JsonNode> map = new HashMap<>(); int i=0;
        for (JsonNode item: array) { String key=item.path(field).asText(); if (map.putIfAbsent(key,item)!=null) findings.add(error(ruleId,"DUPLICATE_ID","Duplicate " + field + ": " + key,ptr+"/"+i)); i++; }
        return map;
    }

    private Finding error(String rule, String code, String message, String ptr) { return new Finding(rule, Severity.ERROR, code, message, ptr); }
}