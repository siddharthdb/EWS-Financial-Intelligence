package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class RiskIntelligenceContractTest {
  private final ObjectMapper m=new ObjectMapper();
  private final RiskIntelligenceSemanticValidator semantic=new RiskIntelligenceSemanticValidator();

  @Test void phase1EpisodeIsSchemaAndSemanticallyValid() throws Exception {
    Path root=repoRoot();
    JsonNode value=read(root,"tests/fixtures/risk-intelligence/phase1-signal-episode-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/signal-episode-v1.schema.json")).requireValid(value,"Phase-1 signal episode");
    assertTrue(semantic.validateEpisode(value).isEmpty(),semantic.validateEpisode(value).toString());
  }

  @Test void phase1LiquidityCorrelationIsSchemaAndSemanticallyValid() throws Exception {
    Path root=repoRoot();
    JsonNode value=read(root,"tests/fixtures/risk-intelligence/phase1-liquidity-correlation-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/correlation-hypothesis-v1.schema.json")).requireValid(value,"Phase-1 liquidity correlation");
    assertTrue(semantic.validateCorrelation(value).isEmpty(),semantic.validateCorrelation(value).toString());
  }

  @Test void sharedLineageCannotMasqueradeAsIndependentCorroboration() throws Exception {
    Path root=repoRoot();
    JsonNode value=read(root,"tests/fixtures/risk-intelligence/validation/invalid-shared-lineage-corroboration-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/correlation-hypothesis-v1.schema.json")).requireValid(value,"shared-lineage negative fixture");
    var findings=semantic.validateCorrelation(value);
    assertTrue(findings.stream().anyMatch(f->"ECV-007".equals(f.ruleId())&&"INDEPENDENCE_COUNT_MISMATCH".equals(f.code())));
    assertTrue(findings.stream().anyMatch(f->"ECV-008".equals(f.ruleId())&&"INSUFFICIENT_INDEPENDENT_CONTRIBUTORS".equals(f.code())));
  }

  @Test void nonLiveEpisodeRequiresRunId() throws Exception {
    Path root=repoRoot();
    JsonNode value=read(root,"tests/fixtures/risk-intelligence/phase1-signal-episode-v1.json").deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode)value).put("executionMode","COUNTERFACTUAL_BACKTEST");
    ((com.fasterxml.jackson.databind.node.ObjectNode)value).put("episodeKey","COUNTERFACTUAL_BACKTEST|EP-POL-HIGH-UTILIZATION|1|FACILITY|FAC-1001|HIGH_UTILIZATION|FAC-1001");
    var findings=semantic.validateEpisode(value);
    assertTrue(findings.stream().anyMatch(f->"ECV-009".equals(f.ruleId())));
  }

  private JsonNode read(Path root,String p)throws Exception{return m.readTree(Files.readString(root.resolve(p)));}
  private Path repoRoot(){Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.exists(p.resolve("schemas")))p=p.getParent();if(p==null)throw new IllegalStateException("Repository root not found");return p;}
}
