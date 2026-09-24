package org.ewsfi.contracts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Enforces docs/architecture/deprecated-signal-aliases.json: a deprecated signal-taxonomy alias
 * (docs/architecture/04-signal-taxonomy.md Section 17, "Alias/deprecation mapping") must never
 * appear as a live quoted string literal in application code, schemas, or the topic registry --
 * only their canonical replacements should be used going forward. Satisfies
 * docs/architecture/05-coherence-review-parts-i-iii.md Section 5, backlog item 5 ("Add automated
 * terminology checks for deprecated canonical aliases").
 *
 * <p>Scans source files that could plausibly assign a live {@code signalType}/{@code eventType}:
 * every {@code .java} file under {@code services/}/{@code platform/} main sources, every schema
 * file, and the topic registry. Deliberately excludes
 * {@code docs/architecture/04-signal-taxonomy.md} (the alias mapping's own source of truth, which
 * legitimately names every deprecated alias) and
 * {@code docs/architecture/deprecated-signal-aliases.json} itself.
 */
class DeprecatedTerminologyTest {

    private static final Path REPO_ROOT = ContractPaths.repoRoot();
    private static final Path REGISTRY_PATH =
            REPO_ROOT.resolve("docs/architecture/deprecated-signal-aliases.json");

    @Test
    void noDeprecatedAliasAppearsAsALiveStringLiteral() throws IOException {
        List<String> deprecatedAliases = readDeprecatedAliases();
        assertFalse(deprecatedAliases.isEmpty(), "Expected at least one deprecated alias in the registry");

        List<Path> scannedFiles = filesToScan();
        assertFalse(scannedFiles.isEmpty(), "Expected at least one file to scan");

        List<String> violations = new ArrayList<>();
        for (Path file : scannedFiles) {
            String content = Files.readString(file);
            for (String alias : deprecatedAliases) {
                String quoted = "\"" + alias + "\"";
                if (content.contains(quoted)) {
                    violations.add(REPO_ROOT.relativize(file) + " uses deprecated alias " + quoted);
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "Deprecated signal-taxonomy aliases found as live string literals (see "
                        + "docs/architecture/04-signal-taxonomy.md Section 17 for the canonical "
                        + "replacement): "
                        + violations);
    }

    private static List<String> readDeprecatedAliases() throws IOException {
        if (!Files.isRegularFile(REGISTRY_PATH)) {
            fail("Expected deprecated-alias registry at " + REGISTRY_PATH);
        }
        JsonNode root = new ObjectMapper().readTree(REGISTRY_PATH.toFile());
        List<String> aliases = new ArrayList<>();
        for (JsonNode entry : root.get("deprecatedAliases")) {
            aliases.add(entry.get("deprecatedAlias").asText());
        }
        return aliases;
    }

    private static List<Path> filesToScan() throws IOException {
        List<Path> files = new ArrayList<>();
        files.addAll(findUnder(REPO_ROOT.resolve("services"), ".java"));
        files.addAll(findUnder(REPO_ROOT.resolve("platform"), ".java"));
        files.addAll(findUnder(REPO_ROOT.resolve("schemas"), ".avsc"));
        files.addAll(findUnder(REPO_ROOT.resolve("schemas"), ".schema.json"));

        Path topicRegistry = REPO_ROOT.resolve("docs/architecture/topic-registry.json");
        if (Files.isRegularFile(topicRegistry)) {
            files.add(topicRegistry);
        }
        return files;
    }

    private static List<Path> findUnder(Path root, String suffix) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(suffix))
                    .filter(p -> !p.toString().contains("/target/"))
                    .toList();
        }
    }
}
