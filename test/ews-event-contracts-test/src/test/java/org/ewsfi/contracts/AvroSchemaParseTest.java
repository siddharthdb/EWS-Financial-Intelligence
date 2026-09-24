package org.ewsfi.contracts;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

/**
 * Parses every {@code .avsc} file under the repository-root {@code schemas/events} tree, per
 * docs/architecture/05-coherence-review-parts-i-iii.md Section 5, backlog item 1 ("Add CI that
 * parses every JSON/Avro schema"). A parse failure here means a contract is malformed before it
 * ever reaches the schema registry.
 */
class AvroSchemaParseTest {

    private static final Path SCHEMAS_ROOT = ContractPaths.repoRoot().resolve("schemas/events");

    @Test
    void everyAvroContractParses() throws IOException {
        List<Path> avscFiles = findAvscFiles();
        assertFalse(avscFiles.isEmpty(), "Expected at least one .avsc file under " + SCHEMAS_ROOT);

        Schema.Parser parser = new Schema.Parser();
        for (Path file : avscFiles) {
            assertDoesNotThrow(
                    () -> parser.parse(file.toFile()),
                    () -> "Failed to parse Avro schema: " + file);
        }
    }

    private static List<Path> findAvscFiles() throws IOException {
        if (!Files.isDirectory(SCHEMAS_ROOT)) {
            fail("Expected schemas directory at " + SCHEMAS_ROOT);
        }
        try (Stream<Path> paths = Files.walk(SCHEMAS_ROOT)) {
            return paths.filter(p -> p.toString().endsWith(".avsc")).toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
