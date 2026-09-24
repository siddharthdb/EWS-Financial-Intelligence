package org.ewsfi.caseworkflow.casemgmt;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestigationCaseRepository extends JpaRepository<InvestigationCase, String> {

    List<InvestigationCase> findByStatus(String status);
}
