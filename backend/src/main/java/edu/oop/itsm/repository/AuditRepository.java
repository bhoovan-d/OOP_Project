package edu.oop.itsm.repository;

import edu.oop.itsm.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {
    List<AuditEntry> findByIncidentIdOrderByTimestampAscIdAsc(Long incidentId);
}
