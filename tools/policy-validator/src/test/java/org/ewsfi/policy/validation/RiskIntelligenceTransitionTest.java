package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

final class RiskIntelligenceTransitionTest {
  private final ObjectMapper m=new ObjectMapper();
  private final RiskIntelligenceSemanticValidator validator=new RiskIntelligenceSemanticValidator();

  @Test void resolvedEpisodeMayReopenWithSequentialRevisionAndCounter() throws Exception {
    ObjectNode prev=episode(); prev.put("status","RESOLVED"); prev.put("revision",2); prev.put("reopenCount",0);
    ObjectNode next=prev.deepCopy(); next.put("status","OPEN"); next.put("revision",3); next.put("reopenCount",1);
    assertTrue(validator.validateEpisodeTransition(prev,next).isEmpty(),validator.validateEpisodeTransition(prev,next).toString());
  }

  @Test void resolvedEpisodeCannotMoveToMonitoringDirectly() throws Exception {
    ObjectNode prev=episode(); prev.put("status","RESOLVED"); prev.put("revision",2);
    ObjectNode next=prev.deepCopy(); next.put("status","MONITORING"); next.put("revision",3);
    assertTrue(validator.validateEpisodeTransition(prev,next).stream().anyMatch(f->"ETV-003".equals(f.ruleId())));
  }

  @Test void proposedHypothesisMayActivate() throws Exception {
    ObjectNode prev=correlation(); ObjectNode next=prev.deepCopy(); next.put("status","ACTIVE"); next.put("revision",2);
    assertTrue(validator.validateCorrelationTransition(prev,next).isEmpty(),validator.validateCorrelationTransition(prev,next).toString());
  }

  @Test void rejectedHypothesisIsTerminal() throws Exception {
    ObjectNode prev=correlation(); prev.put("status","REJECTED"); ObjectNode next=prev.deepCopy(); next.put("status","ACTIVE"); next.put("revision",2);
    assertTrue(validator.validateCorrelationTransition(prev,next).stream().anyMatch(f->"CTV-003".equals(f.ruleId())));
  }

  private ObjectNode episode()throws Exception{return (ObjectNode)read("tests/fixtures/risk-intelligence/phase1-signal-episode-v1.json").deepCopy();}
  private ObjectNode correlation()throws Exception{return (ObjectNode)read("tests/fixtures/risk-intelligence/phase1-liquidity-correlation-v1.json").deepCopy();}
  private JsonNode read(String p)throws Exception{return m.readTree(Files.readString(repoRoot().resolve(p)));}
  private Path repoRoot(){Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.exists(p.resolve("schemas")))p=p.getParent();if(p==null)throw new IllegalStateException("Repository root not found");return p;}
}
