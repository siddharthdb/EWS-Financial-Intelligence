package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

final class RiskIntelligenceContractTest {
  private final ObjectMapper m=new ObjectMapper();
  private final RiskIntelligenceSemanticValidator semantic=new RiskIntelligenceSemanticValidator();

  @Test void phase1EpisodeIsSchemaAndSemanticallyValid() throws Exception {
    Path root=repoRoot(); JsonNode value=read(root,"tests/fixtures/risk-intelligence/phase1-signal-episode-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/signal-episode-v1.schema.json")).requireValid(value,"Phase-1 signal episode");
    assertTrue(semantic.validateEpisode(value).isEmpty(),semantic.validateEpisode(value).toString());
  }

  @Test void phase1LiquidityCorrelationIsSchemaAndSemanticallyValid() throws Exception {
    Path root=repoRoot(); JsonNode value=read(root,"tests/fixtures/risk-intelligence/phase1-liquidity-correlation-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/correlation-hypothesis-v1.schema.json")).requireValid(value,"Phase-1 liquidity correlation");
    assertTrue(semantic.validateCorrelation(value).isEmpty(),semantic.validateCorrelation(value).toString());
  }

  @Test void phase1CorrelationPolicyIsGovernedAndValid() throws Exception {
    Path root=repoRoot(); JsonNode policy=read(root,"policy-packs/phase1/correlations/emerging-liquidity-stress-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/correlation-policy-v1.schema.json")).requireValid(policy,"Phase-1 correlation policy");
    assertTrue(semantic.validateCorrelationPolicy(policy,registry(root),read(root,"registries/phase1-correlation-hypothesis-registry-v1.json")).isEmpty(),
      semantic.validateCorrelationPolicy(policy,registry(root),read(root,"registries/phase1-correlation-hypothesis-registry-v1.json")).toString());
  }

  @Test void sharedLineageCannotMasqueradeAsIndependentCorroboration() throws Exception {
    Path root=repoRoot(); JsonNode value=read(root,"tests/fixtures/risk-intelligence/validation/invalid-shared-lineage-corroboration-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/correlation-hypothesis-v1.schema.json")).requireValid(value,"shared-lineage negative fixture");
    var findings=semantic.validateCorrelation(value);
    assertTrue(findings.stream().anyMatch(f->"ECV-007".equals(f.ruleId())));
    assertTrue(findings.stream().anyMatch(f->"ECV-008".equals(f.ruleId())));
  }

  @Test void sameContributorFamilyCannotMasqueradeAsIndependentCorroboration() throws Exception {
    Path root=repoRoot(); JsonNode value=read(root,"tests/fixtures/risk-intelligence/validation/invalid-same-family-corroboration-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/correlation-hypothesis-v1.schema.json")).requireValid(value,"same-family negative fixture");
    var findings=semantic.validateCorrelation(value);
    assertTrue(findings.stream().anyMatch(f->"ECV-007".equals(f.ruleId())));
    assertTrue(findings.stream().anyMatch(f->"ECV-008".equals(f.ruleId())));
  }

  @Test void institutionCorrelationOverrideCannotExceedPlatformGuardrails() throws Exception {
    Path root=repoRoot(); JsonNode policy=read(root,"tests/fixtures/risk-intelligence/validation/invalid-correlation-policy-override-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/correlation-policy-v1.schema.json")).requireValid(policy,"invalid override fixture must remain structurally valid");
    var findings=semantic.validateCorrelationPolicy(policy,registry(root),read(root,"registries/phase1-correlation-hypothesis-registry-v1.json"));
    assertTrue(findings.stream().anyMatch(f->"CPV-005".equals(f.ruleId())));
    assertTrue(findings.stream().anyMatch(f->"CPV-006".equals(f.ruleId())));
    assertTrue(findings.stream().anyMatch(f->"CPV-007".equals(f.ruleId())));
  }

  @Test void nonLiveEpisodeRequiresRunId() throws Exception {
    Path root=repoRoot(); JsonNode value=read(root,"tests/fixtures/risk-intelligence/phase1-signal-episode-v1.json").deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode)value).put("executionMode","COUNTERFACTUAL_BACKTEST");
    ((com.fasterxml.jackson.databind.node.ObjectNode)value).put("episodeKey","COUNTERFACTUAL_BACKTEST|EP-POL-HIGH-UTILIZATION|1|FACILITY|FAC-1001|UTILIZATION_HIGH|FAC-1001");
    assertTrue(semantic.validateEpisode(value).stream().anyMatch(f->"ECV-009".equals(f.ruleId())));
  }

  private GovernedRegistry registry(Path root)throws Exception{return GovernedRegistry.from(read(root,"registries/phase1-feature-registry-v1.json"),read(root,"registries/phase1-signal-registry-v1.json"),read(root,"registries/risk-dimensions-v1.json"));}
  private JsonNode read(Path root,String p)throws Exception{return m.readTree(Files.readString(root.resolve(p)));}
  private Path repoRoot(){Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.exists(p.resolve("schemas")))p=p.getParent();if(p==null)throw new IllegalStateException("Repository root not found");return p;}
}
