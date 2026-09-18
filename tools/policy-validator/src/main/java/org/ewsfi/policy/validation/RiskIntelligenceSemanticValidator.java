package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

/** Semantic invariants for Part IV-B episode continuity and governed correlation. */
public final class RiskIntelligenceSemanticValidator {
  public record Finding(String ruleId,String code,String message,String jsonPointer) {}

  public List<Finding> validateEpisode(JsonNode e){
    List<Finding> out=new ArrayList<>();
    String signalType=e.path("signalType").asText();
    Set<String> ids=new HashSet<>(); int i=0;
    for(JsonNode m:e.path("memberSignals")){
      String ptr="/memberSignals/"+i++;
      if(!signalType.equals(m.path("signalType").asText()))
        out.add(err("ECV-001","EPISODE_SIGNAL_TYPE_MISMATCH","Episode members must represent the same governed signal condition",ptr+"/signalType"));
      if(!ids.add(m.path("signalId").asText()))
        out.add(err("ECV-002","DUPLICATE_EPISODE_MEMBER","A signal may appear only once in an episode revision",ptr+"/signalId"));
    }
    String expected=String.join("|",e.path("executionMode").asText(),e.path("policy").path("id").asText(),e.path("policy").path("version").asText(),
      e.path("entity").path("type").asText(),e.path("entity").path("id").asText(),signalType,
      e.path("conditionDiscriminator").isNull()?"":e.path("conditionDiscriminator").asText());
    if(!expected.equals(e.path("episodeKey").asText()))
      out.add(err("ECV-003","EPISODE_KEY_MISMATCH","episodeKey does not match the governed deterministic identity inputs","/episodeKey"));
    validateExecutionIsolation(e,out); return List.copyOf(out);
  }

  public List<Finding> validateCorrelation(JsonNode h){
    List<Finding> out=new ArrayList<>(); List<JsonNode> supporting=new ArrayList<>();
    Set<String> contributorIds=new HashSet<>(); int i=0;
    for(JsonNode c:h.path("contributors")){
      String ptr="/contributors/"+i++; String identity=c.path("type").asText()+"|"+c.path("id").asText();
      if(!contributorIds.add(identity)) out.add(err("ECV-004","DUPLICATE_CORRELATION_CONTRIBUTOR","Contributor identity must be unique",ptr+"/id"));
      if("SUPPORTING".equals(c.path("role").asText())){
        supporting.add(c);
        if(c.path("lineageGroupIds").isMissingNode()||c.path("lineageGroupIds").isEmpty())
          out.add(err("ECV-005","MISSING_SUPPORTING_LINEAGE","Supporting contributors require lineage groups before they can count as independent corroboration",ptr+"/lineageGroupIds"));
      }
    }
    int independent=independentFamiliesAfterLineageCollapse(supporting);
    int declared=h.path("independentContributorCount").asInt(-1);
    int raw=h.path("lineageCollapse").path("rawSupportingCount").asInt(-1);
    int collapsed=h.path("lineageCollapse").path("independentSupportingCount").asInt(-1);
    if(raw!=supporting.size()) out.add(err("ECV-006","RAW_SUPPORTING_COUNT_MISMATCH","lineageCollapse.rawSupportingCount must equal the supporting contributor count","/lineageCollapse/rawSupportingCount"));
    if(collapsed!=independent||declared!=independent) out.add(err("ECV-007","INDEPENDENCE_COUNT_MISMATCH","Independent corroboration requires distinct contributor families after shared-lineage collapse","/independentContributorCount"));
    if("EMERGING_LIQUIDITY_STRESS".equals(h.path("hypothesisType").asText())&&independent<2)
      out.add(err("ECV-008","INSUFFICIENT_INDEPENDENT_CONTRIBUTORS","Phase-1 liquidity stress requires at least two independent supporting contributor families","/contributors"));
    validateExecutionIsolation(h,out); return List.copyOf(out);
  }

  public List<Finding> validateCorrelationPolicy(JsonNode p, GovernedRegistry registry, JsonNode hypothesisRegistry){
    List<Finding> out=new ArrayList<>();
    String hypothesis=p.path("hypothesisType").asText();
    boolean known=false,active=false;
    for(JsonNode h:hypothesisRegistry.path("hypotheses")) if(hypothesis.equals(h.path("hypothesisType").asText())){known=true;active="ACTIVE".equals(h.path("status").asText());}
    if(!known) out.add(err("CPV-001","UNKNOWN_HYPOTHESIS_TYPE","Correlation policy references an unknown governed hypothesis type","/hypothesisType"));
    else if(!active) out.add(err("CPV-001","INACTIVE_HYPOTHESIS_TYPE","Correlation policy references a non-active hypothesis type","/hypothesisType"));
    if(!p.path("policyKey").asText().equals(hypothesis)) out.add(err("CPV-002","POLICY_HYPOTHESIS_MISMATCH","policyKey must equal the governed hypothesisType","/policyKey"));

    Set<String> analyticalTypes=new HashSet<>(), supportingFamilies=new HashSet<>(); int i=0;
    for(JsonNode c:p.path("contributors")){
      String ptr="/contributors/"+i++; String type=c.path("analyticalType").asText();
      if(!analyticalTypes.add(type)) out.add(err("CPV-003","DUPLICATE_CONTRIBUTOR_TYPE","Contributor analyticalType must be unique within a correlation policy",ptr+"/analyticalType"));
      GovernedRegistry.SignalRef signal=registry.signals().get(type);
      if(signal==null) out.add(err("CPV-004","UNKNOWN_CONTRIBUTOR_SIGNAL","Correlation contributor is not in the governed signal registry: "+type,ptr+"/analyticalType"));
      else if(!"ACTIVE".equals(signal.status())) out.add(err("CPV-004","INACTIVE_CONTRIBUTOR_SIGNAL","Correlation contributor is not ACTIVE: "+type,ptr+"/analyticalType"));
      if("SUPPORTING".equals(c.path("role").asText())) supportingFamilies.add(c.path("family").asText());
    }
    JsonNode min=p.path("minimumIndependentContributorFamilies"); int value=min.path("value").asInt(), lower=min.path("minimum").asInt(), upper=min.path("maximum").asInt();
    if(value<lower||value>upper||lower>upper) out.add(err("CPV-005","CONTRIBUTOR_THRESHOLD_OUT_OF_BOUNDS","Minimum independent contributor families must remain within ordered governance bounds","/minimumIndependentContributorFamilies"));
    if(value>supportingFamilies.size()) out.add(err("CPV-006","UNSATISFIABLE_CONTRIBUTOR_THRESHOLD","Configured minimum exceeds the number of distinct eligible supporting families","/minimumIndependentContributorFamilies/value"));

    try{
      Duration v=Duration.parse(p.path("window").path("duration").asText()), lo=Duration.parse(p.path("window").path("guardrail").path("minimum").asText()), hi=Duration.parse(p.path("window").path("guardrail").path("maximum").asText());
      if(lo.compareTo(hi)>0||v.compareTo(lo)<0||v.compareTo(hi)>0) out.add(err("CPV-007","CORRELATION_WINDOW_OUT_OF_BOUNDS","Correlation window must remain within ordered governance bounds","/window/duration"));
    }catch(Exception ex){out.add(err("CPV-007","INVALID_CORRELATION_WINDOW","Correlation windows must use ISO-8601 day/time duration syntax","/window"));}

    String lifecycle=p.path("lifecycle").asText();
    if("APPROVED".equals(lifecycle)||"ACTIVE".equals(lifecycle)){
      JsonNode g=p.path("governance"); String maker=g.path("createdBy").asText(), checker=g.path("approvedBy").asText();
      if(!g.path("makerCheckerRequired").asBoolean()||maker.isBlank()||checker.isBlank()||maker.equals(checker))
        out.add(err("CPV-008","MAKER_CHECKER_SEPARATION_REQUIRED","Approved/active correlation policy requires distinct maker and checker identities","/governance"));
      if(g.path("simulationRunId").asText().isBlank()) out.add(err("CPV-009","SIMULATION_EVIDENCE_REQUIRED","Approved/active correlation policy requires simulation evidence","/governance/simulationRunId"));
    }
    return List.copyOf(out);
  }

  public List<Finding> validateEpisodeTransition(JsonNode previous, JsonNode next){
    List<Finding> out=new ArrayList<>();
    for(String field:List.of("episodeId","episodeKey","signalType"))
      if(!previous.path(field).equals(next.path(field))) out.add(err("ETV-001","EPISODE_IDENTITY_CHANGED","Episode identity fields are immutable across revisions","/"+field));
    if(!previous.path("entity").equals(next.path("entity"))||!previous.path("policy").equals(next.path("policy"))||!previous.path("executionMode").equals(next.path("executionMode"))||!previous.path("runId").equals(next.path("runId")))
      out.add(err("ETV-001","EPISODE_IDENTITY_CHANGED","Entity, policy and execution namespace are immutable within an episode","/"));
    if(next.path("revision").asInt()!=previous.path("revision").asInt()+1)
      out.add(err("ETV-002","EPISODE_REVISION_NOT_SEQUENTIAL","Episode revision must increment exactly by one","/revision"));
    String from=previous.path("status").asText(),to=next.path("status").asText();
    boolean allowed=("OPEN".equals(from)&&Set.of("OPEN","MONITORING","RESOLVED","SUPERSEDED").contains(to))
      ||("MONITORING".equals(from)&&Set.of("MONITORING","OPEN","RESOLVED","SUPERSEDED").contains(to))
      ||("RESOLVED".equals(from)&&"OPEN".equals(to));
    if(!allowed) out.add(err("ETV-003","INVALID_EPISODE_TRANSITION","Episode lifecycle transition is not permitted: "+from+" -> "+to,"/status"));
    int expectedReopens=previous.path("reopenCount").asInt()+(("RESOLVED".equals(from)&&"OPEN".equals(to))?1:0);
    if(next.path("reopenCount").asInt()!=expectedReopens)
      out.add(err("ETV-004","INVALID_REOPEN_COUNT","reopenCount must increment exactly once on RESOLVED -> OPEN","/reopenCount"));
    return List.copyOf(out);
  }

  public List<Finding> validateCorrelationTransition(JsonNode previous, JsonNode next){
    List<Finding> out=new ArrayList<>();
    for(String field:List.of("hypothesisId","hypothesisType"))
      if(!previous.path(field).equals(next.path(field))) out.add(err("CTV-001","HYPOTHESIS_IDENTITY_CHANGED","Correlation hypothesis identity is immutable across revisions","/"+field));
    if(!previous.path("entity").equals(next.path("entity"))||!previous.path("policy").equals(next.path("policy"))||!previous.path("executionMode").equals(next.path("executionMode"))||!previous.path("runId").equals(next.path("runId")))
      out.add(err("CTV-001","HYPOTHESIS_IDENTITY_CHANGED","Entity, policy and execution namespace are immutable within a hypothesis","/"));
    if(next.path("revision").asInt()!=previous.path("revision").asInt()+1)
      out.add(err("CTV-002","HYPOTHESIS_REVISION_NOT_SEQUENTIAL","Hypothesis revision must increment exactly by one","/revision"));
    String from=previous.path("status").asText(),to=next.path("status").asText();
    boolean allowed=("PROPOSED".equals(from)&&Set.of("PROPOSED","ACTIVE","REJECTED","RESOLVED").contains(to))
      ||("ACTIVE".equals(from)&&Set.of("ACTIVE","RESOLVED").contains(to));
    if(!allowed) out.add(err("CTV-003","INVALID_HYPOTHESIS_TRANSITION","Correlation lifecycle transition is not permitted: "+from+" -> "+to,"/status"));
    return List.copyOf(out);
  }

  public List<Finding> validateAggregationPolicy(JsonNode p, GovernedRegistry registry, JsonNode hypothesisRegistry){
    List<Finding> out=new ArrayList<>();
    double scaleMin=p.path("scoreScale").path("minimum").asDouble(), scaleMax=p.path("scoreScale").path("maximum").asDouble();
    if(scaleMin>=scaleMax) out.add(err("RAP-001","INVALID_SCORE_SCALE","Score scale minimum must be less than maximum","/scoreScale"));
    List<JsonNode> bands=new ArrayList<>(); p.path("scoreScale").path("bands").forEach(bands::add);
    bands.sort(Comparator.comparingDouble(x->x.path("minimum").asDouble()));
    Set<String> bandNames=new HashSet<>(); double previousMin=Double.NEGATIVE_INFINITY;
    for(int i=0;i<bands.size();i++){
      JsonNode b=bands.get(i); double lo=b.path("minimum").asDouble();
      if(!bandNames.add(b.path("band").asText())||lo<scaleMin||lo>scaleMax||lo<=previousMin)
        out.add(err("RAP-002","INVALID_SCORE_BAND","Band thresholds must be unique, strictly increasing and inside the score scale","/scoreScale/bands"));
      previousMin=lo;
    }
    if(bands.size()!=4||!bandNames.equals(Set.of("LOW","MEDIUM","HIGH","CRITICAL"))||Math.abs(bands.get(0).path("minimum").asDouble()-scaleMin)>0.000001)
      out.add(err("RAP-002","INCOMPLETE_SCORE_BANDS","LOW/MEDIUM/HIGH/CRITICAL thresholds are required and LOW must begin at scale minimum","/scoreScale/bands"));
    JsonNode sev=p.path("severityContribution"); double info=sev.path("INFO").asDouble(),low=sev.path("LOW").asDouble(),med=sev.path("MEDIUM").asDouble(),high=sev.path("HIGH").asDouble(),crit=sev.path("CRITICAL").asDouble();
    double guardMin=sev.path("guardrail").path("minimum").asDouble(),guardMax=sev.path("guardrail").path("maximum").asDouble();
    if(!(guardMin<=info&&info<=low&&low<med&&med<high&&high<crit&&crit<=guardMax))
      out.add(err("RAP-003","INVALID_SEVERITY_CONTRIBUTIONS","Severity contributions must be ordered INFO <= LOW < MEDIUM < HIGH < CRITICAL inside governance guardrails","/severityContribution"));

    Set<String> dimensions=new HashSet<>(); for(JsonNode d:p.path("dimensionPolicies")){
      String dim=d.path("riskDimension").asText();
      if(!dimensions.add(dim)) out.add(err("RAP-004","DUPLICATE_DIMENSION_POLICY","A risk dimension may have only one aggregation policy entry","/dimensionPolicies"));
      if(registry==null||!registry.riskDimensions().contains(dim)) out.add(err("RAP-005","UNKNOWN_RISK_DIMENSION","Aggregation policy references unknown risk dimension: "+dim,"/dimensionPolicies"));
      double cap=d.path("cap").asDouble(); if(cap<scaleMin||cap>scaleMax) out.add(err("RAP-006","DIMENSION_CAP_OUT_OF_SCALE","Dimension cap must be inside score scale","/dimensionPolicies"));
    }

    String method=p.path("aggregation").path("method").asText(); JsonNode weights=p.path("aggregation").path("dimensionWeights");
    if("WEIGHTED_DIMENSIONS".equals(method)){
      double sum=0; Iterator<String> names=weights.fieldNames(); int count=0;
      while(names.hasNext()){String dim=names.next();count++;sum+=weights.path(dim).asDouble();if(registry==null||!registry.riskDimensions().contains(dim))out.add(err("RAP-007","UNKNOWN_WEIGHT_DIMENSION","Weight references unknown risk dimension: "+dim,"/aggregation/dimensionWeights/"+dim));}
      if(count==0||Math.abs(sum-1.0)>0.000001) out.add(err("RAP-008","DIMENSION_WEIGHTS_NOT_NORMALIZED","WEIGHTED_DIMENSIONS weights must sum to 1.0","/aggregation/dimensionWeights"));
    } else if(weights.size()>0) out.add(err("RAP-009","UNUSED_DIMENSION_WEIGHTS","Dimension weights are only permitted for WEIGHTED_DIMENSIONS","/aggregation/dimensionWeights"));

    Set<String> knownHypotheses=new HashSet<>(); for(JsonNode h:hypothesisRegistry.path("hypotheses"))if("ACTIVE".equals(h.path("status").asText()))knownHypotheses.add(h.path("hypothesisType").asText());
    for(JsonNode s:p.path("correlationSubstitution")){
      if(!knownHypotheses.contains(s.path("hypothesisType").asText()))out.add(err("RAP-010","UNKNOWN_CORRELATION_HYPOTHESIS","Substitution references unknown/non-active hypothesis","/correlationSubstitution"));
      if(!registry.riskDimensions().contains(s.path("riskDimension").asText()))out.add(err("RAP-005","UNKNOWN_RISK_DIMENSION","Substitution references unknown risk dimension","/correlationSubstitution"));
      double contribution=s.path("contribution").asDouble();if(contribution<scaleMin||contribution>scaleMax)out.add(err("RAP-011","CORRELATION_CONTRIBUTION_OUT_OF_SCALE","Correlation contribution must be inside score scale","/correlationSubstitution"));
    }
    String lifecycle=p.path("lifecycle").asText();
    if("APPROVED".equals(lifecycle)||"ACTIVE".equals(lifecycle)){
      JsonNode g=p.path("governance");String maker=g.path("createdBy").asText(),checker=g.path("approvedBy").asText();
      if(!g.path("makerCheckerRequired").asBoolean()||maker.isBlank()||checker.isBlank()||maker.equals(checker))out.add(err("RAP-012","MAKER_CHECKER_SEPARATION_REQUIRED","Approved/active aggregation policy requires distinct maker and checker","/governance"));
      if(g.path("simulationRunId").asText().isBlank())out.add(err("RAP-013","SIMULATION_EVIDENCE_REQUIRED","Approved/active aggregation policy requires simulation evidence","/governance/simulationRunId"));
    }
    return List.copyOf(out);
  }

  public List<Finding> validateRiskAssessment(JsonNode a, JsonNode policy, GovernedRegistry registry, String expectedPolicyHash){
    List<Finding> out=new ArrayList<>(); validateExecutionIsolation(a,out);
    if(!policy.path("policyId").asText().equals(a.path("aggregationPolicy").path("id").asText())||!policy.path("version").asText().equals(a.path("aggregationPolicy").path("version").asText()))
      out.add(err("RAV-001","ASSESSMENT_POLICY_MISMATCH","Assessment policy identity/version must match the evaluated aggregation policy","/aggregationPolicy"));
    if(!expectedPolicyHash.equals(a.path("aggregationPolicy").path("artifactHash").asText()))
      out.add(err("RAV-002","ASSESSMENT_POLICY_HASH_MISMATCH","Assessment must bind to exact aggregation policy artifact bytes","/aggregationPolicy/artifactHash"));

    Set<String> dims=new HashSet<>(); double maxScore=Double.NEGATIVE_INFINITY; String maxBand=null;
    Map<String,Set<String>> suppressed=new HashMap<>();
    for(JsonNode s:policy.path("correlationSubstitution"))suppressed.put(s.path("riskDimension").asText(),textSet(s.path("suppressUnderlyingFamilies")));
    for(JsonNode d:a.path("dimensionAssessments")){
      String dim=d.path("riskDimension").asText();
      if(!dims.add(dim))out.add(err("RAV-003","DUPLICATE_DIMENSION_ASSESSMENT","A risk dimension may appear only once per assessment","/dimensionAssessments"));
      if(registry==null||!registry.riskDimensions().contains(dim))out.add(err("RAV-004","UNKNOWN_RISK_DIMENSION","Assessment references unknown risk dimension: "+dim,"/dimensionAssessments"));
      Set<String> identities=new HashSet<>(); boolean scoringCorrelation=false; Set<String> scoringFamilies=new HashSet<>();
      for(JsonNode x:d.path("contributors")){
        String identity=x.path("type").asText()+"|"+x.path("id").asText();
        if(!identities.add(identity))out.add(err("RAV-005","DUPLICATE_DIMENSION_CONTRIBUTOR","Contributor identity may score only once per dimension","/dimensionAssessments"));
        if("SCORING".equals(x.path("role").asText())){
          scoringFamilies.add(x.path("contributionFamily").asText());
          if("CORRELATION_HYPOTHESIS".equals(x.path("type").asText()))scoringCorrelation=true;
        }
      }
      if(scoringCorrelation){for(String family:suppressed.getOrDefault(dim,Set.of()))if(scoringFamilies.contains(family))out.add(err("RAV-006","CORRELATION_DOUBLE_COUNT","Underlying family cannot score again when correlation substitution is authoritative for the same dimension","/dimensionAssessments"));}
      if(!d.path("rawScore").isNull()){double score=d.path("rawScore").asDouble();if(score>maxScore){maxScore=score;maxBand=d.path("proposedBand").asText();}}
    }
    JsonNode overall=a.path("overallAssessment");String method=overall.path("method").asText();
    if(!method.equals(policy.path("aggregation").path("method").asText()))out.add(err("RAV-007","AGGREGATION_METHOD_MISMATCH","Assessment method must match policy","/overallAssessment/method"));
    if("MAX_DIMENSION".equals(method)&&!overall.path("rawScore").isNull()&&Math.abs(overall.path("rawScore").asDouble()-maxScore)>0.000001)
      out.add(err("RAV-008","OVERALL_SCORE_MISMATCH","MAX_DIMENSION overall score must equal maximum dimension raw score","/overallAssessment/rawScore"));
    if("MAX_DIMENSION".equals(method)&&maxBand!=null&&!maxBand.equals(overall.path("proposedBand").asText()))
      out.add(err("RAV-009","OVERALL_BAND_MISMATCH","MAX_DIMENSION overall band must follow the maximum scoring dimension","/overallAssessment/proposedBand"));
    return List.copyOf(out);
  }

  private Set<String> textSet(JsonNode n){Set<String>s=new HashSet<>();for(JsonNode x:n)s.add(x.asText());return s;}

  private int independentFamiliesAfterLineageCollapse(List<JsonNode> nodes){
    int n=nodes.size(); if(n==0)return 0; int[] parent=new int[n]; for(int i=0;i<n;i++)parent[i]=i;
    List<Set<String>> groups=new ArrayList<>(); List<String> families=new ArrayList<>();
    for(JsonNode c:nodes){Set<String>s=new HashSet<>();for(JsonNode g:c.path("lineageGroupIds"))s.add(g.asText());groups.add(s);families.add(c.path("contributorFamily").asText());}
    for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)if(families.get(i).equals(families.get(j))||!Collections.disjoint(groups.get(i),groups.get(j)))union(parent,i,j);
    Set<Integer> roots=new HashSet<>();for(int i=0;i<n;i++)roots.add(find(parent,i));return roots.size();
  }
  private int find(int[]p,int x){while(p[x]!=x){p[x]=p[p[x]];x=p[x];}return x;}
  private void union(int[]p,int a,int b){a=find(p,a);b=find(p,b);if(a!=b)p[b]=a;}
  private void validateExecutionIsolation(JsonNode n,List<Finding> out){String mode=n.path("executionMode").asText();if(!"LIVE".equals(mode)&&(n.path("runId").isMissingNode()||n.path("runId").isNull()||n.path("runId").asText().isBlank()))out.add(err("ECV-009","NON_LIVE_RUN_ID_REQUIRED","Non-live processing requires runId for state isolation","/runId"));}
  private Finding err(String id,String code,String message,String ptr){return new Finding(id,code,message,ptr);}
}
