package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Repository/CI entry point. Validates actual Phase-1 policy artifacts and emits validation evidence. */
public final class PolicyValidatorCli {
  public static void main(String[] args) throws Exception {
    Path root=Path.of(args.length==0?".":args[0]);
    ObjectMapper m=new ObjectMapper();
    JsonNode schema=read(m,root,"schemas/policies/risk-policy-v1.schema.json");
    JsonNode constraints=read(m,root,"policy-packs/phase1/constraints/phase1-semantic-constraints-v1.json");
    GovernedRegistry registry=GovernedRegistry.from(
      read(m,root,"registries/phase1-feature-registry-v1.json"),
      read(m,root,"registries/phase1-signal-registry-v1.json"),
      read(m,root,"registries/risk-dimensions-v1.json"));
    PolicyPublicationValidator validator=new PolicyPublicationValidator(schema);
    JsonSchemaGate evidenceGate=new JsonSchemaGate(read(m,root,"schemas/policies/policy-validation-result-v1.schema.json"));

    Path dir=root.resolve("policy-packs/phase1/policies");
    Path outDir=root.resolve("tools/policy-validator/target/policy-validation-results");
    Files.createDirectories(outDir);
    boolean failed=false;

    try(var paths=Files.list(dir)){
      for(Path p:paths.filter(x->x.toString().endsWith(".json")).sorted().toList()){
        byte[] policyBytes=Files.readAllBytes(p);
        JsonNode policy=m.readTree(policyBytes);
        var result=validator.validate(policy,policyBytes,constraints,registry,List.of());
        ObjectNode evidence=toEvidence(m,policy,result);
        evidenceGate.requireValid(evidence,"validation evidence for "+p.getFileName());

        Path out=outDir.resolve(p.getFileName().toString().replace(".json","-validation.json"));
        Files.writeString(out,m.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        System.out.printf("%s schema=%s semantic=%s publishable=%s sha256=%s%n",
          root.relativize(p),result.schemaValid(),result.semanticValid(),result.publishable(),result.policyArtifactHash());
        for(var x:result.findings())
          System.out.printf("  %s %s %s %s%n",x.severity(),x.ruleId(),x.code(),x.message());
        if(!result.publishable()) failed=true;
      }
    }
    if(failed) throw new IllegalStateException("One or more Phase-1 policies failed the publication gate");
  }

  private static ObjectNode toEvidence(ObjectMapper m,JsonNode p,PolicyPublicationValidator.Result r){
    ObjectNode o=m.createObjectNode();
    o.put("validationId",UUID.randomUUID().toString());
    ObjectNode policy=o.putObject("policy");
    policy.put("policyId",p.path("policyId").asText());
    policy.put("version",p.path("version").asInt());
    o.put("policyArtifactHash",r.policyArtifactHash());

    ObjectNode sv=o.putObject("schemaValidation");
    sv.put("valid",r.schemaValid());
    sv.put("schemaRef","schemas/policies/risk-policy-v1.schema.json");
    ArrayNode schemaErrors=sv.putArray("errors");

    ObjectNode sm=o.putObject("semanticValidation");
    sm.put("valid",r.semanticValid());
    ObjectNode snap=sm.putObject("registrySnapshot");
    r.registrySnapshot().forEach(snap::put);
    ArrayNode semanticFindings=sm.putArray("findings");

    for(var f:r.findings()){
      ObjectNode n=m.createObjectNode();
      n.put("ruleId",f.ruleId()); n.put("severity",f.severity().name());
      n.put("code",f.code()); n.put("message",f.message());
      if(f.jsonPointer()==null)n.putNull("jsonPointer"); else n.put("jsonPointer",f.jsonPointer());
      n.putNull("reference");
      if("SCHEMA".equals(f.ruleId()))schemaErrors.add(n); else semanticFindings.add(n);
    }

    o.put("publishable",r.publishable());
    o.put("validatedAt",Instant.now().toString());
    o.put("validatorVersion","0.1.0");
    o.putNull("traceId");
    return o;
  }

  private static JsonNode read(ObjectMapper m,Path root,String path)throws Exception{
    return m.readTree(Files.readString(root.resolve(path)));
  }
}