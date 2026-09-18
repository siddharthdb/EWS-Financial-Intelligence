package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Single authoritative pre-publication gate: schema + semantics + governance. */
public final class PolicyPublicationValidator {
  public record Result(boolean schemaValid, boolean semanticValid, boolean publishable,
                       String policyArtifactHash, Map<String,String> registrySnapshot,
                       List<PolicySemanticValidator.Finding> findings) {}

  private final JsonSchema schema;
  private final PolicySemanticValidator semantic=new PolicySemanticValidator();
  private final PolicyGovernanceValidator governance=new PolicyGovernanceValidator();

  public PolicyPublicationValidator(JsonNode schemaNode) {
    this.schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
  }

  public Result validate(JsonNode policy,JsonNode constraints,GovernedRegistry registry,
                         Collection<PolicyGovernanceValidator.PolicyVersion> existingVersions) {
    return validate(policy,policy.toString().getBytes(StandardCharsets.UTF_8),constraints,registry,existingVersions);
  }

  public Result validate(JsonNode policy,byte[] artifactBytes,JsonNode constraints,GovernedRegistry registry,
                         Collection<PolicyGovernanceValidator.PolicyVersion> existingVersions) {
    List<PolicySemanticValidator.Finding> findings=new ArrayList<>();
    Set<ValidationMessage> schemaErrors=schema.validate(policy);
    for(ValidationMessage m:schemaErrors)
      findings.add(new PolicySemanticValidator.Finding("SCHEMA",PolicySemanticValidator.Severity.ERROR,
        "JSON_SCHEMA_VIOLATION",m.getMessage(),m.getInstanceLocation().toString()));

    boolean schemaValid=schemaErrors.isEmpty();
    if(schemaValid){
      findings.addAll(semantic.validate(policy,registry));
      findings.addAll(governance.validate(policy,constraints,registry,existingVersions));
    }
    boolean semanticValid=schemaValid&&findings.stream().noneMatch(f->f.severity()==PolicySemanticValidator.Severity.ERROR);
    Map<String,String> snapshot=registry==null?Map.of():Map.of(
      "features",registry.featureRegistryVersion(),
      "signals",registry.signalRegistryVersion(),
      "riskDimensions",registry.riskDimensionRegistryVersion());
    return new Result(schemaValid,semanticValid,semanticValid,sha256(artifactBytes),snapshot,List.copyOf(findings));
  }

  private String sha256(byte[] input) {
    try {
      byte[] digest=MessageDigest.getInstance("SHA-256").digest(input);
      return HexFormat.of().formatHex(digest);
    } catch(Exception e) {
      throw new IllegalStateException(e);
    }
  }
}