package org.ewsfi.platform.outbox;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application context for integration-testing this starter module in
 * isolation. Picks up {@code EwsOutboxAutoConfiguration} via the standard
 * {@code AutoConfiguration.imports} mechanism.
 */
@SpringBootApplication
class OutboxTestApplication {
}
