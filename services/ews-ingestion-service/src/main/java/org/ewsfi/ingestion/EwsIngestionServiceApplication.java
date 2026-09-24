package org.ewsfi.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Source adapter host, per docs/architecture/01-architecture-blueprint.md Section 4
 * ("Acquisition and Ingestion Architecture"). Skeleton only.
 */
@SpringBootApplication
public class EwsIngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EwsIngestionServiceApplication.class, args);
    }
}
