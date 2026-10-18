package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class PolicyNegativeFixtureTest {
  private final ObjectMapper m=new ObjectMapper();

  @Test void thresholdOrderFixtureFailsWithPsv006() throws Exception {
    Path root=repoRoot();
    JsonNode base=read(root,"policy-packs/phase1/policies/high-utilization-v1.json").deepCopy();
    JsonNode fixture=read(root,"tests/fixtures/policies/validation/invalid-high-utilization-threshold-order-v1.json");
    ObjectNode policy=(ObjectNode)base;
    for(JsonNode p:policy.path("parameters")){
      String name=p.path("name").asText();
      if(fixture.path("parameterOverrides").has(name))
        ((ObjectNode)p).set("value",fixture.path("parameterOverrides").get(name));
    }
    var validator=publicationValidator(root);
    var result=validator.validate(policy,read(root,"policy-packs/phase1/constraints/phase1-semantic-constraints-v1.json"),registry(root),List.of());
    assertTrue(result.schemaValid());
    assertFalse(result.publishable());
    assertTrue(result.findings().stream().anyMatch(f->"PSV-006".equals(f.ruleId())&&"PARAMETER_RELATION_VIOLATION".equals(f.code())));
  }

  @Test void overlappingVersionFixtureFailsWithPsv010() throws Exception {
    Path root=repoRoot();
    JsonNode fixture=read(root,"tests/fixtures/policies/validation/invalid-policy-version-overlap-v1.json");
    ObjectNode policy=(ObjectNode)read(root,"policy-packs/phase1/policies/high-utilization-v1.json").deepCopy();
    JsonNode c=fixture.path("candidate");
    policy.put("policyId","POL-CORP-HIGH-UTILIZATION-002"); policy.put("version",2);
    ObjectNode scope=(ObjectNode)policy.path("scope");
    scope.set("entityTypes",c.path("entityTypes")); scope.set("productTypes",c.path("productTypes"));
    ObjectNode ep=(ObjectNode)policy.path("effectivePeriod"); ep.put("effectiveFrom",c.path("effectiveFrom").asText()); ep.putNull("effectiveTo");

    JsonNode e=fixture.path("existing");
    var existing=new PolicyGovernanceValidator.PolicyVersion(
      e.path("policyId").asText(),c.path("policyKey").asText(),e.path("version").asInt(),e.path("institutionId").asText(),
      textSet(e.path("entityTypes")),Set.of(),textSet(e.path("productTypes")),
      Instant.parse(e.path("effectiveFrom").asText()),null,e.path("lifecycle").asText());

    var result=publicationValidator(root).validate(policy,read(root,"policy-packs/phase1/constraints/phase1-semantic-constraints-v1.json"),registry(root),List.of(existing));
    assertTrue(result.schemaValid());
    assertFalse(result.publishable());
    assertTrue(result.findings().stream().anyMatch(f->"PSV-010".equals(f.ruleId())&&"EFFECTIVE_VERSION_OVERLAP".equals(f.code())));
  }

  private PolicyPublicationValidator publicationValidator(Path root)throws Exception{return new PolicyPublicationValidator(read(root,"schemas/policies/risk-policy-v1.schema.json"));}
  private GovernedRegistry registry(Path root)throws Exception{return GovernedRegistry.from(read(root,"registries/phase1-feature-registry-v1.json"),read(root,"registries/phase1-signal-registry-v1.json"),read(root,"registries/risk-dimensions-v1.json"));}
  private JsonNode read(Path root,String p)throws Exception{return m.readTree(Files.readString(root.resolve(p)));}
  private Set<String> textSet(JsonNode n){Set<String>s=new HashSet<>();for(JsonNode x:n)s.add(x.asText());return s;}
  private Path repoRoot(){Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.exists(p.resolve("policy-packs")))p=p.getParent();if(p==null)throw new IllegalStateException("Repository root not found");return p;}
}