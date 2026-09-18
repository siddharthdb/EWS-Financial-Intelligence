package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Authoritative pre-publication gate for governed correlation policies. */
public final class CorrelationPolicyPublicationValidator {
  public record PolicyVersion(String policyId,String policyKey,String version,String institutionId,Set<String> entityTypes,Set<String> jurisdictions,Set<String> productTypes,Instant from,Instant to,String lifecycle){}
  public record Result(boolean schemaValid,boolean semanticValid,boolean publishable,String policyArtifactHash,List<RiskIntelligenceSemanticValidator.Finding> findings){}

  private final JsonSchemaGate schema;
  private final RiskIntelligenceSemanticValidator semantic=new RiskIntelligenceSemanticValidator();

  public CorrelationPolicyPublicationValidator(JsonNode schemaNode){this.schema=new JsonSchemaGate(schemaNode);}

  public Result validate(JsonNode policy,byte[] artifactBytes,GovernedRegistry registry,JsonNode hypothesisRegistry,Collection<PolicyVersion> existing){
    List<RiskIntelligenceSemanticValidator.Finding> findings=new ArrayList<>();
    Set<ValidationMessage> errors=schema.validate(policy);
    for(ValidationMessage m:errors) findings.add(new RiskIntelligenceSemanticValidator.Finding("CPS-000","JSON_SCHEMA_VIOLATION",m.getMessage(),m.getPath()));
    boolean schemaValid=errors.isEmpty();
    if(schemaValid){
      findings.addAll(semantic.validateCorrelationPolicy(policy,registry,hypothesisRegistry));
      findings.addAll(validateVersionOverlap(policy,existing));
    }
    boolean semanticValid=schemaValid&&findings.isEmpty();
    return new Result(schemaValid,semanticValid,semanticValid,sha256(artifactBytes),List.copyOf(findings));
  }

  private List<RiskIntelligenceSemanticValidator.Finding> validateVersionOverlap(JsonNode p,Collection<PolicyVersion> existing){
    List<RiskIntelligenceSemanticValidator.Finding> out=new ArrayList<>();
    try{
      String id=p.path("policyId").asText(),key=p.path("policyKey").asText(),version=p.path("version").asText(),institution=p.path("scope").path("institutionId").asText();
      Set<String> entities=textSet(p.path("scope").path("entityTypes")),jurisdictions=textSet(p.path("scope").path("jurisdictions")),products=textSet(p.path("scope").path("productTypes"));
      Instant from=Instant.parse(p.path("effectivePeriod").path("effectiveFrom").asText());
      Instant to=p.path("effectivePeriod").path("effectiveTo").isNull()?null:Instant.parse(p.path("effectivePeriod").path("effectiveTo").asText());
      for(PolicyVersion e:existing){
        if(id.equals(e.policyId())&&version.equals(e.version()))continue;
        if(!key.equals(e.policyKey())||!institution.equals(e.institutionId())||!Set.of("APPROVED","ACTIVE").contains(e.lifecycle()))continue;
        if(scopeIntersects(entities,e.entityTypes())&&scopeIntersects(jurisdictions,e.jurisdictions())&&scopeIntersects(products,e.productTypes())&&overlaps(from,to,e.from(),e.to()))
          out.add(new RiskIntelligenceSemanticValidator.Finding("CPV-010","EFFECTIVE_VERSION_OVERLAP","Correlation policy effective scope overlaps approved/active version "+e.policyId()+" v"+e.version(),"/effectivePeriod"));
      }
    }catch(Exception ex){
      out.add(new RiskIntelligenceSemanticValidator.Finding("CPV-010","INVALID_EFFECTIVE_PERIOD","Correlation policy effective period could not be evaluated","/effectivePeriod"));
    }
    return out;
  }
  private boolean scopeIntersects(Set<String>a,Set<String>b){if(a.isEmpty()||b.isEmpty())return true;for(String x:a)if(b.contains(x))return true;return false;}
  private boolean overlaps(Instant a1,Instant a2,Instant b1,Instant b2){return (a2==null||b1.isBefore(a2))&&(b2==null||a1.isBefore(b2));}
  private Set<String> textSet(JsonNode n){Set<String>s=new HashSet<>();for(JsonNode x:n)s.add(x.asText());return s;}
  private String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
}
