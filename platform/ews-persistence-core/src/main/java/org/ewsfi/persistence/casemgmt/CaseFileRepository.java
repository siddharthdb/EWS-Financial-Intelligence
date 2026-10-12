package org.ewsfi.persistence.casemgmt;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseFileRepository extends JpaRepository<CaseFile, String> {

    List<CaseFile> findByStatus(String status);

    boolean existsBySignalId(String signalId);
}
