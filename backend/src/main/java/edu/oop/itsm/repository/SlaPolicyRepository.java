package edu.oop.itsm.repository;

import edu.oop.itsm.domain.SlaPolicy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    @EntityGraph(attributePaths = "targets")
    Optional<SlaPolicy> findFirstByDefaultPolicyTrue();

    @EntityGraph(attributePaths = "targets")
    List<SlaPolicy> findAllByOrderByName();

    boolean existsByNameIgnoreCase(String name);
}
