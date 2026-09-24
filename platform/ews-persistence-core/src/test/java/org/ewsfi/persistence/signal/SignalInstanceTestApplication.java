package org.ewsfi.persistence.signal;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application context for integration-testing this library module in
 * isolation (it has no application entry point of its own). Picks up
 * {@code EwsPersistenceCoreAutoConfiguration} via the standard
 * {@code AutoConfiguration.imports} mechanism.
 */
@SpringBootApplication
class SignalInstanceTestApplication {
}
