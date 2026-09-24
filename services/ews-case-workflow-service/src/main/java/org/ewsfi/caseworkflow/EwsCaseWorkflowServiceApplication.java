package org.ewsfi.caseworkflow;

import org.ewsfi.caseworkflow.casemgmt.InvestigationCase;
import org.ewsfi.caseworkflow.casemgmt.InvestigationCaseRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Human validation / case workflow, per docs/architecture/01-architecture-blueprint.md Section 16
 * ("Human-in-the-Loop").
 *
 * <p>Explicit {@code @EntityScan}/{@code @EnableJpaRepositories} for this service's own local
 * {@link InvestigationCase} entity, following the same explicit-scoping pattern
 * {@code ews-persistence-core} and {@code ews-platform-outbox-starter} already use for their
 * entities: any explicit {@code @EnableJpaRepositories} in the context (both of those modules
 * declare one) disables Spring Boot's default classpath-scan-based repository discovery for the
 * whole application, so a service with its own local JPA entities must declare its own explicit
 * scan too, scoped to just its own package -- the three declarations coexist without conflict,
 * each handling its own designated repositories.
 */
@SpringBootApplication
@EntityScan(basePackageClasses = InvestigationCase.class)
@EnableJpaRepositories(basePackageClasses = InvestigationCaseRepository.class)
public class EwsCaseWorkflowServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EwsCaseWorkflowServiceApplication.class, args);
    }
}
