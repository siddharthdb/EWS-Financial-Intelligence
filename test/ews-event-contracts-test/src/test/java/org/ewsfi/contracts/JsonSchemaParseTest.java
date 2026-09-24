package org.ewsfi.contracts;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Parses every {@code .schema.json} file under the repository-root {@code schemas} tree (excluding
 * {@code schemas/events}, which is Avro-only) against JSON Schema draft 2020-12, matching the
 * {@code "$schema"} declaration in every contract file. Satisfies
 * docs/architecture/05-coherence-review-parts-i-iii.md Section 5, backlog item 1.
 */
class JsonSchemaParseTest {

    private static final Path SCHEMAS_ROOT = ContractPaths.repoRoot().resolve("schemas");

    @Test
    void everyJsonSchemaContractParses() throws IOException {
        List<Path> schemaFiles = findJsonSchemaFiles();
        assertFalse(schemaFiles.isEmpty(), "Expected at least one .schema.json file under " + SCHEMAS_ROOT);

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        for (Path file : schemaFiles) {
            assertDoesNotThrow(
                    () -> {
                        JsonSchema schema = factory.getSchema(file.toUri());
                        // Triggers resolution/validation of the schema document itself.
                        schema.initializeValidators();
                    },
                    () -> "Failed to parse JSON Schema contract: " + file);
        }
    }

    private static List<Path> findJsonSchemaFiles() throws IOException {
        if (!Files.isDirectory(SCHEMAS_ROOT)) {
            fail("Expected schemas directory at " + SCHEMAS_ROOT);
        }
        try (Stream<Path> paths = Files.walk(SCHEMAS_ROOT)) {
            return paths.filter(p -> p.toString().endsWith(".schema.json"))
                    .filter(p -> !p.toString().contains(SCHEMAS_ROOT.resolve("events").toString()))
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
