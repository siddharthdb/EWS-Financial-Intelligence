package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end contract tests against repository artifacts, not synthetic policy strings. */
class RepositoryPolicyPublicationTest {
  private final ObjectMapper m=new ObjectMapper();
  private final Path root=Path.of("../..").normalize();
  private PolicyPublicationValidator validator; private GovernedRegistry registry; private JsonNode constraints;

  @BeforeEach void setup() throws Exception {
    validator=new PolicyPublicationValidator(read("schemas/policies/risk-policy-v1.schema.json"));
    constraints=read("policy-packs/phase1/constraints/phase1-semantic-constraints-v1.json");
    registry=GovernedRegistry.from(read("registries/phase1-feature-registry-v1.json"),
      read("registries/phase1-signal-registry-v1.json"),read("registries/risk-dimensions-v1.json"));
  }

  @Test void allPhase1PoliciesPassPublicationGate() throws Exception {
    for(String name:List.of("repeated-payment-return-v1.json","dpd-deterioration-v1.json","high-utilization-v1.json")){
      var r=validator.validate(read("policy-packs/phase1/policies/"+name),constraints,registry,List.of());
      assertTrue(r.schemaValid(),name+" schema findings="+r.findings());
      assertTrue(r.publishable(),name+" publication findings="+r.findings());
    }
  }

  @Test void invalidThresholdOrderingFixtureFailsPsv006() throws Exception {
    JsonNode fixture=read("tests/fixtures/policies/validation/invalid-high-utilization-threshold-order-v1.json");
    ObjectNode policy=(ObjectNode)read("policy-packs/phase1/policies/high-utilization-v1.json").deepCopy();
    fixture.path("parameterOverrides").fields().forEachRemaining(e->{
      for(JsonNode p:policy.withArray("parameters")) if(p.path("name").asText().equals(e.getKey())) ((ObjectNode)p).set("value",e.getValue());
    });
    var r=validator.validate(policy,constraints,registry,List.of());
    assertTrue(r.schemaValid());
    assertFalse(r.publishable());
    assertTrue(r.findings().stream().anyMatch(f->f.ruleId().equals("PSV-006")&&f.code().equals("PARAMETER_RELATION_VIOLATION")));
  }

  @Test void overlappingVersionFixtureFailsPsv010() throws Exception {
    JsonNode fixture=read("tests/fixtures/policies/validation/invalid-policy-version-overlap-v1.json");
    ObjectNode policy=(ObjectNode)read("policy-packs/phase1/policies/high-utilization-v1.json").deepCopy();
    JsonNode candidate=fixture.path("candidate");
    policy.put("version",2); policy.with("effectivePeriod").put("effectiveFrom",candidate.path("effectiveFrom").asText());
    ArrayNode products=policy.with("scope").putArray("productTypes"); candidate.path("productTypes").forEach(products::add);
    JsonNode old=fixture.path("existing");
    var existing=new PolicyGovernanceValidator.PolicyVersion(old.path("policyId").asText(),candidate.path("policyKey").asText(),
      old.path("version").asInt(),old.path("institutionId").asText(),set(old.path("entityTypes")),Set.of(),set(old.path("productTypes")),
      Instant.parse(old.path("effectiveFrom").asText()),null,old.path("lifecycle").asText());
    var r=validator.validate(policy,constraints,registry,List.of(existing));
    assertFalse(r.publishable());
    assertTrue(r.findings().stream().anyMatch(f->f.ruleId().equals("PSV-010")&&f.code().equals("EFFECTIVE_VERSION_OVERLAP")));
  }

  private JsonNode read(String p)throws Exception{return m.readTree(Files.readString(root.resolve(p)));}
  private Set<String> set(JsonNode n){Set<String>s=new HashSet<>();n.forEach(x->s.add(x.asText()));return s;}
}