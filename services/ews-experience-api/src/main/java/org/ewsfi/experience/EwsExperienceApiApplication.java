package org.ewsfi.experience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Experience layer entry point (Portfolio Cockpit / Counterparty 360 / Analyst Workbench per
 * docs/architecture/01-architecture-blueprint.md Section 2, Layer 9). Skeleton only.
 */
@SpringBootApplication
public class EwsExperienceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EwsExperienceApiApplication.class, args);
    }
}
