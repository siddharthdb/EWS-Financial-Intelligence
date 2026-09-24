package org.ewsfi.persistence.signal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalInstanceRepository extends JpaRepository<SignalInstance, String> {

    List<SignalInstance> findByStatus(String status);

    List<SignalInstance> findByEntityTypeAndEntityIdAndSignalType(
            String entityType, String entityId, String signalType);
}
