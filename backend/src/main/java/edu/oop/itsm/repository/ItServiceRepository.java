package edu.oop.itsm.repository;

import edu.oop.itsm.domain.ItService;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItServiceRepository extends JpaRepository<ItService, Long> {

    @EntityGraph(attributePaths = {"slaPolicy", "defaultCategory"})
    List<ItService> findByActiveTrueOrderByName();

    @EntityGraph(attributePaths = {"slaPolicy", "defaultCategory"})
    List<ItService> findAllByOrderByName();

    @EntityGraph(attributePaths = {"slaPolicy", "defaultCategory"})
    Optional<ItService> findWithPolicyById(Long id);

    Optional<ItService> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
