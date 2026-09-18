package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Semantic invariants for Part IV-B episode continuity and correlation lineage. */
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
    String expected=String.join("|",
      e.path("executionMode").asText(),e.path("policy").path("id").asText(),e.path("policy").path("version").asText(),
      e.path("entity").path("type").asText(),e.path("entity").path("id").asText(),signalType,
      e.path("conditionDiscriminator").isNull()?"":e.path("conditionDiscriminator").asText());
    if(!expected.equals(e.path("episodeKey").asText()))
      out.add(err("ECV-003","EPISODE_KEY_MISMATCH","episodeKey does not match the governed deterministic identity inputs","/episodeKey"));
    validateExecutionIsolation(e,out);
    return List.copyOf(out);
  }

  public List<Finding> validateCorrelation(JsonNode h){
    List<Finding> out=new ArrayList<>();
    List<JsonNode> supporting=new ArrayList<>();
    Set<String> contributorIds=new HashSet<>(); int i=0;
    for(JsonNode c:h.path("contributors")){
      String ptr="/contributors/"+i++;
      String identity=c.path("type").asText()+"|"+c.path("id").asText();
      if(!contributorIds.add(identity))
        out.add(err("ECV-004","DUPLICATE_CORRELATION_CONTRIBUTOR","Contributor identity must be unique",ptr+"/id"));
      if("SUPPORTING".equals(c.path("role").asText())){
        supporting.add(c);
        if(c.path("lineageGroupIds").isMissingNode()||c.path("lineageGroupIds").isEmpty())
          out.add(err("ECV-005","MISSING_SUPPORTING_LINEAGE","Supporting contributors require lineage groups before they can count as independent corroboration",ptr+"/lineageGroupIds"));
      }
    }

    int independent=independentComponents(supporting);
    int declared=h.path("independentContributorCount").asInt(-1);
    int raw=h.path("lineageCollapse").path("rawSupportingCount").asInt(-1);
    int collapsed=h.path("lineageCollapse").path("independentSupportingCount").asInt(-1);
    if(raw!=supporting.size())
      out.add(err("ECV-006","RAW_SUPPORTING_COUNT_MISMATCH","lineageCollapse.rawSupportingCount must equal the supporting contributor count","/lineageCollapse/rawSupportingCount"));
    if(collapsed!=independent||declared!=independent)
      out.add(err("ECV-007","INDEPENDENCE_COUNT_MISMATCH","Independent corroboration must be calculated after collapsing shared lineage","/independentContributorCount"));
    if("EMERGING_LIQUIDITY_STRESS".equals(h.path("hypothesisType").asText())&&independent<2)
      out.add(err("ECV-008","INSUFFICIENT_INDEPENDENT_CONTRIBUTORS","Phase-1 liquidity stress requires at least two independent supporting contributor families","/contributors"));
    validateExecutionIsolation(h,out);
    return List.copyOf(out);
  }

  private int independentComponents(List<JsonNode> nodes){
    int n=nodes.size(); if(n==0)return 0;
    int[] parent=new int[n]; for(int i=0;i<n;i++)parent[i]=i;
    List<Set<String>> groups=new ArrayList<>();
    for(JsonNode c:nodes){Set<String>s=new HashSet<>();for(JsonNode g:c.path("lineageGroupIds"))s.add(g.asText());groups.add(s);}
    for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)if(!Collections.disjoint(groups.get(i),groups.get(j)))union(parent,i,j);
    Set<Integer> roots=new HashSet<>();for(int i=0;i<n;i++)roots.add(find(parent,i));return roots.size();
  }
  private int find(int[]p,int x){while(p[x]!=x){p[x]=p[p[x]];x=p[x];}return x;}
  private void union(int[]p,int a,int b){a=find(p,a);b=find(p,b);if(a!=b)p[b]=a;}

  private void validateExecutionIsolation(JsonNode n,List<Finding> out){
    String mode=n.path("executionMode").asText();
    if(!"LIVE".equals(mode)&&(n.path("runId").isMissingNode()||n.path("runId").isNull()||n.path("runId").asText().isBlank()))
      out.add(err("ECV-009","NON_LIVE_RUN_ID_REQUIRED","Non-live processing requires runId for state isolation","/runId"));
  }
  private Finding err(String id,String code,String message,String ptr){return new Finding(id,code,message,ptr);}
}
