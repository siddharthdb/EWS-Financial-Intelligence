package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class RiskAssessmentContractTest {
  private final ObjectMapper m=new ObjectMapper();
  private final RiskIntelligenceSemanticValidator semantic=new RiskIntelligenceSemanticValidator();

  @Test void phase1AggregationPolicyIsPublishable() throws Exception {
    Path root=repoRoot();Path p=root.resolve("policy-packs/phase1/assessments/corporate-risk-assessment-v1.json");byte[] bytes=Files.readAllBytes(p);JsonNode policy=m.readTree(bytes);
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/risk-assessment-aggregation-policy-v1.schema.json")).requireValid(policy,"Phase-1 aggregation policy");
    var result=publisher(root).validate(policy,bytes,registry(root),hypotheses(root),List.of());
    assertTrue(result.publishable(),result.findings().toString());
  }

  @Test void phase1ProposedAssessmentIsValidAndBoundToExactPolicy() throws Exception {
    Path root=repoRoot();Path p=root.resolve("policy-packs/phase1/assessments/corporate-risk-assessment-v1.json");byte[] bytes=Files.readAllBytes(p);JsonNode policy=m.readTree(bytes);
    String hash=publisher(root).validate(policy,bytes,registry(root),hypotheses(root),List.of()).policyArtifactHash();
    JsonNode assessment=read(root,"tests/fixtures/risk-intelligence/phase1-proposed-risk-assessment-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/proposed-risk-assessment-v1.schema.json")).requireValid(assessment,"Phase-1 proposed assessment");
    assertTrue(semantic.validateRiskAssessment(assessment,policy,registry(root),hash).isEmpty(),semantic.validateRiskAssessment(assessment,policy,registry(root),hash).toString());
  }

  @Test void correlationSubstitutionPreventsUnderlyingFamilyDoubleCount() throws Exception {
    Path root=repoRoot();Path p=root.resolve("policy-packs/phase1/assessments/corporate-risk-assessment-v1.json");byte[] bytes=Files.readAllBytes(p);JsonNode policy=m.readTree(bytes);
    String hash=publisher(root).validate(policy,bytes,registry(root),hypotheses(root),List.of()).policyArtifactHash();
    JsonNode assessment=read(root,"tests/fixtures/risk-intelligence/validation/invalid-risk-assessment-double-count-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/proposed-risk-assessment-v1.schema.json")).requireValid(assessment,"double-count negative fixture");
    assertTrue(semantic.validateRiskAssessment(assessment,policy,registry(root),hash).stream().anyMatch(f->"RAV-006".equals(f.ruleId())));
  }

  @Test void weightedDimensionsMustSumToOne() throws Exception {
    Path root=repoRoot();JsonNode p=read(root,"tests/fixtures/risk-intelligence/validation/invalid-risk-aggregation-weights-v1.json");
    new JsonSchemaGate(read(root,"schemas/risk-intelligence/risk-assessment-aggregation-policy-v1.schema.json")).requireValid(p,"invalid weights fixture must remain structurally valid");
    assertTrue(semantic.validateAggregationPolicy(p,registry(root),hypotheses(root)).stream().anyMatch(f->"RAP-008".equals(f.ruleId())));
  }

  @Test void overlappingActiveAggregationVersionIsRejected() throws Exception {
    Path root=repoRoot();Path p=root.resolve("policy-packs/phase1/assessments/corporate-risk-assessment-v1.json");byte[] bytes=Files.readAllBytes(p);JsonNode policy=m.readTree(bytes);
    var existing=new RiskAggregationPolicyPublicationValidator.PolicyVersion("RISK-AGG-OLD","CORPORATE_RISK_ASSESSMENT","0","REFERENCE_INSTITUTION",Set.of("COUNTERPARTY"),Set.of(),Set.of(),Instant.parse("2026-01-01T00:00:00Z"),null,"ACTIVE");
    var result=publisher(root).validate(policy,bytes,registry(root),hypotheses(root),List.of(existing));
    assertFalse(result.publishable());assertTrue(result.findings().stream().anyMatch(f->"RAP-014".equals(f.ruleId())));
  }

  @Test void scoreCannotClaimAWeakerBandThanPolicyThreshold() throws Exception {
    Path root=repoRoot();Path p=root.resolve("policy-packs/phase1/assessments/corporate-risk-assessment-v1.json");byte[] bytes=Files.readAllBytes(p);JsonNode policy=m.readTree(bytes);
    String hash=publisher(root).validate(policy,bytes,registry(root),hypotheses(root),List.of()).policyArtifactHash();
    com.fasterxml.jackson.databind.node.ObjectNode assessment=(com.fasterxml.jackson.databind.node.ObjectNode)read(root,"tests/fixtures/risk-intelligence/phase1-proposed-risk-assessment-v1.json").deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode)assessment.path("dimensionAssessments").get(0)).put("proposedBand","HIGH");
    assertTrue(semantic.validateRiskAssessment(assessment,policy,registry(root),hash).stream().anyMatch(f->"RAV-012".equals(f.ruleId())));
  }

  @Test void highOrCriticalAssessmentCannotBypassHumanReview() throws Exception {
    Path root=repoRoot();Path p=root.resolve("policy-packs/phase1/assessments/corporate-risk-assessment-v1.json");byte[] bytes=Files.readAllBytes(p);JsonNode policy=m.readTree(bytes);
    String hash=publisher(root).validate(policy,bytes,registry(root),hypotheses(root),List.of()).policyArtifactHash();
    com.fasterxml.jackson.databind.node.ObjectNode assessment=(com.fasterxml.jackson.databind.node.ObjectNode)read(root,"tests/fixtures/risk-intelligence/phase1-proposed-risk-assessment-v1.json").deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode)assessment.path("overallAssessment")).put("requiresHumanReview",false);
    assertTrue(semantic.validateRiskAssessment(assessment,policy,registry(root),hash).stream().anyMatch(f->"RAV-014".equals(f.ruleId())));
  }

  @Test void nonLiveAssessmentRequiresRunId() throws Exception {
    Path root=repoRoot();JsonNode a=read(root,"tests/fixtures/risk-intelligence/phase1-proposed-risk-assessment-v1.json").deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode)a).put("executionMode","COUNTERFACTUAL_BACKTEST");
    var errors=new JsonSchemaGate(read(root,"schemas/risk-intelligence/proposed-risk-assessment-v1.schema.json")).validate(a);
    assertFalse(errors.isEmpty());
  }

  private RiskAggregationPolicyPublicationValidator publisher(Path root)throws Exception{return new RiskAggregationPolicyPublicationValidator(read(root,"schemas/risk-intelligence/risk-assessment-aggregation-policy-v1.schema.json"));}
  private GovernedRegistry registry(Path root)throws Exception{return GovernedRegistry.from(read(root,"registries/phase1-feature-registry-v1.json"),read(root,"registries/phase1-signal-registry-v1.json"),read(root,"registries/risk-dimensions-v1.json"));}
  private JsonNode hypotheses(Path root)throws Exception{return read(root,"registries/phase1-correlation-hypothesis-registry-v1.json");}
  private JsonNode read(Path root,String p)throws Exception{return m.readTree(Files.readString(root.resolve(p)));}
  private Path repoRoot(){Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.exists(p.resolve("schemas")))p=p.getParent();if(p==null)throw new IllegalStateException("Repository root not found");return p;}
}
