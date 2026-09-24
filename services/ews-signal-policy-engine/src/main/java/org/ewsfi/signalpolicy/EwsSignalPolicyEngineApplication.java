package org.ewsfi.signalpolicy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Signal policy engine, per docs/architecture/adr/ADR-004-kafka-streams-first.md and
 * docs/architecture/02-canonical-risk-model.md Section 11 ("Signal policy model"). Skeleton only.
 */
@SpringBootApplication
public class EwsSignalPolicyEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(EwsSignalPolicyEngineApplication.class, args);
    }
}
