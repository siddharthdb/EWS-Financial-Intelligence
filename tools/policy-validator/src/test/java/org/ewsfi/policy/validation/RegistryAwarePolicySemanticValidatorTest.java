package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistryAwarePolicySemanticValidatorTest {
    private final ObjectMapper mapper=new ObjectMapper(); private final PolicySemanticValidator validator=new PolicySemanticValidator();
    private GovernedRegistry registry(){return new GovernedRegistry("1",Map.of("utilization_ratio",new GovernedRegistry.FeatureRef("utilization_ratio","GLOBAL_PRODUCT_SPECIFIC","FACILITY","DECIMAL",null)),"1",Map.of("HIGH_UTILIZATION",new GovernedRegistry.SignalRef("HIGH_UTILIZATION","ACTIVE","GLOBAL_PRODUCT_SPECIFIC")),"1",Set.of("LIQUIDITY","REFINANCING_FUNDING"));}

    @Test void acceptsRegisteredFeatureSignalAndDimensions() throws Exception { assertTrue(validator.validate(mapper.readTree(policy("utilization_ratio","HIGH_UTILIZATION","LIQUIDITY","FACILITY")),registry()).isEmpty()); }
    @Test void rejectsUnknownFeature() throws Exception { assertHas(policy("unknown_feature","HIGH_UTILIZATION","LIQUIDITY","FACILITY"),"PSV-002"); }
    @Test void rejectsUnknownSignal() throws Exception { assertHas(policy("utilization_ratio","UNKNOWN_SIGNAL","LIQUIDITY","FACILITY"),"PSV-003"); }
    @Test void rejectsUnknownRiskDimension() throws Exception { assertHas(policy("utilization_ratio","HIGH_UTILIZATION","MAGIC_RISK","FACILITY"),"PSV-004"); }
    @Test void rejectsEntityGrainMismatch() throws Exception { assertHas(policy("utilization_ratio","HIGH_UTILIZATION","LIQUIDITY","COUNTERPARTY"),"PSV-015"); }
    private void assertHas(String json,String id)throws Exception{assertTrue(validator.validate(mapper.readTree(json),registry()).stream().anyMatch(f->f.ruleId().equals(id)));}
    private String policy(String feature,String signal,String dimension,String entity){return "{\"policyId\":\"P\",\"policyKey\":\"P\",\"version\":1,\"lifecycle\":\"VALIDATION\",\"scope\":{\"entityTypes\":[\""+entity+"\"]},\"effectivePeriod\":{\"effectiveFrom\":\"2026-09-17T00:00:00Z\",\"effectiveTo\":null},\"evaluation\":{\"engine\":\"NATIVE\",\"mode\":\"ALL_MATCHES\"},\"parameters\":[{\"name\":\"T\",\"value\":1}],\"rules\":[{\"ruleId\":\"R\",\"enabled\":true,\"priority\":1,\"condition\":{\"left\":{\"feature\":\""+feature+"\"},\"operator\":\"GTE\",\"right\":{\"parameter\":\"T\"}},\"outcome\":{\"type\":\"SIGNAL_CANDIDATE\",\"code\":\""+signal+"\",\"riskDimensions\":[\""+dimension+"\"]}}],\"governance\":{\"makerCheckerRequired\":true,\"approvedBy\":null,\"approvedAt\":null,\"simulationRunId\":null}}";}
}