package org.ewsfi.coreregistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Counterparty/facility/relationship core, per docs/architecture/02-canonical-risk-model.md
 * Section 4-5. Skeleton only.
 */
@SpringBootApplication
public class EwsCoreRegistryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EwsCoreRegistryServiceApplication.class, args);
    }
}
