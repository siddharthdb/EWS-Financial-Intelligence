package org.ewsfi.persistence.envelope;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanonicalEventEnvelopeRepository extends JpaRepository<CanonicalEventEnvelope, UUID> {
}
