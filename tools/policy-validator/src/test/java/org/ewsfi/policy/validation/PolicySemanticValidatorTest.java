package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolicySemanticValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PolicySemanticValidator validator = new PolicySemanticValidator();

    @Test void rejectsUnknownParameterReference() throws Exception {
        var policy = mapper.readTree(basePolicy().replace("THRESHOLD_A\"}", "MISSING_THRESHOLD\"}"));
        assertTrue(validator.validate(policy).stream().anyMatch(f -> f.ruleId().equals("PSV-001")));
    }

    @Test void rejectsDuplicatePriorityInPriorityMode() throws Exception {
        var policy = mapper.readTree(basePolicy().replace("\"mode\":\"ALL_MATCHES\"", "\"mode\":\"PRIORITY\"").replace("\"rules\":[", "\"rules\":[{\"ruleId\":\"R0\",\"enabled\":true,\"priority\":100,\"condition\":{\"left\":{\"feature\":\"x\"},\"operator\":\"GTE\",\"right\":{\"parameter\":\"THRESHOLD_A\"}},\"outcome\":{}},"));
        assertTrue(validator.validate(policy).stream().anyMatch(f -> f.ruleId().equals("PSV-007")));
    }

    @Test void acceptsMinimalCoherentDraft() throws Exception {
        assertTrue(validator.validate(mapper.readTree(basePolicy())).isEmpty());
    }

    private String basePolicy() {
        return """
        {"policyId":"P1","policyKey":"TEST_POLICY","version":1,"lifecycle":"VALIDATION",
         "effectivePeriod":{"effectiveFrom":"2026-09-17T00:00:00Z","effectiveTo":null},
         "evaluation":{"engine":"NATIVE","mode":"ALL_MATCHES"},
         "parameters":[{"name":"THRESHOLD_A","value":1}],
         "rules":[{"ruleId":"R1","enabled":true,"priority":100,"condition":{"left":{"feature":"x"},"operator":"GTE","right":{"parameter":"THRESHOLD_A"}},"outcome":{}}],
         "governance":{"makerCheckerRequired":true,"approvedBy":null,"approvedAt":null,"simulationRunId":null}}
        """;
    }
}