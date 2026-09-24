package org.ewsfi.contracts;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the repository root from this module's location: {@code test/ews-event-contracts-test}
 * is two directories below the root.
 */
final class ContractPaths {

    private ContractPaths() {
    }

    static Path repoRoot() {
        return Paths.get("").toAbsolutePath().getParent().getParent();
    }
}
