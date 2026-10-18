package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PolicyGovernanceValidatorTest {
 private final ObjectMapper m=new ObjectMapper(); private final PolicyGovernanceValidator v=new PolicyGovernanceValidator();
 @Test void rejectsParameterAboveGuardrail()throws Exception{assertHas(policy(1.10,0.95),constraints(),List.of(),"PSV-005");}
 @Test void rejectsInvertedThresholds()throws Exception{assertHas(policy(0.97,0.95),constraints(),List.of(),"PSV-006");}
 @Test void rejectsOverlappingActiveVersion()throws Exception{var old=new PolicyGovernanceValidator.PolicyVersion("OLD","HIGH_UTILIZATION",1,"BANK_A",Set.of("FACILITY"),Set.of(),Set.of("REVOLVING_CREDIT"),Instant.parse("2026-01-01T00:00:00Z"),null,"ACTIVE");assertHas(policy(0.85,0.95),constraints(),List.of(old),"PSV-010");}
 @Test void warnsWhenProductSpecificFeatureHasNoProductScope()throws Exception{var r=registry();var findings=v.validate(m.readTree(policyNoProduct()),m.readTree(constraints()),r,List.of());assertTrue(findings.stream().anyMatch(f->f.ruleId().equals("PSV-014")&&f.severity()==PolicySemanticValidator.Severity.WARNING));}
 @Test void coherentConfigurationPassesHardChecks()throws Exception{assertTrue(v.validate(m.readTree(policy(0.85,0.95)),m.readTree(constraints()),registry(),List.of()).stream().noneMatch(f->f.severity()==PolicySemanticValidator.Severity.ERROR));}
 private void assertHas(String p,String c,List<PolicyGovernanceValidator.PolicyVersion>x,String id)throws Exception{assertTrue(v.validate(m.readTree(p),m.readTree(c),registry(),x).stream().anyMatch(f->f.ruleId().equals(id)));}
 private GovernedRegistry registry(){return new GovernedRegistry("1",Map.of("utilization_ratio",new GovernedRegistry.FeatureRef("utilization_ratio","GLOBAL_PRODUCT_SPECIFIC","FACILITY","DECIMAL",null)),"1",Map.of(),"1",Set.of());}
 private String constraints(){return "{\"policyKey\":\"HIGH_UTILIZATION\",\"constraints\":[{\"constraintId\":\"C1\",\"type\":\"PARAMETER_RELATION\",\"leftParameter\":\"HIGH_UTILIZATION_THRESHOLD\",\"operator\":\"LT\",\"rightParameter\":\"CRITICAL_UTILIZATION_THRESHOLD\",\"severity\":\"ERROR\",\"message\":\"high must be below critical\"}]}";}
 private String policy(double high,double critical){return base(high,critical,"[\"REVOLVING_CREDIT\"]");} private String policyNoProduct(){return base(.85,.95,"[]");}
 private String base(double high,double critical,String products){return "{\"policyId\":\"P2\",\"policyKey\":\"HIGH_UTILIZATION\",\"version\":2,\"lifecycle\":\"VALIDATION\",\"scope\":{\"institutionId\":\"BANK_A\",\"entityTypes\":[\"FACILITY\"],\"jurisdictions\":[],\"productTypes\":"+products+"},\"effectivePeriod\":{\"effectiveFrom\":\"2026-09-17T00:00:00Z\",\"effectiveTo\":null},\"parameters\":[{\"name\":\"HIGH_UTILIZATION_THRESHOLD\",\"type\":\"DECIMAL\",\"value\":"+high+",\"allowedRange\":{\"minimum\":0,\"maximum\":1},\"allowedValues\":null},{\"name\":\"CRITICAL_UTILIZATION_THRESHOLD\",\"type\":\"DECIMAL\",\"value\":"+critical+",\"allowedRange\":{\"minimum\":0,\"maximum\":1},\"allowedValues\":null}],\"rules\":[{\"condition\":{\"left\":{\"feature\":\"utilization_ratio\"}}}]}";}
}