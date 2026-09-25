package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Hard publication-gate checks that require policy/configuration context. */
public final class PolicyGovernanceValidator {
    public record PolicyVersion(String policyId,String policyKey,int version,String institutionId,Set<String> entityTypes,Set<String> jurisdictions,Set<String> products,Instant from,Instant to,String lifecycle) {}

    public List<PolicySemanticValidator.Finding> validate(JsonNode policy, JsonNode constraints, GovernedRegistry registry, Collection<PolicyVersion> existingVersions) {
        List<PolicySemanticValidator.Finding> out=new ArrayList<>();
        Map<String,JsonNode> params=new HashMap<>(); int pi=0;
        for(JsonNode p:policy.path("parameters")){params.put(p.path("name").asText(),p);validateParameter(p,out,"/parameters/"+pi++);}
        validateConstraints(policy,constraints,params,out);
        validateScope(policy,registry,out);
        validateVersionOverlap(policy,existingVersions,out);
        return List.copyOf(out);
    }

    private void validateParameter(JsonNode p,List<PolicySemanticValidator.Finding> out,String ptr){
        JsonNode v=p.path("value"); String type=p.path("type").asText(); boolean typeOk=switch(type){case "DECIMAL"->v.isNumber();case "INTEGER"->v.isIntegralNumber();case "BOOLEAN"->v.isBoolean();case "STRING","DATE","DURATION"->v.isTextual();default->false;};
        if(!typeOk)out.add(err("PSV-005","PARAMETER_TYPE_MISMATCH","Parameter value does not match declared type "+type,ptr+"/value"));
        if(v.isNumber()&&p.hasNonNull("allowedRange")){BigDecimal x=v.decimalValue();JsonNode r=p.path("allowedRange");if(r.hasNonNull("minimum")&&x.compareTo(r.path("minimum").decimalValue())<0)out.add(err("PSV-005","PARAMETER_BELOW_MINIMUM","Parameter is below allowed minimum",ptr+"/value"));if(r.hasNonNull("maximum")&&x.compareTo(r.path("maximum").decimalValue())>0)out.add(err("PSV-005","PARAMETER_ABOVE_MAXIMUM","Parameter exceeds allowed maximum",ptr+"/value"));}
        if(p.hasNonNull("allowedValues")&&p.path("allowedValues").isArray()){boolean found=false;for(JsonNode a:p.path("allowedValues"))if(a.equals(v)){found=true;break;}if(!found)out.add(err("PSV-005","PARAMETER_VALUE_NOT_ALLOWED","Parameter value is outside allowedValues",ptr+"/value"));}
    }

    private void validateConstraints(JsonNode policy,JsonNode root,Map<String,JsonNode> params,List<PolicySemanticValidator.Finding> out){
        if(root==null)return; JsonNode selected=root;
        if(root.has("constraintSets")){selected=null;for(JsonNode s:root.path("constraintSets"))if(policy.path("policyKey").asText().equals(s.path("policyKey").asText())){selected=s;break;}}
        if(selected==null)return;
        for(JsonNode c:selected.path("constraints")){
            if("PARAMETER_RELATION".equals(c.path("type").asText())){String l=c.path("leftParameter").asText(),r=c.path("rightParameter").asText();if(!params.containsKey(l)||!params.containsKey(r)){out.add(err("PSV-006","CONSTRAINT_PARAMETER_MISSING","Constraint references unknown parameter",null));continue;}JsonNode lv=params.get(l).path("value"),rv=params.get(r).path("value");if(!lv.isNumber()||!rv.isNumber())continue;int cmp=lv.decimalValue().compareTo(rv.decimalValue());String op=c.path("operator").asText();boolean ok=switch(op){case "LT"->cmp<0;case "LTE"->cmp<=0;case "GT"->cmp>0;case "GTE"->cmp>=0;case "EQ"->cmp==0;case "NE"->cmp!=0;default->false;};if(!ok)out.add(err("PSV-006","PARAMETER_RELATION_VIOLATION",c.path("message").asText("Parameter relation violated"),null));}
        }
    }

    private void validateScope(JsonNode policy,GovernedRegistry registry,List<PolicySemanticValidator.Finding> out){if(registry==null)return;Set<String> jurisdictions=set(policy.path("scope").path("jurisdictions"));Set<String> products=set(policy.path("scope").path("productTypes"));Set<String> features=new HashSet<>();collectFeatures(policy.path("rules"),features);for(String name:features){var f=registry.features().get(name);if(f==null)continue;if("JURISDICTION_EXTENSION".equals(f.semanticScope())&&jurisdictions.isEmpty())out.add(err("PSV-014","JURISDICTION_SCOPE_REQUIRED","Jurisdiction feature "+name+" requires explicit policy jurisdiction scope",null));if("GLOBAL_PRODUCT_SPECIFIC".equals(f.semanticScope())&&products.isEmpty())out.add(new PolicySemanticValidator.Finding("PSV-014",PolicySemanticValidator.Severity.WARNING,"PRODUCT_SCOPE_UNSPECIFIED","Product-specific feature "+name+" is used without explicit product scope",null));}}
    private void collectFeatures(JsonNode n,Set<String> out){if(n.isObject()){if(n.has("feature"))out.add(n.path("feature").asText());n.fields().forEachRemaining(e->collectFeatures(e.getValue(),out));}else if(n.isArray())for(JsonNode x:n)collectFeatures(x,out);}

    private void validateVersionOverlap(JsonNode policy,Collection<PolicyVersion> versions,List<PolicySemanticValidator.Finding> out){String key=policy.path("policyKey").asText(),inst=policy.path("scope").path("institutionId").asText();Set<String> entities=set(policy.path("scope").path("entityTypes")),jur=set(policy.path("scope").path("jurisdictions")),prod=set(policy.path("scope").path("productTypes"));Instant from=Instant.parse(policy.path("effectivePeriod").path("effectiveFrom").asText());Instant to=policy.path("effectivePeriod").path("effectiveTo").isNull()?null:Instant.parse(policy.path("effectivePeriod").path("effectiveTo").asText());for(PolicyVersion v:versions){if(!key.equals(v.policyKey())||!inst.equals(v.institutionId())||!("ACTIVE".equals(v.lifecycle())||"APPROVED".equals(v.lifecycle())))continue;if(!scopeIntersects(entities,v.entityTypes())||!scopeIntersects(jur,v.jurisdictions())||!scopeIntersects(prod,v.products()))continue;if(overlap(from,to,v.from(),v.to()))out.add(err("PSV-010","EFFECTIVE_VERSION_OVERLAP","Policy effective period overlaps governed version "+v.policyId()+" v"+v.version(),"/effectivePeriod"));}}
    private boolean overlap(Instant a,Instant ae,Instant b,Instant be){return(ae==null||b.isBefore(ae))&&(be==null||a.isBefore(be));}
    private boolean scopeIntersects(Set<String>a,Set<String>b){return a.isEmpty()||b.isEmpty()||!Collections.disjoint(a,b);}
    private Set<String> set(JsonNode n){Set<String>s=new HashSet<>();for(JsonNode x:n)s.add(x.asText());return s;}
    private PolicySemanticValidator.Finding err(String id,String code,String msg,String ptr){return new PolicySemanticValidator.Finding(id,PolicySemanticValidator.Severity.ERROR,code,msg,ptr);}
}