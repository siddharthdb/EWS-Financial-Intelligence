package org.ewsfi.contracts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Validates docs/architecture/topic-registry.json, per
 * docs/architecture/05-coherence-review-parts-i-iii.md Section 5, backlog item 3 ("Generate a
 * machine-readable topic registry mapping event type -> schema artifact -> topic -> key strategy
 * -> owner -> retention/security class"). Checks internal consistency: every event references a
 * topic that is actually declared, every non-null schema artifact path points at a real file, and
 * there are no duplicate event type entries.
 */
class TopicRegistryTest {

    private static final Path REGISTRY_PATH =
            ContractPaths.repoRoot().resolve("docs/architecture/topic-registry.json");

    @Test
    void everyEventReferencesADeclaredTopic() throws IOException {
        JsonNode root = readRegistry();

        Set<String> declaredTopics = new HashSet<>();
        for (JsonNode topic : root.get("topics")) {
            declaredTopics.add(topic.get("topic").asText());
        }
        assertFalse(declaredTopics.isEmpty(), "Expected at least one declared topic");

        for (JsonNode event : root.get("events")) {
            String topic = event.get("topic").asText();
            assertTrue(
                    declaredTopics.contains(topic),
                    "Event " + event.get("eventType").asText() + " references undeclared topic " + topic);
        }
    }

    @Test
    void everyNonNullSchemaArtifactPathExists() throws IOException {
        JsonNode root = readRegistry();
        Path repoRoot = ContractPaths.repoRoot();

        for (JsonNode event : root.get("events")) {
            JsonNode schemaArtifact = event.get("schemaArtifact");
            if (schemaArtifact == null || schemaArtifact.isNull()) {
                continue;
            }
            Path schemaPath = repoRoot.resolve(schemaArtifact.asText());
            assertTrue(
                    Files.isRegularFile(schemaPath),
                    "Event "
                            + event.get("eventType").asText()
                            + " references schemaArtifact "
                            + schemaArtifact.asText()
                            + " which does not exist at "
                            + schemaPath);
        }
    }

    @Test
    void noDuplicateEventTypes() throws IOException {
        JsonNode root = readRegistry();

        Set<String> seen = new HashSet<>();
        for (JsonNode event : root.get("events")) {
            String eventType = event.get("eventType").asText();
            assertTrue(seen.add(eventType), "Duplicate eventType in topic registry: " + eventType);
        }
    }

    private static JsonNode readRegistry() throws IOException {
        if (!Files.isRegularFile(REGISTRY_PATH)) {
            fail("Expected topic registry at " + REGISTRY_PATH);
        }
        return new ObjectMapper().readTree(REGISTRY_PATH.toFile());
    }
}
