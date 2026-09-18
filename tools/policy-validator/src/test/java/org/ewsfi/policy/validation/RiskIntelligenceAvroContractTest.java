package org.ewsfi.policy.validation;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

final class RiskIntelligenceAvroContractTest {
  @Test void episodeRevisionEventIsValidAvro() throws Exception {
    Schema s=parse("schemas/events/payloads/signal-episode-changed-v1.avsc");
    assertEquals("org.ewsfi.events.riskintelligence.v1.SignalEpisodeChangedV1",s.getFullName());
    assertNotNull(s.getField("snapshotRef"));
  }
  @Test void correlationRevisionEventIsValidAvro() throws Exception {
    Schema s=parse("schemas/events/payloads/correlation-hypothesis-changed-v1.avsc");
    assertEquals("org.ewsfi.events.riskintelligence.v1.CorrelationHypothesisChangedV1",s.getFullName());
    assertNotNull(s.getField("independentContributorCount"));
    assertNotNull(s.getField("contributors"));
  }
  private Schema parse(String path)throws Exception{return new Schema.Parser().parse(Files.readString(repoRoot().resolve(path)));}
  private Path repoRoot(){Path p=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(p!=null&&!Files.exists(p.resolve("schemas")))p=p.getParent();if(p==null)throw new IllegalStateException("Repository root not found");return p;}
}
