package org.ewsfi.caseworkflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Human validation / case workflow, per docs/architecture/01-architecture-blueprint.md Section 16
 * ("Human-in-the-Loop"). Skeleton only.
 */
@SpringBootApplication
public class EwsCaseWorkflowServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EwsCaseWorkflowServiceApplication.class, args);
    }
}
