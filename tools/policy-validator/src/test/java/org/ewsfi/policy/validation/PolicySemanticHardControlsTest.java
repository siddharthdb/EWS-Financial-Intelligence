package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolicySemanticHardControlsTest {
  private final ObjectMapper m=new ObjectMapper();
  private final PolicySemanticValidator v=new PolicySemanticValidator();

  @Test void firstMatchRequiresUniquePriority() throws Exception {
    var p=m.readTree(policy("FIRST_MATCH","2026-09-17T00:00:00Z","SIGNAL_CANDIDATE","INSTITUTION_POLICY_SPECIFIC","[]",true));
    assertTrue(v.validate(p).stream().anyMatch(f->f.ruleId().equals("PSV-008")));
  }

  @Test void invalidEffectiveFromIsRejected() throws Exception {
    var p=m.readTree(policy("ALL_MATCHES","not-a-time","SIGNAL_CANDIDATE","INSTITUTION_POLICY_SPECIFIC","[]",false));
    assertTrue(v.validate(p).stream().anyMatch(f->f.ruleId().equals("PSV-009")));
  }

  @Test void analyticalPolicyCannotEmitClassificationCandidate() throws Exception {
    var p=m.readTree(policy("ALL_MATCHES","2026-09-17T00:00:00Z","CLASSIFICATION_CANDIDATE","INSTITUTION_POLICY_SPECIFIC","[]",false));
    assertTrue(v.validate(p).stream().anyMatch(f->f.ruleId().equals("PSV-013")));
  }

  @Test void jurisdictionExtensionCanEmitClassificationCandidate() throws Exception {
    var p=m.readTree(policy("ALL_MATCHES","2026-09-17T00:00:00Z","CLASSIFICATION_CANDIDATE","JURISDICTION_EXTENSION","[\"GB\"]",false));
    assertTrue(v.validate(p).stream().noneMatch(f->f.ruleId().equals("PSV-013")));
  }

  private String policy(String mode,String from,String outcome,String scope,String jurisdictions,boolean duplicatePriority){
    String second=duplicatePriority?",{\"ruleId\":\"R2\",\"enabled\":true,\"priority\":100,\"condition\":{\"left\":{\"feature\":\"x\"},\"operator\":\"GTE\",\"right\":{\"parameter\":\"T\"}},\"outcome\":{\"type\":\"SIGNAL_CANDIDATE\",\"code\":\"X\"}}":"";
    return "{\"policyId\":\"P\",\"policyKey\":\"TEST\",\"version\":1,\"lifecycle\":\"VALIDATION\",\"scope\":{\"semanticScope\":\""+scope+"\",\"jurisdictions\":"+jurisdictions+"},\"effectivePeriod\":{\"effectiveFrom\":\""+from+"\",\"effectiveTo\":null},\"evaluation\":{\"engine\":\"NATIVE\",\"mode\":\""+mode+"\"},\"parameters\":[{\"name\":\"T\",\"value\":1}],\"rules\":[{\"ruleId\":\"R1\",\"enabled\":true,\"priority\":100,\"condition\":{\"left\":{\"feature\":\"x\"},\"operator\":\"GTE\",\"right\":{\"parameter\":\"T\"}},\"outcome\":{\"type\":\""+outcome+"\",\"code\":\"X\"}}"+second+"],\"governance\":{\"makerCheckerRequired\":false}}";
  }
}