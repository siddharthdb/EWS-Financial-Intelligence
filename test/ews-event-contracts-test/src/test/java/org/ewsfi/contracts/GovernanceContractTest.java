package org.ewsfi.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Machine-checks the Phase-0 governance artifacts: the topic registry (roadmap 0.8), shared
 * envelope types (0.9), deprecated-alias terminology (0.10) and the diagram generated from the
 * registry (0.12).
 */
class GovernanceContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path ROOT = ContractPaths.repoRoot();
    private static final List<String> CATALOGUE_TOPICS =
            List.of(
                    "ews.canonical.counterparty",
                    "ews.canonical.facility",
                    "ews.canonical.account-transaction",
                    "ews.canonical.repayment",
                    "ews.canonical.trade-finance",
                    "ews.canonical.financial-statement",
                    "ews.canonical.collateral",
                    "ews.canonical.rating",
                    "ews.canonical.relationship",
                    "ews.canonical.external-intelligence",
                    "ews.canonical.market",
                    "ews.derived.feature",
                    "ews.derived.signal",
                    "ews.derived.risk",
                    "ews.derived.classification",
                    "ews.derived.decision",
                    "ews.derived.case");
    private static final Set<String> RETENTION_CLASSES = Set.of("R1", "R2", "R3", "R4");
    private static final Set<String> SECURITY_CLASSES =
            Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");
    private static final Set<String> EVENT_STATUSES = Set.of("WIRED", "CATALOGUED");

    @Test
    void topicRegistryMatchesCatalogueSchemasAndOwners() throws IOException {
        JsonNode registry = MAPPER.readTree(ROOT.resolve("schemas/topics/topic-registry-v1.json").toFile());
        Set<String> topicNames = new HashSet<>();
        for (JsonNode topic : registry.get("topics")) {
            String name = topic.get("name").asText();
            assertTrue(topicNames.add(name), "duplicate topic " + name);
            assertFalse(topic.get("primaryKey").asText().isBlank());
            assertFalse(topic.get("owner").asText().isBlank());
            assertTrue(RETENTION_CLASSES.contains(topic.get("retentionClass").asText()), name);
            assertTrue(SECURITY_CLASSES.contains(topic.get("securityClass").asText()), name);
            String mode = topic.get("retentionMode").asText();
            if ("R4".equals(topic.get("retentionClass").asText())) {
                assertEquals("compact", mode, name);
                assertFalse(topic.get("provisioned").asBoolean(), name);
            } else {
                assertEquals("delete", mode, name);
                assertTrue(topic.get("retentionDaysMin").asInt() > 0, name);
                assertTrue(
                        topic.get("retentionDaysMax").asInt() >= topic.get("retentionDaysMin").asInt(),
                        name);
            }
        }
        for (String required : CATALOGUE_TOPICS) {
            assertTrue(topicNames.contains(required), "missing catalogue topic " + required);
        }

        assertTrue(Files.isRegularFile(ROOT.resolve(registry.get("envelopeSchema").asText())));
        Set<String> shared = new HashSet<>();
        for (JsonNode node : registry.get("sharedTypes")) {
            Path path = ROOT.resolve(node.asText());
            assertTrue(Files.isRegularFile(path), node.asText());
            shared.add(node.asText());
        }
        try (Stream<Path> paths = Files.list(ROOT.resolve("schemas/events/common"))) {
            List<String> onDisk =
                    paths.filter(path -> path.toString().endsWith(".avsc"))
                            .map(path -> ROOT.relativize(path).toString().replace('\\', '/'))
                            .toList();
            assertEquals(shared, new HashSet<>(onDisk));
        }

        Set<String> eventTypes = new HashSet<>();
        Set<String> schemaArtifacts = new HashSet<>();
        for (JsonNode event : registry.get("events")) {
            String eventType = event.get("eventType").asText();
            assertTrue(eventTypes.add(eventType), "duplicate event type " + eventType);
            String topic = event.get("topic").asText();
            assertTrue(topicNames.contains(topic), eventType + " topic " + topic);
            assertFalse(event.get("keyStrategy").asText().isBlank(), eventType);
            assertFalse(event.get("owner").asText().isBlank(), eventType);
            String status = event.get("status").asText();
            assertTrue(EVENT_STATUSES.contains(status), eventType);
            if (event.hasNonNull("schemaArtifact")) {
                String artifact = event.get("schemaArtifact").asText();
                assertTrue(schemaArtifacts.add(artifact), "schema reused by two events: " + artifact);
                assertTrue(Files.isRegularFile(ROOT.resolve(artifact)), artifact);
                assertTrue("WIRED".equals(status), artifact + " is contracted but not WIRED");
            }
        }

        try (Stream<Path> paths = Files.walk(ROOT.resolve("schemas/events/payloads"))) {
            List<String> payloads =
                    paths.filter(path -> path.toString().endsWith(".avsc"))
                            .map(path -> ROOT.relativize(path).toString().replace('\\', '/'))
                            .toList();
            assertEquals(schemaArtifacts, new HashSet<>(payloads));
        }
    }

    @Test
    void committedDiagramIsGeneratedFromTheTopicRegistry() throws IOException {
        JsonNode registry = MAPPER.readTree(ROOT.resolve("schemas/topics/topic-registry-v1.json").toFile());
        String committed = Files.readString(ROOT.resolve("docs/architecture/diagrams/topic-registry.mmd"));
        assertEquals(render(registry), committed);
    }

    @Test
    void deprecatedAliasesAreAbsentFromProducersAndContracts() throws IOException {
        JsonNode aliases = MAPPER.readTree(ROOT.resolve("schemas/governance/deprecated-aliases-v1.json").toFile());
        Set<String> deprecated = new HashSet<>();
        for (JsonNode alias : aliases.get("aliases")) {
            String name = alias.get("deprecatedName").asText();
            assertTrue(deprecated.add(name), "duplicate alias " + name);
            assertFalse(name.equals(alias.get("canonicalName").asText()), name);
            assertFalse(alias.get("canonicalName").asText().isBlank(), name);
            assertFalse(alias.get("kind").asText().isBlank(), name);
        }
        List<String> prefixes = new ArrayList<>();
        for (JsonNode rule : aliases.get("prefixRules")) {
            prefixes.add(rule.get("deprecatedPrefix").asText());
        }
        List<String> prohibited = new ArrayList<>();
        aliases.get("prohibitedSignalTypes").forEach(node -> prohibited.add(node.asText()));

        List<String> violations = new ArrayList<>();
        for (Path file : contractFiles()) {
            violations.addAll(literalViolations(file, Files.readString(file), deprecated, prefixes, List.of()));
        }
        for (Path file : javaMainSources()) {
            violations.addAll(literalViolations(file, Files.readString(file), deprecated, prefixes, prohibited));
        }
        for (Path file : signalContracts()) {
            violations.addAll(literalViolations(file, Files.readString(file), List.of(), List.of(), prohibited));
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    /**
     * Kept in lockstep with the generator used to write
     * {@code docs/architecture/diagrams/topic-registry.mmd}.
     */
    static String render(JsonNode doc) {
        StringBuilder out = new StringBuilder();
        out.append("%% Generated from schemas/topics/topic-registry-v1.json. Do not edit by hand.\n");
        out.append("flowchart TB\n");
        List<JsonNode> topics = new ArrayList<>();
        doc.get("topics").forEach(topics::add);
        topics.sort(Comparator.comparing(node -> node.get("name").asText()));
        List<JsonNode> events = new ArrayList<>();
        doc.get("events").forEach(events::add);
        for (JsonNode topic : topics) {
            String name = topic.get("name").asText();
            String id = name.replace(".", "_").replace("-", "_");
            out.append("  subgraph ").append(id).append(" [").append(name).append("]\n");
            List<JsonNode> owned = new ArrayList<>();
            for (JsonNode event : events) {
                if (name.equals(event.get("topic").asText())) {
                    owned.add(event);
                }
            }
            owned.sort(Comparator.comparing(node -> node.get("eventType").asText()));
            if (owned.isEmpty()) {
                out.append("    ").append(id).append("_none[\"(no catalogued events)\"]\n");
            } else {
                for (int i = 0; i < owned.size(); i++) {
                    out.append("    ")
                            .append(id)
                            .append("_")
                            .append(i)
                            .append("[\"")
                            .append(owned.get(i).get("eventType").asText())
                            .append("\"]\n");
                }
            }
            out.append("  end\n");
        }
        return out.toString();
    }

    private static List<Path> javaMainSources() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String root : List.of("services", "platform")) {
            Path start = ROOT.resolve(root);
            if (!Files.isDirectory(start)) {
                fail("missing " + start);
            }
            try (Stream<Path> paths = Files.walk(start)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> path.toString().replace('\\', '/').contains("/src/main/"))
                        .forEach(files::add);
            }
        }
        return files;
    }

    private static List<String> literalViolations(
            Path file,
            String text,
            Collection<String> deprecated,
            List<String> prefixes,
            List<String> prohibited) {
        List<String> violations = new ArrayList<>();
        for (String name : deprecated) {
            if (text.contains("\"" + name + "\"")) {
                violations.add(file + " contains deprecated name " + name);
            }
        }
        for (String prefix : prefixes) {
            Matcher matcher = Pattern.compile("\"" + Pattern.quote(prefix) + "[A-Z0-9_]+\"").matcher(text);
            if (matcher.find()) {
                violations.add(file + " contains deprecated prefix " + matcher.group());
            }
        }
        for (String prohibitedName : prohibited) {
            if (text.contains("\"" + prohibitedName + "\"")) {
                violations.add(file + " uses classification namespace as a signal literal " + prohibitedName);
            }
        }
        return violations;
    }

    private static List<Path> signalContracts() throws IOException {
        List<Path> files = new ArrayList<>();
        Path signals = ROOT.resolve("schemas/signals");
        if (Files.isDirectory(signals)) {
            try (Stream<Path> paths = Files.walk(signals)) {
                paths.filter(path -> path.toString().endsWith(".schema.json")).forEach(files::add);
            }
        }
        try (Stream<Path> paths = Files.walk(ROOT.resolve("schemas/events/payloads"))) {
            paths.filter(path -> path.getFileName().toString().startsWith("signal-"))
                    .filter(path -> path.toString().endsWith(".avsc"))
                    .forEach(files::add);
        }
        return files;
    }

    private static List<Path> contractFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(ROOT.resolve("schemas"))) {
            paths.filter(Files::isRegularFile)
                    .filter(
                            path -> {
                                String name = path.toString();
                                return name.endsWith(".avsc") || name.endsWith(".schema.json");
                            })
                    .forEach(files::add);
        }
        return files;
    }
}
