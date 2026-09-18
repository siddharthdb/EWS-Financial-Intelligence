package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Repository/CI entry point. Validates the actual Phase-1 policy artifacts. */
public final class PolicyValidatorCli {
  public static void main(String[] args) throws Exception {
    Path root=Path.of(args.length==0?".":args[0]); ObjectMapper m=new ObjectMapper();
    JsonNode schema=read(m,root,"schemas/policies/risk-policy-v1.schema.json");
    JsonNode constraints=read(m,root,"policy-packs/phase1/constraints/phase1-semantic-constraints-v1.json");
    GovernedRegistry registry=GovernedRegistry.from(
      read(m,root,"registries/phase1-feature-registry-v1.json"),
      read(m,root,"registries/phase1-signal-registry-v1.json"),
      read(m,root,"registries/risk-dimensions-v1.json"));
    PolicyPublicationValidator validator=new PolicyPublicationValidator(schema);
    Path dir=root.resolve("policy-packs/phase1/policies"); boolean failed=false;
    try(var paths=Files.list(dir)){
      for(Path p:paths.filter(x->x.toString().endsWith(".json")).sorted().toList()){
        JsonNode policy=m.readTree(Files.readString(p));
        var result=validator.validate(policy,constraints,registry,List.of());
        System.out.printf("%s schema=%s semantic=%s publishable=%s sha256=%s%n",
          root.relativize(p),result.schemaValid(),result.semanticValid(),result.publishable(),result.policyArtifactHash());
        for(var f:result.findings()) System.out.printf("  %s %s %s %s%n",f.severity(),f.ruleId(),f.code(),f.message());
        if(!result.publishable()) failed=true;
      }
    }
    if(failed) throw new IllegalStateException("One or more Phase-1 policies failed the publication gate");
  }
  private static JsonNode read(ObjectMapper m,Path root,String path)throws Exception{return m.readTree(Files.readString(root.resolve(path)));}
}