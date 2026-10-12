package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

/** Deterministic semantic validator. Execution-engine independent by design. */
public final class PolicySemanticValidator {
    public record Finding(String ruleId, Severity severity, String code, String message, String jsonPointer) {}
    public enum Severity { ERROR, WARNING, INFO }

    public List<Finding> validate(JsonNode policy) { return validate(policy, null); }

    public List<Finding> validate(JsonNode policy, GovernedRegistry registry) {
        List<Finding> findings = new ArrayList<>();
        Map<String, JsonNode> parameters = indexBy(policy.path("parameters"), "name", findings, "PSV-019", "/parameters");
        Set<String> ruleIds = new HashSet<>(); Set<Integer> priorities = new HashSet<>();
        String mode = policy.path("evaluation").path("mode").asText();
        String semanticScope=policy.path("scope").path("semanticScope").asText();
        Set<String> jurisdictions=textSet(policy.path("scope").path("jurisdictions"));
        Set<String> policyEntityTypes=textSet(policy.path("scope").path("entityTypes"));

        int i=0;
        for (JsonNode rule: policy.path("rules")) {
            String ptr="/rules/"+i++; String ruleId=rule.path("ruleId").asText();
            if (!ruleIds.add(ruleId)) findings.add(error("PSV-019","DUPLICATE_RULE_ID","Duplicate ruleId: "+ruleId,ptr+"/ruleId"));
            if (("PRIORITY".equals(mode)||"FIRST_MATCH".equals(mode)) && rule.path("enabled").asBoolean()) {
                int p=rule.path("priority").asInt();
                if(!priorities.add(p)) findings.add(error("PRIORITY".equals(mode)?"PSV-007":"PSV-008","DUPLICATE_PRIORITY",
                    "Enabled "+mode+" rules require unique explicit priorities: "+p,ptr+"/priority"));
            }
            validateExpression(rule.path("condition"),parameters,registry,policyEntityTypes,findings,ptr+"/condition");
            validateClassificationBoundary(rule.path("outcome"),semanticScope,jurisdictions,findings,ptr+"/outcome");
            if (registry != null) validateOutcome(rule.path("outcome"),registry,findings,ptr+"/outcome");
        }

        validateEffectivePeriod(policy,findings);
        validateActivationGovernance(policy,findings);
        validateEngineArtifact(policy,findings);
        return List.copyOf(findings);
    }

    private void validateEffectivePeriod(JsonNode policy,List<Finding> findings){
        JsonNode from=policy.path("effectivePeriod").path("effectiveFrom"), to=policy.path("effectivePeriod").path("effectiveTo");
        Instant fromInstant;
        try { fromInstant=Instant.parse(from.asText()); }
        catch(Exception ex){ findings.add(error("PSV-009","INVALID_EFFECTIVE_FROM","effectiveFrom must be an ISO-8601 instant","/effectivePeriod/effectiveFrom")); return; }
        if(!to.isMissingNode()&&!to.isNull()){
            try { if(!Instant.parse(to.asText()).isAfter(fromInstant)) findings.add(error("PSV-009","INVALID_EFFECTIVE_PERIOD","effectiveTo must be later than effectiveFrom","/effectivePeriod")); }
            catch(Exception ex){ findings.add(error("PSV-009","INVALID_EFFECTIVE_TO","effectiveTo must be an ISO-8601 instant","/effectivePeriod/effectiveTo")); }
        }
    }

    private void validateActivationGovernance(JsonNode policy,List<Finding> findings){
        if(!"ACTIVE".equals(policy.path("lifecycle").asText())||!policy.path("governance").path("makerCheckerRequired").asBoolean()) return;
        JsonNode g=policy.path("governance");
        if(blank(g.path("approvedBy"))||blank(g.path("approvedAt"))) findings.add(error("PSV-011","MISSING_APPROVAL","ACTIVE maker-checker policy requires approval identity and timestamp","/governance"));
        if(blank(g.path("simulationRunId"))) findings.add(error("PSV-011","MISSING_SIMULATION","ACTIVE policy requires accepted simulation evidence","/governance/simulationRunId"));
        if(!blank(g.path("createdBy"))&&!blank(g.path("approvedBy"))&&g.path("createdBy").asText().equals(g.path("approvedBy").asText()))
            findings.add(error("PSV-011","MAKER_CHECKER_SAME_ACTOR","Maker and checker must differ for maker-checker policy","/governance/approvedBy"));
    }

    private void validateEngineArtifact(JsonNode policy,List<Finding> findings){
        String engine=policy.path("evaluation").path("engine").asText(); JsonNode e=policy.path("evaluation");
        if(("DMN".equals(engine)||"DROOLS".equals(engine))&&(blank(e.path("externalArtifactRef"))||blank(e.path("externalArtifactVersion"))))
            findings.add(error("PSV-012","MISSING_ENGINE_ARTIFACT",engine+" policy requires approved artifact reference and version","/evaluation"));
        if("NATIVE".equals(engine)&&(!blank(e.path("externalArtifactRef"))||!blank(e.path("externalArtifactVersion"))))
            findings.add(error("PSV-012","UNDECLARED_NATIVE_ARTIFACT","NATIVE policy must not depend on an external executable artifact","/evaluation"));
    }

    private void validateClassificationBoundary(JsonNode outcome,String scope,Set<String> jurisdictions,List<Finding> findings,String ptr){
        if(!"CLASSIFICATION_CANDIDATE".equals(outcome.path("type").asText())) return;
        if(!"JURISDICTION_EXTENSION".equals(scope)||jurisdictions.isEmpty())
            findings.add(error("PSV-013","CLASSIFICATION_BOUNDARY_VIOLATION","Classification candidates require an explicit jurisdiction-extension policy scope",ptr+"/type"));
    }

    private void validateExpression(JsonNode node, Map<String,JsonNode> parameters, GovernedRegistry registry, Set<String> entityTypes, List<Finding> findings, String ptr) {
        if(node.has("all")){int n=0;for(JsonNode c:node.path("all"))validateExpression(c,parameters,registry,entityTypes,findings,ptr+"/all/"+n++);return;}
        if(node.has("any")){int n=0;for(JsonNode c:node.path("any"))validateExpression(c,parameters,registry,entityTypes,findings,ptr+"/any/"+n++);return;}
        if(node.has("not")){validateExpression(node.path("not"),parameters,registry,entityTypes,findings,ptr+"/not");return;}
        validateOperand(node.path("left"),parameters,registry,entityTypes,findings,ptr+"/left");
        if(node.has("right")) validateOperand(node.path("right"),parameters,registry,entityTypes,findings,ptr+"/right");
    }

    private void validateOperand(JsonNode operand, Map<String,JsonNode> parameters, GovernedRegistry registry, Set<String> entityTypes, List<Finding> findings, String ptr) {
        if(operand.has("parameter")&&!parameters.containsKey(operand.path("parameter").asText())) findings.add(error("PSV-001","UNKNOWN_PARAMETER","Referenced parameter does not exist: "+operand.path("parameter").asText(),ptr));
        if(operand.has("feature")&&registry!=null){String name=operand.path("feature").asText();var f=registry.features().get(name);if(f==null)findings.add(error("PSV-002","UNKNOWN_FEATURE","Feature is not registered: "+name,ptr));else if(!entityTypes.isEmpty()&&!entityTypes.contains(f.entityGrain()))findings.add(error("PSV-015","ENTITY_GRAIN_MISMATCH","Feature "+name+" has grain "+f.entityGrain()+" but policy targets "+entityTypes,ptr));}
    }

    private void validateOutcome(JsonNode outcome, GovernedRegistry registry, List<Finding> findings, String ptr){
        if("SIGNAL_CANDIDATE".equals(outcome.path("type").asText())){String code=outcome.path("code").asText();var s=registry.signals().get(code);if(s==null)findings.add(error("PSV-003","UNKNOWN_SIGNAL","Signal is not registered: "+code,ptr+"/code"));else if(!"ACTIVE".equals(s.status()))findings.add(new Finding("PSV-020",Severity.WARNING,"NON_ACTIVE_SIGNAL","Signal is not ACTIVE: "+code,ptr+"/code"));}
        int n=0;for(JsonNode d:outcome.path("riskDimensions")){if(!registry.riskDimensions().contains(d.asText()))findings.add(error("PSV-004","UNKNOWN_RISK_DIMENSION","Risk dimension is not canonical: "+d.asText(),ptr+"/riskDimensions/"+n));n++;}
    }

    private Map<String,JsonNode> indexBy(JsonNode array,String field,List<Finding> findings,String ruleId,String ptr){Map<String,JsonNode> map=new HashMap<>();int i=0;for(JsonNode item:array){String key=item.path(field).asText();if(map.putIfAbsent(key,item)!=null)findings.add(error(ruleId,"DUPLICATE_ID","Duplicate "+field+": "+key,ptr+"/"+i));i++;}return map;}
    private Set<String> textSet(JsonNode n){Set<String>s=new HashSet<>();for(JsonNode x:n)s.add(x.asText());return s;}
    private boolean blank(JsonNode n){return n.isMissingNode()||n.isNull()||!n.isTextual()||n.asText().isBlank();}
    private Finding error(String rule,String code,String message,String ptr){return new Finding(rule,Severity.ERROR,code,message,ptr);}
}