package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class CorrelationPolicyPublicationValidatorTest {
  private final ObjectMapper m=new ObjectMapper();

  @Test void phase1CorrelationPolicyIsPublishable() throws Exception {
    Path root=repoRoot(); Path p=root.resolve("policy-packs/phase1/correlations/emerging-liquidity-stress-v1.json");
    byte[] bytes=Files.readAllBytes(p); JsonNode policy=m.readTree(bytes);
    var result=validator(root).validate(policy,bytes,registry(root),read(root,"registries/phase1-correlation-hypothesis-registry-v1.json"),List.of());
    assertTrue(result.publishable(),result.findings().toString());
    assertEquals(64,result.policyArtifactHash().length());
  }

  @Test void overlappingApprovedVersionIsNotPublishable() throws Exception {
    Path root=repoRoot(); Path p=root.resolve("policy-packs/phase1/correlations/emerging-liquidity-stress-v1.json");
    byte[] bytes=Files.readAllBytes(p); JsonNode policy=m.readTree(bytes);
    var existing=new CorrelationPolicyPublicationValidator.PolicyVersion("CORR-OLD","EMERGING_LIQUIDITY_STRESS","0","REFERENCE_INSTITUTION",
      Set.of("COUNTERPARTY"),Set.of(),Set.of(),Instant.parse("2026-01-01T00:00:00Z"),null,"ACTIVE");
    var result=validator(root).validate(policy,bytes,registry(root),read(root,"registries/phase1-correlation-hypothesis-registry-v1.json"),List.of(existing));
    assertFalse(result.publishable());
    assertTrue(result.findings().stream().anyMatch(f->"CPV-010".equals(f.ruleId())));
  }

  private CorrelationPolicyPublicationValidator validator(Path root)throws Exception{return new CorrelationPolicyPublicationValidator(read(root,"schemas/risk-intelligence/correlation-policy-v1.schema.json"));}
  private GovernedRegistry registry(Path root)throws Exception{return GovernedRegistry.from(read(root,"registries/phase1-feature-registry-v1.json"),read(root,"registries/phase1-signal-registry-v1.json"),read(root,"registries/risk-dimensions-v1.json"));}
  private JsonNode read(Path root,String p)throws Exception{return m.readTree(Files.readString(root.resolve(p)));}
  private Path repoRoot(){Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.exists(p.resolve("schemas")))p=p.getParent();if(p==null)throw new IllegalStateException("Repository root not found");return p;}
}
