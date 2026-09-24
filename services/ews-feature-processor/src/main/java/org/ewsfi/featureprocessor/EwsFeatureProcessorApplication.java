package org.ewsfi.featureprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Operational feature processor, per docs/architecture/adr/ADR-004-kafka-streams-first.md and
 * docs/architecture/01-architecture-blueprint.md Section 11 ("Stream Intelligence"). Skeleton only.
 */
@SpringBootApplication
public class EwsFeatureProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EwsFeatureProcessorApplication.class, args);
    }
}
