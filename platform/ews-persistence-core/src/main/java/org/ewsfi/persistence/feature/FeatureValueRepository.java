package org.ewsfi.persistence.feature;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureValueRepository extends JpaRepository<FeatureValue, String> {

    List<FeatureValue> findByEntityTypeAndEntityIdAndFeatureNameOrderByKnowledgeTimeDesc(
            String entityType, String entityId, String featureName);
}
